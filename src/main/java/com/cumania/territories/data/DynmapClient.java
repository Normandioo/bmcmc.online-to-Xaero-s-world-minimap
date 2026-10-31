package com.cumania.territories.data;

import com.cumania.territories.CumaniaTerritories;
import com.cumania.territories.TerritoryManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What the site reads from dynmap, read by the mod the same way: who is online and where (up/world/&lt;world&gt;/&lt;timestamp&gt;) and the map's
 * markers, whose icons say who holds each city in a live war (tiles/_markers_/marker_&lt;world&gt;.json). Only asks while the World Map is open.
 * Everything handed out is immutable, so the render thread can read it without locking.
 */
public final class DynmapClient {
    private DynmapClient() {}

    /** An online player. name = in-game name, siteName = what the site calls them. */
    public record Online(String account, String name, String siteName, double x, double y, double z, String world, double health) {}
    public record Marker(String id, String label, String icon, double x, double z) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static ScheduledExecutorService scheduler;

    private static volatile List<Online> players = List.of();
    private static volatile long playersAt;
    private static volatile List<Marker> markers = List.of();
    private static volatile long markersAt;
    private static volatile boolean playersFailed;
    private static volatile long playersEvery = 1000;           // how often the players are being asked for, so the map can glide between answers
    private static volatile long wantMap, wantMarkers, wantBackground;   // last time the map was open / a live war was being looked at / the chat alerts needed the markers
    private static long timestamp;                          // dynmap's, sent back so it only sends what changed
    private static long lastPlayersPoll, lastMarkersPoll;
    private static final AtomicBoolean busyPlayers = new AtomicBoolean(), busyMarkers = new AtomicBoolean();
    private static volatile Runnable onMarkers = () -> {};

    /** The map changed: forget what the other world's dynmap said. */
    public static void reset() { players = List.of(); playersAt = 0; markers = List.of(); markersAt = 0; timestamp = 0; }
    public static List<Online> players() { return players; }
    public static List<Marker> markers() { return markers; }
    public static long markersAt() { return markersAt; }
    /** Milliseconds between answers about the players, for gliding the markers from one spot to the next. */
    public static long playersEveryMs() { return playersEvery; }
    /** When the player list last changed hands (millis), to tell a new answer from an old one. */
    public static long playersAt() { return playersAt; }
    /** True once dynmap has answered recently, so "not online" can be told from "don't know". */
    public static boolean playersKnown() { return playersAt != 0 && System.currentTimeMillis() - playersAt < 60_000; }
    public static boolean markersKnown() { return markersAt != 0 && System.currentTimeMillis() - markersAt < 120_000; }
    public static void onMarkersChanged(Runnable callback) { onMarkers = callback == null ? () -> {} : callback; }

    /** The map is on screen: keep the player list fresh. */
    public static void wantPlayers() { wantMap = System.currentTimeMillis(); ensureStarted(); }
    /** A live war is being looked at: keep the markers fresh too. */
    public static void wantMarkers() { wantMarkers = System.currentTimeMillis(); wantMap = wantMarkers; ensureStarted(); }
    /** No map open, but a live war should still be followed (for the chat alerts): the markers, less often. */
    public static void wantMarkersInBackground() { wantBackground = System.currentTimeMillis(); ensureStarted(); }

    /** Where a player's face is (what the site shows next to them on its map). */
    public static String faceUrl(String account) {
        String base = base();
        return base == null || account == null || account.isBlank() ? null : base + "tiles/faces/32x32/" + URLEncoder.encode(account, StandardCharsets.UTF_8) + ".png";
    }

    /** The online player with this site name or in-game name, or null. */
    public static Online find(String siteOrIgn) {
        if (siteOrIgn == null || !playersKnown()) return null;
        for (Online p : players) {
            if (p.siteName.equalsIgnoreCase(siteOrIgn) || p.name.equalsIgnoreCase(siteOrIgn) || p.account.equalsIgnoreCase(siteOrIgn)) return p;
        }
        return null;
    }

