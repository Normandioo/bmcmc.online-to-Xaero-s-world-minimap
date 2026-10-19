package com.cumania.territories.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notices when a city changes hands in a war, by comparing each reading of the war with the one before. The first reading of a war is
 * only remembered (so joining the game does not announce everything that was captured before), and a live war is not read at all
 * until dynmap has answered, since until then the list shows only what the site last exported. No Minecraft classes in here.
 */
public final class WarWatch {
    private WarWatch() {}

    public enum Kind { CAPTURED, RETAKEN, CONTESTED, UNCONTROLLED }

    /** toSide = the side now holding it (-1 for contested / uncontrolled); fromSide = the one holding it before. */
    public record Change(String warId, String warName, TerritoryFile.Locality locality, Kind kind, int fromSide, int toSide) {}

    private static final Map<String, Map<String, String>> last = new HashMap<>();

    private static String signature(WarScore.Holding h) { return h.state() + ":" + h.holder(); }

    /** The changes since the last reading of this war (empty the first time). */
    public static synchronized List<Change> update(TerritoryFile.WarInfo war, WarScore.Result result) {
        Map<String, String> before = last.get(war.id);
        Map<String, String> now = new HashMap<>();
        List<Change> out = new ArrayList<>();
        for (WarScore.Holding h : result.holdings()) {
            String key = WarScore.key(h.locality().name, h.locality().country);
            String sig = signature(h);
            now.put(key, sig);
            if (before == null) continue;
            String was = before.get(key);
            if (was == null || was.equals(sig)) continue;
            int fromSide = -1;
            int colon = was.lastIndexOf(':');
            if (colon >= 0) { try { fromSide = Integer.parseInt(was.substring(colon + 1)); } catch (NumberFormatException ignored) { } }
            Kind kind = h.disputed() ? ("contested".equals(h.state()) ? Kind.CONTESTED : Kind.UNCONTROLLED)
                    : h.holder() < 0 ? null : h.holder() == h.owner() ? Kind.RETAKEN : Kind.CAPTURED;
            if (kind == null) continue;
            out.add(new Change(war.id, war.name, h.locality(), kind, fromSide, h.holder()));
        }
        last.put(war.id, now);
        return out;
    }

    /** Forgets everything (a different data set was loaded, or the setting was switched off). */
    public static synchronized void reset() { last.clear(); }
}
