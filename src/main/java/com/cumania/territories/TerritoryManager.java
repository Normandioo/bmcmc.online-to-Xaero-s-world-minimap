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
        cacheFile = cacheFor(config.map);
        // Show the last download immediately, then check the site
        try { if (Files.exists(cacheFile)) apply(TerritoryFile.parse(Files.readString(cacheFile)), false); }
        catch (IOException | RuntimeException e) { CumaniaTerritories.LOGGER.warn("Ignoring unreadable cache", e); }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cumania-territories"); t.setDaemon(true); return t; });
        rescheduleRefresh();
        reload();
    }

    // ---- the site's maps (Gaia, Cumania IV): each has its own territories.json in its own folder, and its own world on the dynmap ----

    /** key, name, folder on the site, dynmap world (Gaia's is the one in the config). */
    private static final String[][] MAPS = {{"gaia", "Gaia", "gaia/", null}, {"cumania", "Cumania IV", "cumania4/", "Cumania4"}};

    private static String[] mapInfo(String key) {
        for (String[] m : MAPS) if (m[0].equalsIgnoreCase(key)) return m;
        return MAPS[0];
    }
    public static String mapName() { return mapInfo(config.map)[1]; }

    /** The territories file of the map being shown (the config's url is Gaia's; the other maps sit in a sibling folder). */
    public static String mapUrl() {
        String url = config.url, folder = mapInfo(config.map)[2];
        if (url == null || folder.equals("gaia/")) return url;
        int at = url.lastIndexOf("gaia/");
        return at < 0 ? url : url.substring(0, at) + folder + url.substring(at + "gaia/".length());
    }

    public static String dynmapWorld() {
        String world = mapInfo(config.map)[3];
        return world != null ? world : config.dynmapWorld;
    }

    private static Path cacheFor(String key) {
        Path dir = configFile.getParent();
        return "gaia".equalsIgnoreCase(key) ? dir.resolve("cumania_territories_cache.json") : dir.resolve("cumania_territories_cache_" + key + ".json");
    }

    /** What the settings row shows: "Auto (Cumania IV)", "Gaia"... */
    public static String mapLabel() { return config.autoMap ? "Auto (" + mapName() + ")" : mapName(); }

    /** The settings button: Auto -> each map in turn -> Auto. */
    public static void nextMap() {
        if (config.autoMap) { config.autoMap = false; chooseMap(MAPS[0][0]); return; }
        for (int i = 0; i < MAPS.length; i++) {
            if (!MAPS[i][0].equalsIgnoreCase(config.map)) continue;
            if (i + 1 < MAPS.length) chooseMap(MAPS[i + 1][0]);
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
        for (String[] m : MAPS) {
            String world = m[3] != null ? m[3] : config.dynmapWorld;
            if (world.equalsIgnoreCase(dynmapWorld)) { if (!m[0].equalsIgnoreCase(config.map)) chooseMap(m[0]); return; }
        }
    }

    /** Switches to a map: nothing of the old one stays on screen. */
    public static void chooseMap(String key) {
        String wanted = mapInfo(key)[0];
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
        if (!"gaia".equalsIgnoreCase(config.map)) return key != Level.NETHER && key != Level.END;
        return key == dimension();
    }

    /** The dimension to ask Xaero's to redraw: the one you are in, when the map is not Gaia's. */
    private static ResourceKey<Level> refreshDimension() {
        var level = Minecraft.getInstance().level;
        return !"gaia".equalsIgnoreCase(config.map) && level != null && inDimension(level.dimension()) ? level.dimension() : dimension();
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
