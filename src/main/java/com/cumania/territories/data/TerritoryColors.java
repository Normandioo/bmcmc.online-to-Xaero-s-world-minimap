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
        short[] front = frontChunk(chunkX, chunkZ);
        if (!territory && limit == null && front == null) return false;
        if (!territory) {
            java.util.Arrays.fill(out, 0, 256, 0);
            if (limit != null) overlayLimits(index, limit, out);
            overlayFront(front, out);
            return true;
        }
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
        if (paint != null && com.cumania.territories.TerritoryManager.config().showBorderGlow) glowBorder(index, paint, baseX, baseZ, out);
        if (limit != null) overlayLimits(index, limit, out);
        overlayFront(front, out);
        return true;
    }

    // ---- the border between two sides ----

    private static final int GLOW = 3;   // blocks of glow on each side of the border

    /** The war colour of the land at a block (a side's, contested yellow...), or -1 for land that is not painted by the war. */
    private static int warRgb(TerritoryIndex index, WarPaint.Paint paint, int bx, int bz) {
        int country = index.countryAtBlock(bx, bz);
        if (country < 0) return -1;
        int painted = country < paint.countryRgb().length ? paint.countryRgb()[country] : -1;
        if (!paint.provinceRgb().isEmpty()) {
            for (Map.Entry<Integer, int[]> e : paint.provinceBounds().entrySet()) {
                int[] b = e.getValue();
                if (bx < b[0] || bx > b[2] || bz < b[1] || bz > b[3] || index.provinceAtBlock(bx, bz) != e.getKey()) continue;
                return paint.provinceRgb().get(e.getKey());
            }
        }
        return painted;
    }

    private static final int[][] AROUND = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

    /**
     * Along the whole front, where the land of one side meets the land of another: a bright band on both sides, solid at the line and
     * fading over GLOW blocks, with a few yellow sparks. Each side's band is a lighter shade of its own colour.
     */
    private static void glowBorder(TerritoryIndex index, WarPaint.Paint paint, int baseX, int baseZ, int[] out) {
        boolean two = false;
        int first = -1;
        for (int rgb : paint.countryRgb()) { if (rgb < 0) continue; if (first < 0) first = rgb; else if (rgb != first) { two = true; break; } }
        if (!two && paint.provinceRgb().isEmpty()) return;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int bx = baseX + x, bz = baseZ + z;
                int own = warRgb(index, paint, bx, bz);
                if (own < 0) continue;
                int dist = 0;
                search:
                for (int d = 1; d <= GLOW; d++) {
                    for (int[] o : AROUND) {
                        int other = warRgb(index, paint, bx + o[0] * d, bz + o[1] * d);
                        if (other >= 0 && other != own) { dist = d; break search; }
                    }
                }
                if (dist == 0) continue;
                if (dist <= 2 && Math.floorMod(bx * 7 + bz * 13, 11) == 0) { out[(z << 4) | x] = pack(0xFFE060, 255); continue; }
                double share = dist == 1 ? 0.7 : dist == 2 ? 0.4 : 0.18;
                int alpha = dist == 1 ? 255 : dist == 2 ? 235 : 200;
                out[(z << 4) | x] = pack(lighten(own, share), alpha);
            }
        }
    }

    // ---- the front of a war ----

    private static boolean frontOn() {
        return com.cumania.territories.TerritoryManager.config().showFrontLines && WarPaint.current() != null && !WarPaint.current().front().isEmpty();
    }

    private static short[] frontChunk(int cx, int cz) { return frontOn() ? WarPaint.current().front().chunk(cx, cz) : null; }

    /** The lines that put cities In Play: solid, bright, with a dark edge so they stand out over the tinted land. */
    private static void overlayFront(short[] front, int[] out) {
        if (front == null) return;
        FrontLines lines = WarPaint.current().front();
        for (int i = 0; i < 256; i++) {
            if (front[i] == 0) continue;
            int x = i & 15, z = i >> 4;
            if (x > 0 && front[i - 1] == 0) out[i - 1] = pack(0x000000, SHADOW_ALPHA);
            if (x < 15 && front[i + 1] == 0) out[i + 1] = pack(0x000000, SHADOW_ALPHA);
            if (z > 0 && front[i - 16] == 0) out[i - 16] = pack(0x000000, SHADOW_ALPHA);
            if (z < 15 && front[i + 16] == 0) out[i + 16] = pack(0x000000, SHADOW_ALPHA);
        }
        for (int i = 0; i < 256; i++) if (front[i] != 0) out[i] = pack(lighten(lines.rgb(front[i]), 0.25), 255);
    }

    // ---- the site's capture limits ----

    private static final int LIMIT_RGB = 0xFFFFFF, LIMIT_ALPHA = 170, SHADOW_ALPHA = 170;

    private static boolean limitsOn() { return com.cumania.territories.TerritoryManager.config().showCaptureLimits; }

    /** Whether the chunk has anything for the map to paint: territory, or (with capture limits on) a limit's outline. */
    public static boolean chunkHasAnything(TerritoryIndex index, int cx, int cz) {
        return index.chunkHasTerritory(cx, cz) || (limitsOn() && index.limits().chunkHas(cx, cz)) || (frontOn() && WarPaint.current().front().chunkHas(cx, cz));
    }

    public static boolean regionHasAnything(TerritoryIndex index, int rx, int rz) {
        return index.regionHasTerritory(rx, rz) || (limitsOn() && index.limits().regionHas(rx, rz)) || (frontOn() && WarPaint.current().front().regionHas(rx, rz));
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