    private static synchronized void ensureStarted() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cumania-dynmap"); t.setDaemon(true); return t; });
        scheduler.scheduleWithFixedDelay(DynmapClient::tick, 0, 200, TimeUnit.MILLISECONDS);
    }

    private static void tick() {
        try {
            var config = TerritoryManager.config();
            if (!config.showOnlinePlayers && !config.liveWar && !config.showSwords && !config.autoMap) return;
            long now = System.currentTimeMillis();
            long every = Math.max(500, config.dynmapRefreshMs);
            playersEvery = every;
            if (now - wantMap < 3000 && now - lastPlayersPoll >= every && (config.showOnlinePlayers || config.showSwords || config.autoMap)) { lastPlayersPoll = now; pollPlayers(); }
            boolean watching = now - wantMarkers < 3000;
            boolean background = now - wantBackground < 3000;
            long markersEvery = watching ? 8000 : 20000;
            if ((watching || background) && now - lastMarkersPoll >= markersEvery && config.liveWar) { lastMarkersPoll = now; pollMarkers(); }
        } catch (RuntimeException e) {
            CumaniaTerritories.LOGGER.debug("dynmap tick failed", e);
        }
    }

    private static String base() {
        String url = TerritoryManager.config().dynmapUrl;
        return url == null || url.isBlank() ? null : url.endsWith("/") ? url : url + "/";
    }

    private static String world() { return URLEncoder.encode(TerritoryManager.dynmapWorld(), StandardCharsets.UTF_8); }

    private static void get(String url, java.util.function.Consumer<String> ok, Runnable failed, AtomicBoolean busy) {
        if (!busy.compareAndSet(false, true)) return;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", "cumania-territories")
                        .header("Cache-Control", "no-cache").GET().build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null || response.statusCode() != 200) throw new RuntimeException(error != null ? error.toString() : "HTTP " + response.statusCode());
                        ok.accept(response.body());
                    } catch (RuntimeException e) {
                        CumaniaTerritories.LOGGER.debug("dynmap {}: {}", url, e.toString());
                        failed.run();
                    } finally { busy.set(false); }
                });
    }

    private static void pollPlayers() {
        String base = base();
        if (base == null) return;
        get(base + "up/world/" + world() + "/" + timestamp, body -> {
            TerritoryIndex index = TerritoryManager.index();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("timestamp") && root.get("timestamp").isJsonPrimitive()) timestamp = root.get("timestamp").getAsLong();
            players = parsePlayers(root, index);
            playersAt = System.currentTimeMillis();
            playersFailed = false;
        }, () -> playersFailed = true, busyPlayers);
    }

    private static void pollMarkers() {
        String base = base();
        if (base == null) return;
        get(base + "tiles/_markers_/marker_" + world() + ".json", body -> {
            List<Marker> parsed = parseMarkers(body);
            boolean changed = !parsed.equals(markers);
            markers = parsed;
            markersAt = System.currentTimeMillis();
            if (changed) onMarkers.run();
        }, () -> {}, busyMarkers);
    }

    // ---- parsing (also used by the tests) ----

    /** Colour codes (§x) and HTML tags dynmap leaves in names. */
    public static String cleanName(String raw) {
        return raw == null ? "" : raw.replaceAll("<[^>]*>", "").replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "").trim();
    }

    public static List<Online> parsePlayers(JsonObject root, TerritoryIndex index) {
        List<Online> out = new ArrayList<>();
        if (!root.has("players") || !root.get("players").isJsonArray()) return out;
        for (JsonElement e : root.getAsJsonArray("players")) {
            if (!e.isJsonObject()) continue;
            JsonObject p = e.getAsJsonObject();
            try {
                String account = str(p, "account"), name = cleanName(str(p, "name"));
                if (account.isEmpty()) account = name;
                if (name.isEmpty()) name = account;
                String site = index == null ? name : index.siteNameForIgn(account.isEmpty() ? name : account);
                if (index != null && site.equals(account) && !name.equals(account)) site = index.siteNameForIgn(name);
                out.add(new Online(account, name, site, num(p, "x"), num(p, "y"), num(p, "z"), str(p, "world"), num(p, "health")));
            } catch (RuntimeException ignored) { /* a player we can't read: skip */ }
        }
        return List.copyOf(out);
    }

    public static List<Marker> parseMarkers(String json) {
        List<Marker> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("sets") || !root.get("sets").isJsonObject()) return List.of();
        for (Map.Entry<String, JsonElement> set : root.getAsJsonObject("sets").entrySet()) {
            if (!set.getValue().isJsonObject() || !set.getValue().getAsJsonObject().has("markers")) continue;
            JsonObject ms = set.getValue().getAsJsonObject().getAsJsonObject("markers");
            for (Map.Entry<String, JsonElement> m : ms.entrySet()) {
                if (!m.getValue().isJsonObject()) continue;
                JsonObject o = m.getValue().getAsJsonObject();
                try { out.add(new Marker(m.getKey(), cleanName(str(o, "label")), str(o, "icon"), num(o, "x"), num(o, "z"))); }
                catch (RuntimeException ignored) { }
            }
        }
        return List.copyOf(out);
    }

    private static String str(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : ""; }
    private static double num(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : 0; }
}
