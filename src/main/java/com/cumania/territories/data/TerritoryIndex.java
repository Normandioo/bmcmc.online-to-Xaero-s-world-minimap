package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The territories laid onto Minecraft's block grid. A block belongs to the province that contains its
 * centre (x + 0.5, z + 0.5), and later provinces in the file paint over earlier ones, exactly like the SVG.
 * Immutable once built, so the map threads can read it without locking.
 *
 * Memory: one byte per block of the territory bounding box (about 25 MB for a 5000x5000 map).
 */
public final class TerritoryIndex {
    public static final TerritoryIndex EMPTY = new TerritoryIndex(0, 0, 0, 0, new byte[0], List.of(),
            new String[0], new String[0], new int[0], new short[0], List.of(), List.of());

    private final int minX, minZ, width, height;          // block bounding box
    private final byte[] grid;                            // country index + 1, 0 = nobody
    private final List<TerritoryFile.Province> provinces;
    private final String[] provinceLabels;
    private final short[] countryOfProvince;
    private final int[][] provinceBounds;                 // minX, minZ, maxX, maxZ per province
    private final String[] countryIds, countryNames;
    private final int[] countryRgb;
    private final List<TerritoryFile.Country> countryData;
    private final List<TerritoryFile.Locality> localities;
    private final Map<String, Integer> countryByNameId;
    private TerritoryFile.WarData war = new TerritoryFile.WarData();
    private Map<String, TerritoryFile.Player> players = Map.of();   // lower-case site name -> record
    private Map<String, String> siteNameByIgn = Map.of();            // lower-case in-game name -> the site's name for the person
    private final boolean[] chunkHas;                     // chunks with at least one claimed block
    private final boolean[] regionHas;                    // 32x32 chunk regions
    private final long[] regionCountries;                // 4 longs per region: which countries have blocks in it
    private final int minCx, minCz, chunksW, chunksH, minRx, minRz, regionsW, regionsH;

    private TerritoryIndex(int minX, int minZ, int width, int height, byte[] grid, List<TerritoryFile.Province> provinces,
                           String[] countryIds, String[] countryNames, int[] countryRgb, short[] countryOfProvince,
                           List<TerritoryFile.Country> countryData, List<TerritoryFile.Locality> localities) {
        this.countryData = countryData; this.localities = localities;
        Map<String, Integer> byId = new HashMap<>();
        for (int i = 0; i < countryIds.length; i++) byId.put(countryIds[i], i);
        this.countryByNameId = byId;
        this.minX = minX; this.minZ = minZ; this.width = width; this.height = height; this.grid = grid;
        this.provinces = provinces; this.countryIds = countryIds; this.countryNames = countryNames;
        this.countryRgb = countryRgb; this.countryOfProvince = countryOfProvince;
        provinceLabels = new String[provinces.size()];
        provinceBounds = new int[provinces.size()][];
        for (int i = 0; i < provinces.size(); i++) {
            TerritoryFile.Province p = provinces.get(i);
            provinceLabels[i] = label(p.id, p.country);
            provinceBounds[i] = bounds(p.rings);
        }
        if (width == 0) {
            minCx = minCz = chunksW = chunksH = minRx = minRz = regionsW = regionsH = 0;
            chunkHas = new boolean[0]; regionHas = new boolean[0]; regionCountries = new long[0];
            return;
        }
        minCx = minX >> 4; minCz = minZ >> 4;
        chunksW = ((minX + width - 1) >> 4) - minCx + 1;
        chunksH = ((minZ + height - 1) >> 4) - minCz + 1;
        chunkHas = new boolean[chunksW * chunksH];
        for (int z = 0; z < height; z++) {
            int cRow = ((minZ + z) >> 4) - minCz;
            for (int x = 0; x < width; x++) {
                if (grid[z * width + x] != 0) chunkHas[cRow * chunksW + (((minX + x) >> 4) - minCx)] = true;
            }
        }
        minRx = minCx >> 5; minRz = minCz >> 5;
        regionsW = ((minCx + chunksW - 1) >> 5) - minRx + 1;
        regionsH = ((minCz + chunksH - 1) >> 5) - minRz + 1;
        regionHas = new boolean[regionsW * regionsH];
        for (int cz = 0; cz < chunksH; cz++) for (int cx = 0; cx < chunksW; cx++) {
            if (chunkHas[cz * chunksW + cx]) regionHas[(((minCz + cz) >> 5) - minRz) * regionsW + (((minCx + cx) >> 5) - minRx)] = true;
        }
        regionCountries = new long[regionsW * regionsH * 4];
        for (int z = 0; z < height; z++) {
            int rRow = (((minZ + z) >> 9) - minRz) * regionsW;
            for (int x = 0; x < width; x++) {
                int v = (grid[z * width + x] & 255) - 1;
                if (v < 0) continue;
                regionCountries[(rRow + (((minX + x) >> 9) - minRx)) * 4 + (v >> 6)] |= 1L << (v & 63);
            }
        }
    }

