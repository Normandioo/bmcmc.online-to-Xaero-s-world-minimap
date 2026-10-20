package com.cumania.territories.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the map marks on top of a war, worked out once a second:
 *   SWORD  on a city with online players of both sides inside its territory (its capture limit, else its province)
 *   FLAG   on a city that was just taken, in the colour of whoever took it (FLAG_MS after the change was seen)
 * The first reading of a war only sets the starting point, so nothing already taken is flagged when you join. No Minecraft classes in here.
 */
public final class WarMarkers {
    private WarMarkers() {}

    public enum Kind { SWORD, FLAG }

    public record Marker(Kind kind, double x, double z, int rgb, int rgb2) {}

    public static final long FLAG_MS = 10 * 60_000L;

    private static volatile List<Marker> current = List.of();
    public static List<Marker> current() { return current; }
    public static void clear() { current = List.of(); }

    private record Flag(double x, double z, int rgb, long at) {}
    private static final Map<String, Map<String, Integer>> lastHolder = new HashMap<>();
    private static final Map<String, Map<String, Flag>> flags = new HashMap<>();

    public static boolean running(TerritoryFile.WarInfo war) {
        if (war.ended == null) return true;
        try { return Instant.parse(war.ended).isAfter(Instant.now()); } catch (RuntimeException e) { return false; }
    }

    /** The war the effects follow: the one being viewed, else the first one running (a live one first). */
    public static TerritoryFile.WarInfo followed(TerritoryIndex index, String viewedId) {
        var wars = index.war().wars;
        if (wars == null) return null;
        if (viewedId != null) for (var w : wars) if (w != null && viewedId.equals(w.id) && w.sides != null) return w;   // the war on screen comes first
        TerritoryFile.WarInfo first = null;
        for (var w : wars) {
            if (w == null || w.id == null || w.sides == null || !running(w)) continue;
            if (w.live) return w;
            if (first == null) first = w;
        }
        return first;
    }

    public static void update(TerritoryIndex index, TerritoryFile.WarInfo war, List<DynmapClient.Online> players, String world, long now) {
        WarScore.Result score = WarScore.of(index, war);
        WarFront front = WarFront.of(index, war);
        List<Marker> out = new ArrayList<>();

        // flags: a city whose holder changed since the last reading
        Map<String, Integer> last = lastHolder.get(war.id);
        Map<String, Integer> now_ = new HashMap<>();
        Map<String, Flag> mine = flags.computeIfAbsent(war.id, k -> new HashMap<>());
        for (WarScore.Holding h : score.holdings()) {
            String k = WarScore.key(h.locality().name, h.locality().country);
            int code = "contested".equals(h.state()) ? -2 : "uncontrolled".equals(h.state()) ? -3 : h.holder();
            now_.put(k, code);
            if (last == null) continue;
            Integer before = last.get(k);
            if (before == null || before == code) continue;
            if (code >= 0 && code < war.sides.size()) mine.put(k, new Flag(h.locality().x, h.locality().z, WarPaint.sideColor(war.sides.get(code), code), now));
            else mine.remove(k);
        }
        lastHolder.put(war.id, now_);
        mine.values().removeIf(f -> now - f.at() > FLAG_MS);
        for (Flag f : mine.values()) out.add(new Marker(Kind.FLAG, f.x(), f.z(), f.rgb(), 0));

        // swords: players of two different sides inside the same city's territory
        Map<String, Set<Integer>> sidesIn = new HashMap<>();
        Map<String, TerritoryFile.Locality> city = new HashMap<>();
        for (DynmapClient.Online p : players) {
            if (p.world() == null || !p.world().equalsIgnoreCase(world)) continue;
            int nation = index.nationOfPlayer(p.siteName());
            if (nation < 0) continue;
            int side = WarScore.sideOfCountry(war, index.countryId(nation));
            if (side < 0) continue;
            int province = index.provinceAtBlock((int) Math.floor(p.x()), (int) Math.floor(p.z()));
            for (TerritoryFile.Locality l : index.localities()) {
                var u = front.unitOf(l);
                if (u == null || u.neutral()) continue;
                if (!inside(index, l, province, p.x(), p.z())) continue;
                String k = WarScore.key(l.name, l.country);
                sidesIn.computeIfAbsent(k, x -> new HashSet<>()).add(side);
                city.put(k, l);
            }
        }
        for (var e : sidesIn.entrySet()) if (e.getValue().size() >= 2) out.add(new Marker(Kind.SWORD, city.get(e.getKey()).x, city.get(e.getKey()).z, 0, 0));
        current = List.copyOf(out);
    }

    /** Inside the city's capture limit when the site draws one for it, else in the city's own province. */
    private static boolean inside(TerritoryIndex index, TerritoryFile.Locality l, int province, double x, double z) {
        String key = WarScore.key(l.name, l.country);
        boolean any = false;
        for (TerritoryFile.Zone zone : index.zoneShapes()) {
            if (zone == null || zone.name == null || zone.country == null || !WarScore.key(zone.name, zone.country).equals(key)) continue;
            any = true;
            if (contains(zone, x, z)) return true;
        }
        if (any) return false;
        return province >= 0 && province == index.provinceAtBlock(l.x, l.z);
    }

    /** Even-odd over every ring of the zone, as the site's SVG fills it. */
    static boolean contains(TerritoryFile.Zone zone, double x, double z) {
        if (zone.rings == null) return false;
        boolean in = false;
        for (double[] ring : zone.rings) {
            if (ring == null || ring.length < 6) continue;
            int n = ring.length / 2;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = ring[2 * i], zi = ring[2 * i + 1], xj = ring[2 * j], zj = ring[2 * j + 1];
                if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
            }
        }
        return in;
    }
}
