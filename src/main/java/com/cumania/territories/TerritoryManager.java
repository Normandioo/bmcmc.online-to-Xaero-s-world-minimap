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
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static ScheduledExecutorService scheduler;

    public static void init(Path configDir) {
        configFile = configDir.resolve("cumania_territories.json");
        cacheFile = configDir.resolve("cumania_territories_cache.json");
        config = Config.load(configFile);
        // Show the last download immediately, then check the site
        try { if (Files.exists(cacheFile)) apply(TerritoryFile.parse(Files.readString(cacheFile)), false); }
        catch (IOException | RuntimeException e) { CumaniaTerritories.LOGGER.warn("Ignoring unreadable cache", e); }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "cumania-territories"); t.setDaemon(true); return t; });
        rescheduleRefresh();
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
        String url = config.url;
        if (url == null || url.isBlank() || url.contains("YOUR-SITE")) {
            CumaniaTerritories.LOGGER.warn("Set \"url\" in {} to your site's territories.json", configFile);
            return;
        }
        if (!loading.compareAndSet(false, true)) return;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "cumania-territories").GET().build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) throw new IOException(error);
                        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                        TerritoryFile file = TerritoryFile.parse(response.body());
                        Files.writeString(cacheFile, response.body());
                        apply(file, true);
                    } catch (IOException | RuntimeException e) {
                        CumaniaTerritories.LOGGER.warn("Could not update territories from {}: {}", url, e.toString());
                    } finally {
                        loading.set(false);
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
        if (refreshMaps) CaptureAlerts.checkSoon();   // the site's list of captures may have changed too
    }

    private static int paintFingerprint(TerritoryFile file) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        int h = gson.toJson(file.provinces).hashCode();
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
            if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) com.cumania.territories.compat.WorldMapBridge.refreshRegions(dimension(), regions);
            if (FabricLoader.getInstance().isModLoaded("xaerominimap")) com.cumania.territories.compat.MinimapBridge.refresh();
        });
    }

    /** Tells Xaero's to redraw. The map code isn't thread-safe, so it happens on the game thread. */
    private static void changed() {
        version++;
        Minecraft.getInstance().execute(() -> {
            if (FabricLoader.getInstance().isModLoaded("xaeroworldmap")) com.cumania.territories.compat.WorldMapBridge.refresh(dimension());
            if (FabricLoader.getInstance().isModLoaded("xaerominimap")) com.cumania.territories.compat.MinimapBridge.refresh();
        });
    }
}
