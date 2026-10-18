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
    public record Paint(int[] countryRgb, Map<Integer, Integer> provinceRgb, Map<Integer, int[]> provinceBounds) {}

    public static final int NEUTRAL = 0x6E6E6E, CONTESTED = 0xE0C040, UNCONTROLLED = 0x9A9A9A;
    private static final int[] SIDE_COLORS = {0xD83A3A, 0x3A6FD8, 0x3AAE5A, 0xE59A2E, 0x8E5AC8, 0x2AAFAF};

    private static volatile Paint current;
    public static Paint current() { return current; }
    public static void set(Paint paint) { current = paint; }

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
        if (war.captured != null) for (TerritoryFile.Captured c : war.captured) {
            int province = index.provinceAtBlock(c.x, c.z);
            if (province < 0) continue;
            int color = "contested".equals(c.state) ? CONTESTED : "uncontrolled".equals(c.state) ? UNCONTROLLED
                    : sideOf.getOrDefault(c.holderKey, NEUTRAL);
            provinces.put(province, color);
            bounds.put(province, index.provinceBoundsOf(province));
        }
        return new Paint(rgb, provinces, bounds);
    }

    public static Paint forAlliance(TerritoryIndex index, TerritoryFile.AllianceInfo alliance) {
        int[] rgb = blank(index);
        Integer own = parse(alliance.color);
        int color = own != null ? own : SIDE_COLORS[1];
        if (alliance.members != null) for (TerritoryFile.Member m : alliance.members) {
            if (m.countries == null) continue;
            for (String c : m.countries) { int ci = index.countryIndex(c); if (ci >= 0) rgb[ci] = color; }
        }
        return new Paint(rgb, Map.of(), Map.of());
    }

    private static int[] blank(TerritoryIndex index) {
        int[] rgb = new int[index.countryCount()];
        java.util.Arrays.fill(rgb, -1);
        return rgb;
    }
}
