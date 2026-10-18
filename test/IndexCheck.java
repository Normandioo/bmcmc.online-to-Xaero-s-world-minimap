import com.cumania.territories.data.*;
import java.nio.file.*;
import java.util.*;

/**
 * Checks the block index against the locality markers in the SVG, whose ids name their country, and against
 * an independent exact point-in-polygon test of every marker and of random blocks.
 *   java -cp <classes>:<gson> IndexCheck territories.json markers.csv
 */
public class IndexCheck {
    public static void main(String[] a) throws Exception {
        TerritoryFile f = TerritoryFile.parse(Files.readString(Path.of(a[0])));
        long t0 = System.nanoTime();
        TerritoryIndex idx = TerritoryIndex.build(f);
        System.out.printf("built %d provinces / %d countries in %d ms%n", idx.provinceCount(), idx.countryCount(), (System.nanoTime() - t0) / 1_000_000);
        Map<String, Integer> ids = new HashMap<>();
        for (int i = 0; i < idx.countryCount(); i++) ids.put(idx.countryId(i), i);

        int named = 0, matchName = 0, matchExact = 0, mismatch = 0;
        for (String line : Files.readAllLines(Path.of(a[1]))) {
            String[] p = line.split(",", 4);
            double x = Double.parseDouble(p[0]), z = Double.parseDouble(p[1]);
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int got = idx.countryAtBlock(bx, bz);
            int exact = exactCountry(f, bx + 0.5, bz + 0.5, idx, ids);
            if (got != exact) mismatch++;
            Integer want = ids.get(p[2]);
            if (want == null) continue;
            named++;
            if (got == want) matchName++;
            if (exact == want) matchExact++;
        }
        System.out.printf("markers naming a country: %d | block index agrees with the name: %d | exact polygon test agrees: %d | index vs exact differ on %d%n",
                named, matchName, matchExact, mismatch);

        Random rnd = new Random(7);
        int diff = 0, claimed = 0, N = 4000;
        for (int i = 0; i < N; i++) {
            int bx = rnd.nextInt(5000) - 2500, bz = rnd.nextInt(5000) - 2500;
            int got = idx.countryAtBlock(bx, bz), exact = exactCountry(f, bx + 0.5, bz + 0.5, idx, ids);
            if (exact >= 0) claimed++;
            if (got != exact) diff++;
        }
        System.out.printf("random blocks: %d, claimed %d, index differs from exact test on %d%n", N, claimed, diff);

        // provinceAtBlock must name a province of the same country
        int provBad = 0, provChecked = 0;
        for (int i = 0; i < 4000; i++) {
            int bx = rnd.nextInt(5000) - 2500, bz = rnd.nextInt(5000) - 2500;
            int c = idx.countryAtBlock(bx, bz);
            if (c < 0) continue;
            provChecked++;
            int pr = idx.provinceAtBlock(bx, bz);
            if (pr < 0 || idx.countryOfProvince(pr) != c) provBad++;
        }
        System.out.printf("province lookups: %d checked, %d inconsistent%n", provChecked, provBad);

        // chunk fill + border for a chunk that straddles a border
        int[] out = new int[256];
        int borderChunks = 0, okChunks = 0;
        for (int cx = -160; cx < 160; cx++) for (int cz = -160; cz < 160; cz++) {
            boolean any = TerritoryColors.compute(idx, cx, cz, 90, 230, out);
            if (any != idx.chunkHasTerritory(cx, cz)) { System.out.println("chunk flag mismatch " + cx + "," + cz); return; }
            if (any) okChunks++;
        }
        System.out.println("chunks with territory: " + okChunks + " | region(0,0): " + idx.regionHasTerritory(0, 0));
        System.out.println("label: " + idx.provinceLabel(0));
        Runtime rt = Runtime.getRuntime(); System.gc();
        System.out.printf("heap in use: %d MB%n", (rt.totalMemory() - rt.freeMemory()) >> 20);
    }

    static int exactCountry(TerritoryFile f, double px, double pz, TerritoryIndex idx, Map<String, Integer> ids) {
        int owner = -1;
        for (TerritoryFile.Province p : f.provinces) {
            boolean in = false;
            for (int[] r : p.rings) {
                int n = r.length / 2;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    double zi = r[2 * i + 1], zj = r[2 * j + 1];
                    if ((zi > pz) != (zj > pz) && px < (r[2 * j] - r[2 * i]) * (pz - zi) / (zj - zi) + r[2 * i]) in = !in;
                }
            }
            if (in) owner = ids.get(p.country);
        }
        return owner;
    }
}
