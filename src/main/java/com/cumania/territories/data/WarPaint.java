package com.cumania.territories.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recolours the map for the war menu: the countries of a war's sides (or of an alliance) in their colours, everything else
 * greyed, captured provinces in their captor's colour. null = the normal territory colours.
 */
public final class WarPaint {
    private WarPaint() {}

    /** countryRgb[country] is RGB or -1 (greyed); provinceRgb overrides single provinces. */
    /** zoneRgb: the colour of each capture limit (by "name(country)" in lower case) that a war's view paints by who holds the city. */
    public record Paint(int[] countryRgb, Map<Integer, Integer> provinceRgb, Map<Integer, int[]> provinceBounds, Map<String, Integer> zoneRgb, FrontLines front, FrontLines inner) {
        public Paint(int[] countryRgb, Map<Integer, Integer> provinceRgb, Map<Integer, int[]> provinceBounds, Map<String, Integer> zoneRgb) {
            this(countryRgb, provinceRgb, provinceBounds, zoneRgb, FrontLines.EMPTY, FrontLines.EMPTY);
        }
    }

    public static final int INNER = 0xC8C8C8;
    public static final int NEUTRAL = 0x6E6E6E, CONTESTED = 0xE0C040, UNCONTROLLED = 0x9A9A9A;
    private static final int[] SIDE_COLORS = {0xD83A3A, 0x3A6FD8, 0x3AAE5A, 0xE59A2E, 0x8E5AC8, 0x2AAFAF};

    private static volatile Paint current;
    public static Paint current() { return current; }
    public static void set(Paint paint) { current = paint; }

    /**
     * Switches to another paint and tells the map to redraw only the regions whose colours actually change
     * (switching from one war to another leaves most of the map as it was), which is much faster than redrawing everything.
     */
    public static void apply(TerritoryIndex index, Paint next) {
        Paint old = current;
        current = next;
        boolean[] changed = new boolean[index.countryCount()];
        for (int c = 0; c < changed.length; c++) changed[c] = effective(old, c) != effective(next, c);
        java.util.List<int[]> boxes = new java.util.ArrayList<>();
        addProvinceBoxes(old, next, boxes);
        addProvinceBoxes(next, old, boxes);
        if (com.cumania.territories.TerritoryManager.config().showCaptureLimits) boxes.addAll(index.limits().boxesOf(changedZones(old, next)));
        FrontLines fa = old == null ? FrontLines.EMPTY : old.front(), fb = next == null ? FrontLines.EMPTY : next.front();
        if (!fa.sameAs(fb)) { boxes.addAll(fa.boxes()); boxes.addAll(fb.boxes()); }
        FrontLines ia = old == null ? FrontLines.EMPTY : old.inner(), ib = next == null ? FrontLines.EMPTY : next.inner();
        if (!ia.sameAs(ib)) { boxes.addAll(ia.boxes()); boxes.addAll(ib.boxes()); }
        com.cumania.territories.TerritoryManager.repaintRegions(index.regionsTouching(changed, boxes));
    }

    /** -2 = normal colours, -1 = greyed, otherwise the painted RGB. */
    private static int effective(Paint p, int country) {
        if (p == null) return -2;
        int rgb = country < p.countryRgb().length ? p.countryRgb()[country] : -1;
        return rgb < 0 && !com.cumania.territories.TerritoryManager.config().warGreyOthers ? -2 : rgb;
    }

    /** Capture limits whose colour differs between two paints (a limit has its colour in one and none in the other counts too). */
    private static java.util.Set<String> changedZones(Paint a, Paint b) {
        java.util.Set<String> out = new java.util.HashSet<>();
        Map<String, Integer> za = a == null ? Map.of() : a.zoneRgb(), zb = b == null ? Map.of() : b.zoneRgb();
        for (Map.Entry<String, Integer> e : za.entrySet()) if (!e.getValue().equals(zb.get(e.getKey()))) out.add(e.getKey());
        for (Map.Entry<String, Integer> e : zb.entrySet()) if (!e.getValue().equals(za.get(e.getKey()))) out.add(e.getKey());
        return out;
    }

    /** Provinces painted in `a` whose colour differs in `b`. */
    private static void addProvinceBoxes(Paint a, Paint b, java.util.List<int[]> out) {
        if (a == null) return;
        for (Map.Entry<Integer, Integer> e : a.provinceRgb().entrySet()) {
            if (b != null && e.getValue().equals(b.provinceRgb().get(e.getKey()))) continue;
            out.add(a.provinceBounds().get(e.getKey()));
        }
    }

