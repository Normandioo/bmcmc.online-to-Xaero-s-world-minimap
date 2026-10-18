package com.cumania.territories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/cumania_territories.json */
public final class Config {
    /** Where the site publishes the territories (gaia/territories.json on the site). */
    public String url = "https://bmcmc.online/gaia/territories.json";
    public boolean enabled = true;
    /** Which dimension the territories belong to: "overworld", "nether" or "end". */
    public String dimension = "overworld";
    /** How often to check the site for changes while the game is open. 0 = only on joining a world. */
    public int refreshMinutes = 30;
    /** 0-255. Fill is the tint over the whole territory, border the line along country borders. */
    public int fillAlpha = 90;
    public int borderAlpha = 230;
    /** Also show the province name in the hover tooltip, not just the country. */
    public boolean showProvince = true;
    /** Click a country (or a city) on Xaero's World Map to open the info panel. */
    public boolean showPanel = true;
    /** Draw the cities from the site on Xaero's World Map, and their names. */
    public boolean showLocalities = true;
    public boolean showLocalityNames = true;
    /** Raise to see small cities and names at lower zoom, lower to see fewer. */
    public double localityZoomBias = 1.0;
    /** Zoom the map to at least this when jumping to a lord's city from the player card (never zooms out). 0 = keep the zoom. */
    public double goToZoom = 1.5;
    /** The WAR button on Xaero's World Map (and /territories war): wars, alliances and diplomatic timers from the site. */
    public boolean showWarMenu = true;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config load(Path file) {
        Config c = new Config();
        try {
            if (Files.exists(file)) {
                Config read = GSON.fromJson(Files.readString(file), Config.class);
                if (read != null) c = read;
            }
        } catch (IOException | RuntimeException e) {
            CumaniaTerritories.LOGGER.warn("Could not read {}; using defaults", file, e);
        }
        c.fillAlpha = Math.max(0, Math.min(255, c.fillAlpha));
        c.borderAlpha = Math.max(0, Math.min(255, c.borderAlpha));
        c.save(file);   // writes defaults the first time, and adds options new versions introduce
        return c;
    }

    public void save(Path file) {
        try { Files.writeString(file, GSON.toJson(this)); }
        catch (IOException e) { CumaniaTerritories.LOGGER.warn("Could not write {}", file, e); }
    }
}
