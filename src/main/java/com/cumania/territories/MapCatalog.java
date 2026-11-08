package com.cumania.territories;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The list of maps (Gaia, Cumania IV, ...): which territories file each one has, which world it is on the dynmap and in which dimension it lives.
 * It is a "maps.json" on the site, downloaded at start and kept in a cache, so a new world or server is a line in that file and not a new mod version.
 * What is built into the mod (Gaia and Cumania IV) is only the fallback for when the site cannot be reached.
 */
public final class MapCatalog {
    private MapCatalog() {}

    public static final class Entry {
        public String key, name;
        /** Absolute URL of the territories.json, or relative to maps.json. */
        public String territories;
        /** The world's name on the dynmap; empty = the one in the config. */
        public String dynmapWorld;
        /** Another dynmap for this map; empty = the one in the config. */
        public String dynmapUrl;
        /** "config" (the dimension in the config, Gaia's way), "overworld", "nether", "end", or "any" (any dimension but the Nether and the End: a world kept under a dimension of its own). */
        public String dimension = "config";
        /** Server addresses where this map is the one to show (optional). */
        public List<String> servers = new ArrayList<>();
    }

    private static final class File { int version; List<Entry> maps = new ArrayList<>(); }

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static volatile List<Entry> downloaded = List.of();
    private static volatile String source = "built in";
    private static volatile String error = "";
    private static Path cache;

    /** What the last download of maps.json said was wrong ("" when it was fine). */
    public static String error() { return error; }
    /** Where the list comes from: "built in", "cache" or "site". */
    public static String source() { return source; }

    public static void init(Path configDir) {
        cache = configDir.resolve("cumania_territories_maps.json");
        try { if (Files.exists(cache)) { var list = parse(Files.readString(cache)); downloaded = list; source = "cache"; } }
        catch (IOException | RuntimeException e) { CumaniaTerritories.LOGGER.warn("Ignoring unreadable maps cache", e); }
    }

    /** The maps to offer: the site's list, else the built-in one. Never empty. */
    public static List<Entry> list() {
        List<Entry> list = downloaded;
        return list.isEmpty() ? builtIn() : list;
    }

    public static Entry get(String key) {
        for (Entry e : list()) if (e.key.equalsIgnoreCase(key)) return e;
        return list().get(0);
    }

    /** The map that wants this server address, or null. */
    public static Entry forServer(String address) {
        if (address == null || address.isBlank()) return null;
        String host = address.toLowerCase().trim();
        for (Entry e : list()) for (String s : e.servers) if (s != null && !s.isBlank() && host.equals(s.toLowerCase().trim())) return e;
        return null;
    }

    private static List<Entry> builtIn() {
        String url = TerritoryManager.config().url;
        Entry gaia = new Entry();
        gaia.key = "gaia"; gaia.name = "Gaia"; gaia.territories = url;
        Entry cumania = new Entry();
        cumania.key = "cumania"; cumania.name = "Cumania IV"; cumania.dynmapWorld = "Cumania4"; cumania.dimension = "any";
        int at = url == null ? -1 : url.lastIndexOf("gaia/");
        cumania.territories = at < 0 ? url : url.substring(0, at) + "cumania4/" + url.substring(at + "gaia/".length());
        return List.of(gaia, cumania);
    }

    /** The territories URL of a map, resolved against the catalog's own address when it is relative. */
    public static String territoriesUrl(Entry e) {
        String t = e.territories;
        if (t == null || t.isBlank()) return null;
        if (t.startsWith("http://") || t.startsWith("https://")) return t;
        try { return URI.create(TerritoryManager.config().catalogUrl).resolve(t).toString(); }
        catch (RuntimeException ex) { return t; }
    }

    /** Downloads maps.json in the background; if it is good the maps in use are swapped for it. */
    public static void refresh() {
        String url = TerritoryManager.config().catalogUrl;
        if (url == null || url.isBlank()) return;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "cumania-territories").GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, failure) -> {
                    try {
                        if (failure != null) throw new IOException(failure);
                        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                        List<Entry> list = parse(response.body());
                        Files.writeString(cache, response.body());
                        boolean same = GSON.toJson(list).equals(GSON.toJson(downloaded));
                        downloaded = list; source = "site"; error = "";
                        if (!same) TerritoryManager.catalogChanged();
                    } catch (IOException | RuntimeException e) {
                        error = e.getMessage() == null ? e.toString() : e.getMessage();
                        CumaniaTerritories.LOGGER.warn("Could not update the maps list from {}: {}", url, e.toString());
                    }
                });
    }

    /** Reads maps.json; anything wrong with it (not JSON, no maps, an entry without key or territories) makes the whole file be refused. */
    static List<Entry> parse(String json) {
        File file;
        try { file = GSON.fromJson(json, File.class); } catch (JsonSyntaxException e) { throw new IllegalArgumentException("maps.json is not valid JSON: " + e.getMessage()); }
        if (file == null || file.maps == null || file.maps.isEmpty()) throw new IllegalArgumentException("maps.json lists no maps");
        List<Entry> out = new ArrayList<>();
        for (Entry e : file.maps) {
            if (e == null || e.key == null || e.key.isBlank() || e.territories == null || e.territories.isBlank())
                throw new IllegalArgumentException("a map in maps.json has no key or no territories address");
            if (e.name == null || e.name.isBlank()) e.name = e.key;
            if (e.dynmapWorld != null && e.dynmapWorld.isBlank()) e.dynmapWorld = null;
            if (e.dynmapUrl != null && e.dynmapUrl.isBlank()) e.dynmapUrl = null;
            if (e.dimension == null || e.dimension.isBlank()) e.dimension = "config";
            if (e.servers == null) e.servers = new ArrayList<>();
            out.add(e);
        }
        return List.copyOf(out);
    }
}