    public static TerritoryIndex build(TerritoryFile file) {
        List<String> cIds = new ArrayList<>(file.countries.keySet());
        if (cIds.size() > 254) throw new IllegalArgumentException("too many countries for the block grid");
        Map<String, Integer> cIndex = new HashMap<>();
        String[] cNames = new String[cIds.size()];
        int[] cRgb = new int[cIds.size()];
        for (int i = 0; i < cIds.size(); i++) {
            TerritoryFile.Country c = file.countries.get(cIds.get(i));
            cIndex.put(cIds.get(i), i);
            cNames[i] = c.name != null ? c.name : cIds.get(i);
            cRgb[i] = parseColor(c.color);
        }
        List<TerritoryFile.Province> provinces = new ArrayList<>();
        for (TerritoryFile.Province p : file.provinces) if (cIndex.containsKey(p.country) && p.rings != null && !p.rings.isEmpty()) provinces.add(p);
        if (provinces.isEmpty()) return EMPTY;

        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (TerritoryFile.Province p : provinces) {
            int[] b = bounds(p.rings);
            minX = Math.min(minX, b[0]); minZ = Math.min(minZ, b[1]); maxX = Math.max(maxX, b[2]); maxZ = Math.max(maxZ, b[3]);
        }
        int width = maxX - minX + 1, height = maxZ - minZ + 1;
        byte[] grid = new byte[Math.multiplyExact(width, height)];
        short[] countryOf = new short[provinces.size()];
        for (int pi = 0; pi < provinces.size(); pi++) {
            TerritoryFile.Province p = provinces.get(pi);
            countryOf[pi] = (short) (int) cIndex.get(p.country);
            fill(p.rings, (byte) (countryOf[pi] + 1), grid, minX, minZ, width, height);
        }
        List<TerritoryFile.Country> cData = new ArrayList<>();
        for (String id : cIds) cData.add(file.countries.get(id));
        List<TerritoryFile.Locality> locs = new ArrayList<>();
        if (file.localities != null) for (TerritoryFile.Locality l : file.localities) if (l != null && l.name != null && l.country != null) locs.add(l);
        TerritoryIndex built = new TerritoryIndex(minX, minZ, width, height, grid, provinces, cIds.toArray(new String[0]), cNames, cRgb, countryOf,
                List.copyOf(cData), List.copyOf(locs));
        if (file.players != null) {
            Map<String, TerritoryFile.Player> byName = new HashMap<>();
            file.players.forEach((k, v) -> { if (k != null && v != null) byName.put(k.toLowerCase(), v); });
            built.players = byName;
            Map<String, String> ign = new HashMap<>();
            file.players.forEach((k, v) -> { if (k != null && v != null && v.ign != null && !v.ign.isBlank()) ign.put(v.ign.trim().toLowerCase(), k); });
            built.siteNameByIgn = ign;
        }
        if (file.war != null) built.war = file.war;
        return built;
    }

