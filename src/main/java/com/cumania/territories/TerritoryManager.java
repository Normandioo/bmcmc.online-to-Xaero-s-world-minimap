package com.cumania.territories;

import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.TerritoryIndex;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Holds the current territories and keeps them fresh. Everything the map threads read is volatile/immutable. */
public final class TerritoryManager {
    private TerritoryManager() {}

    private static Path configFile, cacheFile;
    private static volatile Config config = new Config();
    private static volatile TerritoryIndex index = TerritoryIndex.EMPTY;
    private static volatile int version = 1;
    private static volatile String generated = "";
    private static int paintedFingerprint;
    private static final AtomicBoolean loading = new AtomicBoolean();
    private static volatile boolean reloadAgain;
    private static volatile String loadError = "";
    /** Why the last download failed ("" when it worked), for the settings menu and /territories status. */
    public static String loadError() { return loadError; }
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static ScheduledExecutorService scheduler;

    public static void init(Path configDir) {
        configFile = configDir.resolve("cumania_territories.json");
        cacheFile = configDir.resolve("cumania_territories_cache.json");
        config = Config.load(configFile);
        MapCatalog.init(configDir);
        cacheFile = cacheFor(config.map);
        // Show the last download immediately, then check the site
        try { if (Files.exists(cacheFile)) apply(TerritoryFile.parse(Files.readString(cacheFile)), false); }
        catch (IOException | RuntimeException e) { CumaniaTerritories.LOGGER.warn("Ignoring unreadable cache", e); }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cumania-territories"); t.setDaemon(true); return t; });
        rescheduleRefresh();
        scheduler.scheduleWithFixedDelay(MapCatalog::refresh, 0, 30, TimeUnit.MINUTES);   // the list of maps: now, then every half hour
        reload();
    }

    // ---- the site's maps (Gaia, Cumania IV): each has its own territories.json in its own folder, and its own world on the dynmap ----

    private static MapCatalog.Entry mapInfo(String key) { return MapCatalog.get(key); }
    public static String mapName() { return mapInfo(config.map).name; }

    /** The territories file of the map being shown. */
    public static String mapUrl() { return MapCatalog.territoriesUrl(mapInfo(config.map)); }

    public static String dynmapWorld() { return dynmapWorldOf(mapInfo(config.map)); }
    private static String dynmapWorldOf(MapCatalog.Entry m) { return m.dynmapWorld != null ? m.dynmapWorld : config.dynmapWorld; }

    /** The dynmap of the map being shown. */
    public static String dynmapUrl() { String u = mapInfo(config.map).dynmapUrl; return u != null ? u : config.dynmapUrl; }

    /** The list of maps changed (or came from the site for the first time): keep showing a map that still exists, and reload if its file moved. */
    public static void catalogChanged() {
        var wanted = MapCatalog.list();
        boolean exists = false;
        for (var m : wanted) if (m.key.equalsIgnoreCase(config.map)) exists = true;
        if (!exists) { chooseMap(wanted.get(0).key); return; }
        reload();
    }

    /** The map that wants the server you are on, when the list names one (Auto only). */
    public static void followServer(String address) {
        if (!config.autoMap) return;
        var m = MapCatalog.forServer(address);
        if (m != null && !m.key.equalsIgnoreCase(config.map)) chooseMap(m.key);
    }

    private static Path cacheFor(String key) {
        Path dir = configFile.getParent();
        return "gaia".equalsIgnoreCase(key) ? dir.resolve("cumania_territories_cache.json") : dir.resolve("cumania_territories_cache_" + key + ".json");
    }

    /** What the settings row shows: "Auto (Cumania IV)", "Gaia"... */
    public static String mapLabel() { return config.autoMap ? "Auto (" + mapName() + ")" : mapName(); }

