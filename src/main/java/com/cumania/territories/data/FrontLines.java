package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The front of a war laid onto the block grid, like the site's "hot" lines: for every Connection that puts a city In Play, a line
 * three blocks wide from one city to the other, each half in the colour of the side that holds that end. Chunk by chunk, so the
 * highlighter can hand Xaero's the blocks to colour. Immutable. No Minecraft classes in here.
 */
public final class FrontLines {
    public static final FrontLines EMPTY = new FrontLines(List.of(), Map.of(), Set.of(), List.of());

    /** One line: its two ends (block coordinates) and the colour of each half. */
    public record Seg(int x0, int z0, int x1, int z1, int rgbA, int rgbB) {}

    private final List<Integer> palette;
    private final Map<Long, short[]> chunks;   // chunk -> 256 blocks, palette index + 1 or 0
    private final Set<Long> regions;
    private final List<int[]> boxes;           // each line's minX, minZ, maxX, maxZ (a little wider than the line)

    private FrontLines(List<Integer> palette, Map<Long, short[]> chunks, Set<Long> regions, List<int[]> boxes) {
        this.palette = palette; this.chunks = chunks; this.regions = regions; this.boxes = boxes;
    }

    private static long key(int a, int b) { return ((long) a << 32) ^ (b & 0xFFFFFFFFL); }

    public static FrontLines build(List<Seg> segs) { return build(segs, false); }

    /** dotted = one block wide, two blocks on and four off (the quiet lines inside a side); else three blocks wide and solid. */
    public static FrontLines build(List<Seg> segs, boolean dotted) {
        if (segs == null || segs.isEmpty()) return EMPTY;
        List<Integer> palette = new ArrayList<>();
        Map<Long, short[]> chunks = new HashMap<>();
        Set<Long> regions = new HashSet<>();
        List<int[]> boxes = new ArrayList<>();
        for (Seg s : segs) {
            int pa = index(palette, s.rgbA()) + 1, pb = index(palette, s.rgbB()) + 1;
            double dx = s.x1() - s.x0(), dz = s.z1() - s.z0();
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz)));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                int cx = (int) Math.round(s.x0() + dx * t), cz = (int) Math.round(s.z0() + dz * t);
                short v = (short) (t < 0.5 ? pa : pb);
                if (dotted && i % 6 >= 2) continue;
                int r = dotted ? 0 : 1;
                for (int ox = -r; ox <= r; ox++) for (int oz = -r; oz <= r; oz++) {
                    int bx = cx + ox, bz = cz + oz;
                    short[] chunk = chunks.computeIfAbsent(key(bx >> 4, bz >> 4), c -> new short[256]);
                    chunk[((bz & 15) << 4) | (bx & 15)] = v;
                    regions.add(key(bx >> 9, bz >> 9));
                }
            }
            boxes.add(new int[]{Math.min(s.x0(), s.x1()) - 3, Math.min(s.z0(), s.z1()) - 3, Math.max(s.x0(), s.x1()) + 3, Math.max(s.z0(), s.z1()) + 3});
        }
        return new FrontLines(List.copyOf(palette), chunks, regions, List.copyOf(boxes));
    }

    private static int index(List<Integer> palette, int rgb) {
        int i = palette.indexOf(rgb);
        if (i >= 0) return i;
        palette.add(rgb);
        return palette.size() - 1;
    }

    public boolean isEmpty() { return chunks.isEmpty(); }
    public short[] chunk(int cx, int cz) { return chunks.get(key(cx, cz)); }
    public boolean chunkHas(int cx, int cz) { return chunks.containsKey(key(cx, cz)); }
    public boolean regionHas(int rx, int rz) { return regions.contains(key(rx, rz)); }
    /** The colour of a palette entry (a value of chunk(), minus one). */
    public int rgb(int paletteIndexPlusOne) { return palette.get(paletteIndexPlusOne - 1); }
    public List<int[]> boxes() { return boxes; }
    /** Whether two sets of lines are the same (so a repaint can be skipped). */
    public boolean sameAs(FrontLines o) { return palette.equals(o.palette) && boxes.size() == o.boxes.size() && java.util.Arrays.deepEquals(boxes.toArray(), o.boxes.toArray()); }
}