    private static int[] bounds(List<int[]> rings) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] r : rings) for (int i = 0; i + 1 < r.length; i += 2) {
            minX = Math.min(minX, r[i]); maxX = Math.max(maxX, r[i]);
            minZ = Math.min(minZ, r[i + 1]); maxZ = Math.max(maxZ, r[i + 1]);
        }
        return new int[]{minX, minZ, maxX, maxZ};
    }

    /** Even-odd scanline fill of all rings, testing each block by its centre. */
    private static void fill(List<int[]> rings, byte value, byte[] grid, int minX, int minZ, int width, int height) {
        int[] b = bounds(rings);
        double[] xs = new double[64];
        for (int z = b[1]; z <= b[3]; z++) {
            int row = z - minZ;
            if (row < 0 || row >= height) continue;
            double zc = z + 0.5;
            int n = 0;
            for (int[] r : rings) {
                int len = r.length / 2;
                for (int i = 0, j = len - 1; i < len; j = i++) {
                    double zi = r[2 * i + 1], zj = r[2 * j + 1];
                    if ((zi <= zc) == (zj <= zc)) continue;
                    double xi = r[2 * i], xj = r[2 * j];
                    if (n == xs.length) xs = Arrays.copyOf(xs, n * 2);
                    xs[n++] = xi + (zc - zi) * (xj - xi) / (zj - zi);
                }
            }
            Arrays.sort(xs, 0, n);
            for (int k = 0; k + 1 < n; k += 2) {
                int from = (int) Math.ceil(xs[k] - 0.5), to = (int) Math.ceil(xs[k + 1] - 0.5) - 1;   // block centres inside [xa, xb)
                for (int x = Math.max(from, minX); x <= Math.min(to, minX + width - 1); x++) grid[row * width + (x - minX)] = value;
            }
        }
    }

    /** "SynorWestern64Province" in country "Synor" -> "Western 64". */
    static String label(String id, String country) {
        String s = id;
        if (s.startsWith(country) && s.length() > country.length()) s = s.substring(country.length());
        if (s.endsWith("Province") && s.length() > "Province".length()) s = s.substring(0, s.length() - "Province".length());
        return s.replaceAll("(?<=[a-z])(?=[A-Z0-9])|(?<=[0-9])(?=[A-Za-z])|(?<=[A-Z])(?=[A-Z][a-z])", " ").trim();
    }

    private static int parseColor(String hex) {
        try { return Integer.parseInt(hex.substring(1), 16) & 0xFFFFFF; } catch (RuntimeException e) { return 0x888888; }
    }

    // ---- queries ----

    /** Country index of a block, or -1. */
    public int countryAtBlock(int x, int z) {
        int gx = x - minX, gz = z - minZ;
        if (gx < 0 || gz < 0 || gx >= width || gz >= height) return -1;
        return (grid[gz * width + gx] & 255) - 1;
    }

    /** Topmost province containing the block (exact polygon test, for tooltips), or -1. */
    /** The bounding box {minX, minZ, maxX, maxZ} of a province. */
    public int[] provinceBoundsOf(int province) { return provinceBounds[province]; }

    public int provinceAtBlock(int x, int z) {
        if (countryAtBlock(x, z) < 0) return -1;
        double px = x + 0.5, pz = z + 0.5;
        for (int i = provinces.size() - 1; i >= 0; i--) {
            int[] b = provinceBounds[i];
            if (x < b[0] || x > b[2] || z < b[1] || z > b[3]) continue;
            boolean inside = false;
            for (int[] r : provinces.get(i).rings) {
                int len = r.length / 2;
                for (int a = 0, c = len - 1; a < len; c = a++) {
                    double za = r[2 * a + 1], zc = r[2 * c + 1];
                    if ((za > pz) != (zc > pz) && px < (r[2 * c] - r[2 * a]) * (pz - za) / (zc - za) + r[2 * a]) inside = !inside;
                }
            }
            if (inside) return i;
        }
        return -1;
    }

    /** Whether any block of the chunk is claimed. */
    public boolean chunkHasTerritory(int cx, int cz) {
        int x = cx - minCx, z = cz - minCz;
        return x >= 0 && z >= 0 && x < chunksW && z < chunksH && chunkHas[z * chunksW + x];
    }

    /**
     * The regions (as {regionX, regionZ}) a recolouring touches: those holding blocks of a changed country, plus
     * those overlapping the given block boxes (single provinces). Lets the map redraw only what changed.
     */
    public List<int[]> regionsTouching(boolean[] changedCountries, List<int[]> blockBoxes) {
        List<int[]> out = new ArrayList<>();
        for (int rz = 0; rz < regionsH; rz++) for (int rx = 0; rx < regionsW; rx++) {
            int base = (rz * regionsW + rx) * 4;
            boolean hit = false;
            for (int c = 0; c < changedCountries.length && !hit; c++) {
                if (changedCountries[c] && c < 256 && (regionCountries[base + (c >> 6)] & (1L << (c & 63))) != 0) hit = true;
            }
            int wx = (minRx + rx) << 9, wz = (minRz + rz) << 9;
            for (int i = 0; i < blockBoxes.size() && !hit; i++) {
                int[] b = blockBoxes.get(i);
                if (b[2] >= wx && b[0] < wx + 512 && b[3] >= wz && b[1] < wz + 512) hit = true;
            }
            if (hit) out.add(new int[]{minRx + rx, minRz + rz});
        }
        return out;
    }

    /** Whether any chunk in the 32x32 region (region coordinates) is claimed. */
    public boolean regionHasTerritory(int rx, int rz) {
        int x = rx - minRx, z = rz - minRz;
        return x >= 0 && z >= 0 && x < regionsW && z < regionsH && regionHas[z * regionsW + x];
    }

    /** Country index for a country id such as "Greneshire", or -1. */
    public int countryIndex(String id) { Integer i = countryByNameId.get(id); return i == null ? -1 : i; }
    /** Wars, alliances and timers (empty lists when the file has none). */
    public TerritoryFile.WarData war() { return war; }

    /** The site's record of this person, or null (the file may not carry players at all). */
    public TerritoryFile.Player player(String name) { return name == null ? null : players.get(name.toLowerCase()); }

    /** The site's name for the person who plays under this in-game name (or the name itself when the site doesn't know it). */
    public String siteNameForIgn(String ign) {
        if (ign == null) return "";
        String site = siteNameByIgn.get(ign.trim().toLowerCase());
        return site != null ? site : ign;
    }

    public TerritoryFile.Country countryData(int country) { return countryData.get(country); }
    public List<TerritoryFile.Locality> localities() { return localities; }

    /** The localities this player is lord of (a shared lordship lists its lords separated by commas). */
    public List<TerritoryFile.Locality> localitiesOfLord(String name) {
        List<TerritoryFile.Locality> out = new ArrayList<>();
        if (name == null) return out;
        for (TerritoryFile.Locality l : localities) {
            if (l.lord == null) continue;
            for (String lord : l.lord.split(",")) if (lord.trim().equalsIgnoreCase(name.trim())) { out.add(l); break; }
        }
        return out;
    }
    public int countryRgb(int country) { return countryRgb[country]; }
    public String countryName(int country) { return countryNames[country]; }
    public String countryId(int country) { return countryIds[country]; }
    public String provinceLabel(int province) { return provinceLabels[province]; }
    public int countryOfProvince(int province) { return countryOfProvince[province]; }
    /** {minX, minZ, width, height} of the block grid. */
    public int[] blockBounds() { return new int[]{minX, minZ, width, height}; }
    public boolean isEmpty() { return width == 0; }
    public int provinceCount() { return provinceLabels.length; }
    public int countryCount() { return countryIds.length; }
}
