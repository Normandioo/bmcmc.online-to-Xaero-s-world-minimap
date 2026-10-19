package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The capture limits the site draws in gaia/capture.svg ("zones" in territories.json): the land a side must hold to take a city in a war
 * (the BmcWar server plugin counts the same land). Each limit is laid onto the block grid as a dashed outline, chunk by chunk, so the
 * highlighter can hand Xaero's the blocks to colour. Several rings of one zone are one outline, and a ring inside another is a hole whose
 * outline is drawn too. Immutable once built. No Minecraft classes in here.
 */
public final class CaptureLimits {
    public static final CaptureLimits EMPTY = new CaptureLimits(List.of(), Map.of(), Set.of(), Map.of());

    /** key = "name(country)" in lower case, the same as WarScore.key. */
    public record Zone(String name, String country, String key) {}

    private static final int DASH_ON = 8, DASH_PERIOD = 13;   // blocks along the outline: a dash and its gap

    private final List<Zone> zones;
    private final Map<Long, short[]> chunks;   // chunk -> 256 blocks, zone index + 1 where the outline is, 0 elsewhere
    private final Set<Long> regions;
    private final Map<String, int[]> boxes;    // zone key -> minX, minZ, maxX, maxZ

    private CaptureLimits(List<Zone> zones, Map<Long, short[]> chunks, Set<Long> regions, Map<String, int[]> boxes) {
        this.zones = zones; this.chunks = chunks; this.regions = regions; this.boxes = boxes;
    }

    private static long key(int a, int b) { return ((long) a << 32) ^ (b & 0xFFFFFFFFL); }

    public static CaptureLimits build(List<TerritoryFile.Zone> list) {
        if (list == null || list.isEmpty()) return EMPTY;
        List<Zone> zones = new ArrayList<>();
        Map<Long, short[]> chunks = new HashMap<>();
        Set<Long> regions = new HashSet<>();
        Map<String, int[]> boxes = new HashMap<>();
        for (TerritoryFile.Zone z : list) {
            if (z == null || z.name == null || z.country == null || z.rings == null) continue;
            int zi = zones.size();
            String k = WarScore.key(z.name, z.country);
            int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            boolean any = false;
            for (double[] ring : z.rings) {
                if (ring == null || ring.length < 6) continue;
                int n = ring.length / 2;
                double along = 0;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double ax = ring[2 * i], az = ring[2 * i + 1], dx = ring[2 * j] - ax, dz = ring[2 * j + 1] - az;
                    double len = Math.hypot(dx, dz);
                    int steps = (int) Math.ceil(len);
                    for (int s = 0; s < steps; s++) {
                        if (((int) (along + s)) % DASH_PERIOD >= DASH_ON) continue;
                        int bx = (int) Math.floor(ax + dx * s / steps), bz = (int) Math.floor(az + dz * s / steps);
                        short[] chunk = chunks.computeIfAbsent(key(bx >> 4, bz >> 4), c -> new short[256]);
                        chunk[((bz & 15) << 4) | (bx & 15)] = (short) (zi + 1);
                        regions.add(key(bx >> 9, bz >> 9));
                        box[0] = Math.min(box[0], bx); box[1] = Math.min(box[1], bz); box[2] = Math.max(box[2], bx); box[3] = Math.max(box[3], bz);
                        any = true;
                    }
                    along += len;
                }
            }
            zones.add(new Zone(z.name, z.country, k));
            if (any) boxes.put(k, box);
        }
        return zones.isEmpty() || chunks.isEmpty() ? EMPTY : new CaptureLimits(List.copyOf(zones), chunks, regions, boxes);
    }

    public boolean isEmpty() { return chunks.isEmpty(); }
    public int zoneCount() { return zones.size(); }

    /** The outline blocks of the chunk (256 entries, zone index + 1 or 0), or null when it has none. */
    public short[] chunk(int cx, int cz) { return chunks.get(key(cx, cz)); }
    public boolean chunkHas(int cx, int cz) { return chunks.containsKey(key(cx, cz)); }
    public boolean regionHas(int rx, int rz) { return regions.contains(key(rx, rz)); }
    public Zone zone(int index) { return zones.get(index); }

    /** The block boxes {minX, minZ, maxX, maxZ} of these zones (by key), for redrawing just where their colour changed. */
    public List<int[]> boxesOf(Collection<String> keys) {
        List<int[]> out = new ArrayList<>();
        for (String k : keys) { int[] b = boxes.get(k); if (b != null) out.add(b); }
        return out;
    }
}
