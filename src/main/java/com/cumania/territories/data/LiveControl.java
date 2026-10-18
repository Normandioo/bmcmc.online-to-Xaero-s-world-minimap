package com.cumania.territories.data;

import com.cumania.territories.TerritoryManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who holds the cities of a war marked "live" on the site, read from dynmap's markers the way the site does it (js/warlive.js):
 * each city is matched to a marker by its id "Name(Country)" (or the map's own "marker__Name-lp-Country-rp-__Type"), by its name, or by the
 * nearest marker within a few blocks; the marker's icon says who holds it: ...Red the war's first side, ...Blue the second, ...Green the third,
 * ...Contested, ...Uncontrolled. An icon with none of those changes nothing. What dynmap says goes on top of the war's own "captured" list.
 */
public final class LiveControl {
    private LiveControl() {}

    private static final Pattern MAP_ID = Pattern.compile("^marker__(.+?)-lp-(.+?)-rp-_*([A-Za-z]+)$");
    private static final Pattern ICON = Pattern.compile("(Red|Blue|Green|Contested|Uncontrolled)$");
    private static final int SNAP_BLOCKS = 8;

    private static final Map<String, List<TerritoryFile.Captured>> cache = new HashMap<>();
    private static long cachedMarkersAt = -1;
    private static TerritoryIndex cachedIndex;

    /** Whether this war's control is being read live right now. */
    public static boolean isLive(TerritoryFile.WarInfo war) {
        return war != null && war.live && war.ended == null && TerritoryManager.config().liveWar && DynmapClient.markersKnown();
    }

    /** The war's captured cities: its own list, with dynmap's reading on top when the war is live. */
    public static synchronized List<TerritoryFile.Captured> captured(TerritoryIndex index, TerritoryFile.WarInfo war) {
        List<TerritoryFile.Captured> own = war.captured == null ? List.of() : war.captured;
        if (!isLive(war)) return own;
        if (cachedIndex != index || cachedMarkersAt != DynmapClient.markersAt()) { cache.clear(); cachedIndex = index; cachedMarkersAt = DynmapClient.markersAt(); }
        return cache.computeIfAbsent(war.id, id -> merge(index, war, own, DynmapClient.markers()));
    }

    static List<TerritoryFile.Captured> merge(TerritoryIndex index, TerritoryFile.WarInfo war, List<TerritoryFile.Captured> own, List<DynmapClient.Marker> markers) {
        Map<String, TerritoryFile.Captured> byKey = new HashMap<>();
        List<TerritoryFile.Captured> out = new ArrayList<>();
        for (TerritoryFile.Captured c : own) { byKey.put(key(c.name, c.country), c); out.add(c); }
        for (TerritoryFile.Locality l : index.localities()) {
            DynmapClient.Marker m = markerFor(l, markers);
            if (m == null) continue;
            Matcher icon = ICON.matcher(m.icon());
            if (!icon.find()) continue;
            TerritoryFile.Captured c = new TerritoryFile.Captured();
            c.name = l.name; c.country = l.country; c.x = l.x; c.z = l.z;
            switch (icon.group(1)) {
                case "Contested" -> c.state = "contested";
                case "Uncontrolled" -> c.state = "uncontrolled";
                default -> {
                    c.state = "held";
                    int side = switch (icon.group(1)) { case "Red" -> 0; case "Blue" -> 1; default -> 2; };
                    if (war.sides == null || side >= war.sides.size()) continue;   // an icon for a side this war doesn't have
                    c.liveSide = side;
                    c.holder = war.sides.get(side).name;
                }
            }
            TerritoryFile.Captured old = byKey.get(key(l.name, l.country));
            if (old != null) out.set(out.indexOf(old), c); else out.add(c);   // dynmap wins over the list
            byKey.put(key(l.name, l.country), c);
        }
        return List.copyOf(out);
    }

    private static String key(String name, String country) { return (name + "(" + country + ")").toLowerCase(); }

    private static DynmapClient.Marker markerFor(TerritoryFile.Locality l, List<DynmapClient.Marker> markers) {
        String id = key(l.name, l.country);
        for (DynmapClient.Marker m : markers) {                                  // by id
            if (m.id().toLowerCase().equals(id)) return m;
            Matcher mid = MAP_ID.matcher(m.id());
            if (mid.matches() && key(mid.group(1), mid.group(2)).equals(id)) return m;
        }
        for (DynmapClient.Marker m : markers) if (m.label().equalsIgnoreCase(l.name) || m.id().equalsIgnoreCase(l.name)) return m;   // by name
        DynmapClient.Marker best = null;
        double bestD = SNAP_BLOCKS * SNAP_BLOCKS + 1;
        for (DynmapClient.Marker m : markers) {                                  // the nearest marker within a few blocks
            double d = (m.x() - l.x) * (m.x() - l.x) + (m.z() - l.z) * (m.z() - l.z);
            if (d < bestD) { bestD = d; best = m; }
        }
        return best;
    }
}