    /** The settings button: Auto -> each map in turn -> Auto. */
    public static void nextMap() {
        var maps = MapCatalog.list();
        if (config.autoMap) { config.autoMap = false; chooseMap(maps.get(0).key); return; }
        for (int i = 0; i < maps.size(); i++) {
            if (!maps.get(i).key.equalsIgnoreCase(config.map)) continue;
            if (i + 1 < maps.size()) chooseMap(maps.get(i + 1).key);
            else { config.autoMap = true; saveConfig(); }
            return;
        }
        config.autoMap = true;
        saveConfig();
    }

    /**
     * Auto: both maps can be on the same server, so the map follows the dynmap world you are in (the dynmap lists every player with its world).
     * Called once a second with that world's name, or null when dynmap has not shown you yet.
     */
    public static void followWorld(String dynmapWorld) {
        if (!config.autoMap || dynmapWorld == null) return;
        for (var m : MapCatalog.list()) {
            if (dynmapWorldOf(m).equalsIgnoreCase(dynmapWorld)) { if (!m.key.equalsIgnoreCase(config.map)) chooseMap(m.key); return; }
        }
    }

    /** Switches to a map: nothing of the old one stays on screen. */
    public static void chooseMap(String key) {
        String wanted = mapInfo(key).key;
        saveConfig();
        if (wanted.equalsIgnoreCase(config.map)) return;
        config.map = wanted;
        saveConfig();
        cacheFile = cacheFor(wanted);
        index = TerritoryIndex.EMPTY;
        loadError = "";
        paintedFingerprint = 0;
        selected = -1;
        generated = "";
        com.cumania.territories.data.WarPaint.set(null);
        com.cumania.territories.data.WarMarkers.clear();
        com.cumania.territories.data.DynmapClient.reset();
        Minecraft.getInstance().execute(() -> { com.cumania.territories.compat.CountryPanel.clear(); if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) com.cumania.territories.compat.WarMenu.close(); });
        try { if (Files.exists(cacheFile)) apply(TerritoryFile.parse(Files.readString(cacheFile)), true); else changed(); }
        catch (IOException | RuntimeException e) { changed(); }
        reload();
    }

    private static void rescheduleRefresh() {
        int minutes = config.refreshMinutes;
        if (minutes > 0) scheduler.scheduleWithFixedDelay(TerritoryManager::reload, minutes, minutes, TimeUnit.MINUTES);
    }

    // ---- read by the map highlighters ----

    public static boolean active() { return config.enabled && !index.isEmpty(); }
    public static TerritoryIndex index() { return index; }
    public static int version() { return version; }

    private static volatile int selected = -1;
    /** The country shown in the info panel (index into the current data), or -1. */
    public static int selected() { return selected; }
    public static void select(int country) { selected = country; }
    public static Config config() { return config; }
    public static String generated() { return generated; }

    /**
     * Whether the territories belong to this dimension of Xaero's map. Gaia's are the overworld's. An event server (Cumania IV) may keep its world
     * under a dimension of its own, so there any dimension that is not the Nether or the End counts.
     */
    public static boolean inDimension(ResourceKey<Level> key) {
        if (key == null) return false;
        String d = mapInfo(config.map).dimension.toLowerCase();
        if (d.equals("any")) return key != Level.NETHER && key != Level.END;
        return key == (d.equals("config") ? dimension() : named(d));
    }

    /** The dimension to ask Xaero's to redraw: the one you are in, when the map is not Gaia's. */
    private static ResourceKey<Level> refreshDimension() {
        var level = Minecraft.getInstance().level;
        return mapInfo(config.map).dimension.equalsIgnoreCase("any") && level != null && inDimension(level.dimension()) ? level.dimension() : currentDimension();
    }

    private static ResourceKey<Level> currentDimension() {
        String d = mapInfo(config.map).dimension.toLowerCase();
        return d.equals("config") || d.equals("any") ? dimension() : named(d);
    }

    private static ResourceKey<Level> named(String d) {
        return switch (d) {
            case "nether", "the_nether" -> Level.NETHER;
            case "end", "the_end" -> Level.END;
            default -> Level.OVERWORLD;
        };
    }

    public static ResourceKey<Level> dimension() {
        return switch (config.dimension.toLowerCase()) {
            case "nether", "the_nether" -> Level.NETHER;
            case "end", "the_end" -> Level.END;
            default -> Level.OVERWORLD;
        };
    }

    public static void saveConfig() { config.save(configFile); }

    // ---- changes ----

    public static boolean toggle() {
        config.enabled = !config.enabled;
        config.save(configFile);
        changed();
        return config.enabled;
    }

    /** Downloads the file in the background and swaps it in if it parses. Safe to call from any thread. */
    public static void reload() {
        String url = mapUrl();
        if (url == null || url.isBlank() || url.contains("YOUR-SITE")) {
            CumaniaTerritories.LOGGER.warn("Set \"url\" in {} to your site's territories.json", configFile);
            return;
        }
        if (!loading.compareAndSet(false, true)) { reloadAgain = true; return; }   // one is already running: go again when it ends (it may be for the other map)
        final String forMap = config.map;
        final Path forCache = cacheFile;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "cumania-territories").GET().build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) throw new IOException(error);
                        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                        TerritoryFile file = TerritoryFile.parse(response.body());
                        if (!forMap.equals(config.map)) { reloadAgain = true; return; }   // the map was switched while this downloaded: it is the other one's
                        Files.writeString(forCache, response.body());
                        loadError = "";
                        apply(file, true);
                    } catch (IOException | RuntimeException e) {
                        loadError = e.toString();
                        CumaniaTerritories.LOGGER.warn("Could not update territories from {}: {}", url, e.toString());
                    } finally {
                        loading.set(false);
                        if (reloadAgain) { reloadAgain = false; reload(); }
                    }
                });
    }

    private static void apply(TerritoryFile file, boolean refreshMaps) {
        TerritoryIndex built = TerritoryIndex.build(file);
        if (built.isEmpty()) { CumaniaTerritories.LOGGER.warn("The territories file has no territories"); return; }
        // only what Xaero's paints matters: if the shapes and colours are the same as before (a new export with new players or wars, say), leave the map alone
        int print = paintFingerprint(file);
        boolean same = !index.isEmpty() && print == paintedFingerprint;
        paintedFingerprint = print;
        index = built;
        selected = -1;   // country numbers may have changed
        com.cumania.territories.data.WarPaint.set(null);
        generated = file.generated == null ? "" : file.generated;
        CumaniaTerritories.LOGGER.info("Territories: {} provinces, {} countries ({})", built.provinceCount(), built.countryCount(), generated);
        if (!same) { if (refreshMaps) changed(); else version++; }
    }

    private static int paintFingerprint(TerritoryFile file) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        int h = gson.toJson(file.provinces).hashCode();
        h = 31 * h + gson.toJson(file.network).hashCode();
        h = 31 * h + gson.toJson(file.zones).hashCode();   // new or moved capture limits redraw the map too
        if (file.countries != null) for (var e : file.countries.entrySet()) h = 31 * h + (e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue().color)).hashCode();
        return h;
    }

    /** The map's colours changed (the war menu painted or cleared it): redraw. */
    public static void repaint() { changed(); }

    /** Redraws just these regions ({regionX, regionZ}) of the World Map; the minimap redraws as usual. */
    public static void repaintRegions(java.util.List<int[]> regions) {
        version++;
        Minecraft.getInstance().execute(() -> {
            if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) com.cumania.territories.compat.WorldMapBridge.refreshRegions(refreshDimension(), regions);
            if (FabricLoader.getInstance().isModLoaded("xaerominimap")) com.cumania.territories.compat.MinimapBridge.refresh();
        });
    }

    /** Tells Xaero's to redraw. The map code isn't thread-safe, so it happens on the game thread. */
    private static void changed() {
        version++;
        Minecraft.getInstance().execute(() -> {
            if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) com.cumania.territories.compat.WorldMapBridge.refresh(refreshDimension());
            if (FabricLoader.getInstance().isModLoaded("xaerominimap")) com.cumania.territories.compat.MinimapBridge.refresh();
        });
    }
}
