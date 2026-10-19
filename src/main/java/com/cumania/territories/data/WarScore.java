package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Who holds what in a war, and what it is worth. Each city has a value, worked out the way the site does it for settlements
 * (Law 7.9: 150 points are shared among the tiers Small / Medium / Large a realm has, then split evenly between the settlements of each tier;
 * a realm with no settlements has its capital worth 150). The mod only has the cities and their country, not the realm map the site
 * uses, so a country's cities stand for its realm. The war's bar is each side's share of the points it holds right now:
 * its own cities that were not taken, plus what it has taken off the others. No Minecraft classes in here.
 */
public final class WarScore {
    private WarScore() {}

    public static final double REALM_VALUE = 150;

    /**
     * One city in a war. owner = the side its country belongs to (-1 when it belongs to nobody in the war), holder = the side that
     * holds it now (-1 when contested, uncontrolled or held by someone outside the war). state: own / held / contested / uncontrolled.
     */
    public record Holding(TerritoryFile.Locality locality, int owner, int holder, String state, double value) {
        public boolean disputed() { return "contested".equals(state) || "uncontrolled".equals(state); }
        public boolean taken() { return holder >= 0 && holder != owner; }
    }

    public record Result(int sides, double[] points, double[] startPoints, List<Holding> holdings) {
        public double total() { double t = 0; for (double p : points) t += p; return t; }
        /** The fraction (0-1) of the points held by all sides that this side holds; an even split when nothing is worth anything. */
        public double share(int side) { double t = total(); return t <= 0 ? 1.0 / Math.max(1, sides) : points[side] / t; }
        public int leader() { int best = 0; for (int i = 1; i < sides; i++) if (points[i] > points[best]) best = i; return best; }
        /** True when no side is ahead by at least a percentage point. */
        public boolean even() {
            if (sides < 2) return true;
            double top = 0, second = 0;
            for (int i = 0; i < sides; i++) { double s = share(i); if (s > top) { second = top; top = s; } else if (s > second) second = s; }
            return top - second < 0.01;
        }
    }

    // ---- values ----

    private static TerritoryIndex valuesFor;
    private static Map<String, Double> values = Map.of();

    static String key(String name, String country) { return (name + "(" + country + ")").toLowerCase(); }

    /** Small, Medium or Large for a settlement (a "...Capital" kind counts as its tier); null for anything else. */
    static String tier(String kind) {
        if (kind == null) return null;
        String base = kind.endsWith("Capital") ? kind.substring(0, kind.length() - "Capital".length()) : kind;
        return base.equals("Small") || base.equals("Medium") || base.equals("Large") ? base : null;
    }

    static boolean isCapital(String kind) { return kind != null && kind.endsWith("Capital"); }

    /** What every city is worth, by "name(country)" in lower case. Cities worth nothing are left out. */
    public static synchronized Map<String, Double> values(TerritoryIndex index) {
        if (valuesFor == index) return values;
        Map<String, List<TerritoryFile.Locality>> byCountry = new LinkedHashMap<>();
        for (TerritoryFile.Locality l : index.localities()) byCountry.computeIfAbsent(l.country == null ? "" : l.country.toLowerCase(), k -> new ArrayList<>()).add(l);
        Map<String, Double> out = new HashMap<>();
        for (List<TerritoryFile.Locality> list : byCountry.values()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (TerritoryFile.Locality l : list) { String t = tier(l.kind); if (t != null) counts.merge(t, 1, Integer::sum); }
            if (counts.isEmpty()) {
                for (TerritoryFile.Locality l : list) if (isCapital(l.kind)) { out.put(key(l.name, l.country), REALM_VALUE); break; }
                continue;
            }
            double share = REALM_VALUE / counts.size();
            for (TerritoryFile.Locality l : list) { String t = tier(l.kind); if (t != null) out.put(key(l.name, l.country), share / counts.get(t)); }
        }
        valuesFor = index;
        values = out;
        return out;
    }

    public static double value(TerritoryIndex index, TerritoryFile.Locality l) { return values(index).getOrDefault(key(l.name, l.country), 0.0); }

    // ---- sides ----

