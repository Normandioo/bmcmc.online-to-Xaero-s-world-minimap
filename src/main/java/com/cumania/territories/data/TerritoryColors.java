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
        if (!index.chunkHasTerritory(chunkX, chunkZ)) return false;
        WarPaint.Paint paint = WarPaint.xaeroPaint();
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
        return true;
    }
}
