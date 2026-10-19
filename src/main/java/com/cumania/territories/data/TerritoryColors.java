package com.cumania.territories.data;

import java.util.Map;

/** The 16x16 colours Xaero's wants for each highlighted chunk: fill inside a territory, a line along its borders. */
public final class TerritoryColors {
    private TerritoryColors() {}

    /**
     * Xaero's packs highlight colours as B<<24 | G<<16 | R<<8 | A (alpha in the LOW byte, red and blue
     * swapped compared to Minecraft's ARGB). Open Parties and Claims and Xaero's own test highlighter do the same.
     */
    public static int pack(int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (b << 24) | (g << 16) | (r << 8) | (alpha & 255);
    }

    /** Mixes an RGB colour towards white by the given share (0 = unchanged, 1 = white). */
    public static int lighten(int rgb, double share) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * share); g += (int) ((255 - g) * share); b += (int) ((255 - b) * share);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Fills out[(z << 4) | x] for every block of the chunk. Blocks nobody owns get 0 (alpha 0, so Xaero's
     * leaves them alone). A block is on the border when one of its four neighbours belongs to another country
     * (or to nobody), so the line follows the territory outline exactly, one block wide, and the lines between
     * provinces of the same country are not drawn.
     *
     * @return false when the chunk has no territory at all
     */
    public static boolean compute(TerritoryIndex index, int chunkX, int chunkZ, int fillAlpha, int borderAlpha, int[] out) {
        boolean territory = index.chunkHasTerritory(chunkX, chunkZ);
        short[] limit = limitsOn() ? index.limits().chunk(chunkX, chunkZ) : null;
        if (!territory && limit == null) return false;
        if (!territory) { java.util.Arrays.fill(out, 0, 256, 0); return overlayLimits(index, limit, out); }
        WarPaint.Paint paint = WarPaint.current();
        boolean greyOthers = com.cumania.territories.TerritoryManager.config().warGreyOthers;
        int baseX = chunkX << 4, baseZ = chunkZ << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int bx = baseX + x, bz = baseZ + z;
                int country = index.countryAtBlock(bx, bz);
                if (country < 0) { out[(z << 4) | x] = 0; continue; }
                int rgb = index.countryRgb(country), fillA = fillAlpha, borderA = borderAlpha;
                if (paint != null) {
                    int painted = country < paint.countryRgb().length ? paint.countryRgb()[country] : -1;
                    if (painted >= 0) rgb = painted; else if (greyOthers) { rgb = WarPaint.NEUTRAL; fillA = fillAlpha / 2; borderA = borderAlpha / 2; }
                    if (!paint.provinceRgb().isEmpty()) {
                        for (Map.Entry<Integer, int[]> e : paint.provinceBounds().entrySet()) {   // few: only the captured provinces
                            int[] b = e.getValue();
                            if (bx < b[0] || bx > b[2] || bz < b[1] || bz > b[3] || index.provinceAtBlock(bx, bz) != e.getKey()) continue;
                            rgb = paint.provinceRgb().get(e.getKey()); fillA = Math.min(255, fillAlpha * 2);
                            break;
                        }
                    }
                }
                boolean edge = index.countryAtBlock(bx, bz - 1) != country || index.countryAtBlock(bx + 1, bz) != country
                        || index.countryAtBlock(bx, bz + 1) != country || index.countryAtBlock(bx - 1, bz) != country;
                out[(z << 4) | x] = pack(rgb, edge ? borderA : fillA);
            }
        }
        return limit == null || overlayLimits(index, limit, out);
    }

    // ---- the site's capture limits ----

    private static final int LIMIT_RGB = 0xFFFFFF, LIMIT_ALPHA = 170, SHADOW_ALPHA = 170;

    private static boolean limitsOn() { return com.cumania.territories.TerritoryManager.config().showCaptureLimits; }

    /** Whether the chunk has anything for the map to paint: territory, or (with capture limits on) a limit's outline. */
    public static boolean chunkHasAnything(TerritoryIndex index, int cx, int cz) {
        return index.chunkHasTerritory(cx, cz) || (limitsOn() && index.limits().chunkHas(cx, cz));
    }

    public static boolean regionHasAnything(TerritoryIndex index, int rx, int rz) {
        return index.regionHasTerritory(rx, rz) || (limitsOn() && index.limits().regionHas(rx, rz));
    }

    /** Dashes on the outline blocks: white, or in a war's view the colour of whoever holds that city (yellow contested, grey uncontrolled). */
    private static boolean overlayLimits(TerritoryIndex index, short[] limit, int[] out) {
        WarPaint.Paint paint = WarPaint.current();
        boolean war = paint != null && !paint.zoneRgb().isEmpty();
        if (war) {
            // a war's view tints the land in these same colours, so the dash alone would vanish into it: a dark shadow on the blocks beside it
            // (inside this chunk), and the dash itself a lighter, solid shade of the holder's colour
            for (int i = 0; i < 256; i++) {
                if (limit[i] == 0) continue;
                int x = i & 15, z = i >> 4;
                if (x > 0 && limit[i - 1] == 0) out[i - 1] = pack(0x000000, SHADOW_ALPHA);
                if (x < 15 && limit[i + 1] == 0) out[i + 1] = pack(0x000000, SHADOW_ALPHA);
                if (z > 0 && limit[i - 16] == 0) out[i - 16] = pack(0x000000, SHADOW_ALPHA);
                if (z < 15 && limit[i + 16] == 0) out[i + 16] = pack(0x000000, SHADOW_ALPHA);
            }
        }
        for (int i = 0; i < 256; i++) {
            int z = limit[i];
            if (z == 0) continue;
            Integer held = paint == null ? null : paint.zoneRgb().get(index.limits().zone(z - 1).key());
            out[i] = held != null ? pack(lighten(held, 0.55), 255) : pack(LIMIT_RGB, war ? 200 : LIMIT_ALPHA);
        }
        return true;
    }
}