    public static int sideOfCountry(TerritoryFile.WarInfo war, String country) {
        if (country == null || war.sides == null) return -1;
        for (int i = 0; i < war.sides.size(); i++) {
            var members = war.sides.get(i).members;
            if (members == null) continue;
            for (var m : members) {
                if (m.name != null && m.name.equalsIgnoreCase(country)) return i;   // a realm named like the member ("Eleyetia" for USE and Mesea)
                if (m.countries != null) for (String c : m.countries) if (c.equalsIgnoreCase(country)) return i;
            }
        }
        return -1;
    }

    /** The side that took this city: the one live control saw, or the one its capturing country (or its name) belongs to. */
    static int captor(TerritoryFile.WarInfo war, TerritoryFile.Captured c) {
        int n = war.sides == null ? 0 : war.sides.size();
        if (c.liveSide != null) return c.liveSide < n ? c.liveSide : -1;
        int side = sideOfCountry(war, c.holderKey);
        if (side >= 0 || c.holder == null) return side;
        for (int i = 0; i < n; i++) {
            var s = war.sides.get(i);
            if (s.name != null && s.name.equalsIgnoreCase(c.holder)) return i;
            if (s.members != null) for (var m : s.members) if (m.name != null && m.name.equalsIgnoreCase(c.holder)) return i;
        }
        return -1;
    }

    // ---- who holds what ----

    private static TerritoryIndex cachedIndex;
    private static TerritoryFile.WarInfo cachedWar;
    private static List<TerritoryFile.Captured> cachedCaptured;
    private static Result cached;

    /** The war's standing right now (what live control says included). Cached until the data or the captured list changes. */
    public static synchronized Result of(TerritoryIndex index, TerritoryFile.WarInfo war) {
        List<TerritoryFile.Captured> captured = LiveControl.captured(index, war);
        if (cachedIndex == index && cachedWar == war && cachedCaptured == captured && cached != null) return cached;
        cached = compute(index, war, captured);
        cachedIndex = index; cachedWar = war; cachedCaptured = captured;
        return cached;
    }

    public static Result compute(TerritoryIndex index, TerritoryFile.WarInfo war, List<TerritoryFile.Captured> captured) {
        int n = war.sides == null ? 0 : war.sides.size();
        Map<String, TerritoryFile.Captured> byKey = new HashMap<>();
        if (captured != null) for (TerritoryFile.Captured c : captured) byKey.put(key(c.name, c.country), c);
        Map<String, Double> vals = values(index);
        double[] points = new double[n], start = new double[n];
        List<Holding> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TerritoryFile.Locality l : index.localities()) {
            String k = key(l.name, l.country);
            int owner = sideOfCountry(war, l.country);
            TerritoryFile.Captured c = byKey.get(k);
            if (owner < 0 && c == null) continue;
            seen.add(k);
            out.add(holding(war, l, owner, c, vals.getOrDefault(k, 0.0), points, start));
        }
        // captured cities the list names but the localities don't have: shown, worth nothing
        for (Map.Entry<String, TerritoryFile.Captured> e : byKey.entrySet()) {
            if (seen.contains(e.getKey())) continue;
            TerritoryFile.Captured c = e.getValue();
            TerritoryFile.Locality l = new TerritoryFile.Locality();
            l.name = c.name; l.country = c.country; l.x = c.x; l.z = c.z;
            out.add(holding(war, l, sideOfCountry(war, c.country), c, 0, points, start));
        }
        return new Result(n, points, start, List.copyOf(out));
    }

    private static Holding holding(TerritoryFile.WarInfo war, TerritoryFile.Locality l, int owner, TerritoryFile.Captured c, double value, double[] points, double[] start) {
        int holder;
        String state;
        if (c == null) { holder = owner; state = "own"; }
        else if ("contested".equals(c.state)) { holder = -1; state = "contested"; }
        else if ("uncontrolled".equals(c.state)) { holder = -1; state = "uncontrolled"; }
        else { holder = captor(war, c); state = "held"; }
        if (owner >= 0) start[owner] += value;
        if (holder >= 0) points[holder] += value;
        return new Holding(l, owner, holder, state, value);
    }
}
