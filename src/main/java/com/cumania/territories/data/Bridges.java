package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges and Straits: where they stand and whether each is open. A closed one joins nothing, so no front line is drawn between the cities at its ends.
 * The state: the war's own "bridges" first (staff's word, as on the site), then what dynmap's marker says when the war is read live, then the map's drawing.
 */
public final class Bridges {
    private Bridges() {}

    private static final Pattern MAP_ID = Pattern.compile("^marker__(.+?)-lp-(.+?)-rp-_*([A-Za-z]+)$");
    private static final Pattern STATE = Pattern.compile("(?i)^(?:bridge|strait)(open|closed)");

    /** Every Bridge and Strait the file gives a position for, as markers: "Name(Country)" split, kind in {@code link}. */
    public static List<TerritoryFile.Locality> of(TerritoryFile.Network network) {
        List<TerritoryFile.Locality> out = new ArrayList<>();
        if (network == null || network.links == null) return out;
        for (var e : network.links.entrySet()) {
            var link = e.getValue();
            if (link == null || link.x == null || link.z == null) continue;
            Matcher m = Pattern.compile("(.+)\\((.+)\\)").matcher(e.getKey());
            if (!m.matches()) continue;
            var l = new TerritoryFile.Locality();
            l.name = m.group(1); l.country = m.group(2);
            l.link = "strait".equalsIgnoreCase(link.kind) ? "strait" : "bridge";
            l.kind = l.iconKind = l.link;
            l.development = "Hub";
            l.x = link.x; l.z = link.z;
            out.add(l);
        }
        return out;
    }

    /** Whether the Bridge or Strait {@code lowerKey} ("name(country)", lower case) is open in this war. */
    public static boolean open(TerritoryFile.WarInfo war, String lowerKey, boolean drawnOpen) {
        if (war != null && war.bridges != null) {
            for (var e : war.bridges.entrySet())
                if (e.getKey() != null && e.getValue() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lowerKey)) return !WarFront.simple(e.getValue()).equals("closed");
        }
        if (LiveControl.isLive(war)) {
            Boolean live = liveState(lowerKey);
            if (live != null) return live;
        }
        return drawnOpen;
    }

    private static Boolean liveState(String lowerKey) {
        for (DynmapClient.Marker m : DynmapClient.markers()) {
            boolean same = m.id().toLowerCase(Locale.ROOT).equals(lowerKey);
            if (!same) {
                Matcher mid = MAP_ID.matcher(m.id());
                same = mid.matches() && (mid.group(1) + "(" + mid.group(2) + ")").toLowerCase(Locale.ROOT).equals(lowerKey);
            }
            if (!same) continue;
            Matcher icon = STATE.matcher(m.icon() == null ? "" : m.icon());
            if (icon.find()) return icon.group(1).equalsIgnoreCase("open");
        }
        return null;
    }

    /** The key a marker is looked up by. */
    public static String key(TerritoryFile.Locality l) { return (l.name + "(" + l.country + ")").toLowerCase(Locale.ROOT); }
}