    public static int sideColor(TerritoryFile.Side side, int index) {
        Integer own = parse(side.color);
        return own != null ? own : SIDE_COLORS[index % SIDE_COLORS.length];
    }

    public static Integer parse(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        try { return h.length() == 6 ? Integer.parseInt(h, 16) : null; } catch (NumberFormatException e) { return null; }
    }

    public static Paint forWar(TerritoryIndex index, TerritoryFile.WarInfo war) {
        int[] rgb = blank(index);
        Map<String, Integer> sideOf = new HashMap<>();
        List<TerritoryFile.Side> sides = war.sides == null ? List.of() : war.sides;
        for (int i = 0; i < sides.size(); i++) {
            int color = sideColor(sides.get(i), i);
            if (sides.get(i).members == null) continue;
            for (TerritoryFile.Member m : sides.get(i).members) {
                if (m.countries == null) continue;
                for (String c : m.countries) { int ci = index.countryIndex(c); if (ci >= 0) rgb[ci] = color; sideOf.put(c, color); }
            }
        }
        Map<Integer, Integer> provinces = new HashMap<>();
        Map<Integer, int[]> bounds = new HashMap<>();
        for (TerritoryFile.Captured c : LiveControl.captured(index, war)) {
            int province = index.provinceAtBlock(c.x, c.z);
            if (province < 0) continue;
            int color = "contested".equals(c.state) ? CONTESTED : "uncontrolled".equals(c.state) ? UNCONTROLLED
                    : c.liveSide != null && c.liveSide < sides.size() ? sideColor(sides.get(c.liveSide), c.liveSide) : sideOf.getOrDefault(c.holderKey, NEUTRAL);
            provinces.put(province, color);
            bounds.put(province, index.provinceBoundsOf(province));
        }
        // the capture limits take the colour of whoever holds their city (the cities of every side, as live control paints them)
        Map<String, Integer> zones = new HashMap<>();
        if (!index.limits().isEmpty()) {
            for (WarScore.Holding h : WarScore.of(index, war).holdings()) {
                Integer color = "contested".equals(h.state()) ? Integer.valueOf(CONTESTED) : "uncontrolled".equals(h.state()) ? Integer.valueOf(UNCONTROLLED)
                        : h.holder() >= 0 && h.holder() < sides.size() ? Integer.valueOf(sideColor(sides.get(h.holder()), h.holder())) : null;
                if (color != null) zones.put(WarScore.key(h.locality().name, h.locality().country), color);
            }
        }
        // the front: each Connection that puts a city In Play, in the colours of the two sides
        List<FrontLines.Seg> segs = new java.util.ArrayList<>();
        for (WarFront.Hot h : WarFront.of(index, war).hotLines()) {
            int ca = hotColor(sides, h.controlA()), cb = hotColor(sides, h.controlB());
            if (ca < 0 || cb < 0) continue;
            segs.add(new FrontLines.Seg(h.a().x, h.a().z, h.b().x, h.b().z, ca, cb));
        }
        // the quiet network inside each side: dotted grey
        List<FrontLines.Seg> quiet = new java.util.ArrayList<>();
        for (WarFront.Hot h : WarFront.of(index, war).innerLines()) quiet.add(new FrontLines.Seg(h.a().x, h.a().z, h.b().x, h.b().z, INNER, INNER));
        return new Paint(rgb, provinces, bounds, zones, FrontLines.build(segs), FrontLines.build(quiet, true));
    }

    static int hotColor(List<TerritoryFile.Side> sides, int control) {
        if (control == WarFront.CONTESTED) return CONTESTED;
        return control >= 0 && control < sides.size() ? sideColor(sides.get(control), control) : -1;
    }

    public static Paint forAlliance(TerritoryIndex index, TerritoryFile.AllianceInfo alliance) {
        int[] rgb = blank(index);
        Integer own = parse(alliance.color);
        int color = own != null ? own : SIDE_COLORS[1];
        if (alliance.members != null) for (TerritoryFile.Member m : alliance.members) {
            if (m.countries == null) continue;
            for (String c : m.countries) { int ci = index.countryIndex(c); if (ci >= 0) rgb[ci] = color; }
        }
        return new Paint(rgb, Map.of(), Map.of(), Map.of());
    }

    private static int[] blank(TerritoryIndex index) {
        int[] rgb = new int[index.countryCount()];
        java.util.Arrays.fill(rgb, -1);
        return rgb;
    }
}
