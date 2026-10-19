package com.cumania.territories.compat;

import com.cumania.territories.data.DynmapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where each online player is drawn. A player the game itself can see (loaded around you) is placed from the game, which is exact and
 * instant; everyone else from dynmap's latest answer. Either way the marker glides towards the spot instead of jumping when an
 * answer arrives, so it moves smoothly between answers. All on the render thread.
 */
final class PlayerTracker {
    private PlayerTracker() {}

    private static final class Spot { double x, z; long at; }

    private static final Map<String, Spot> spots = new HashMap<>();
    private static final double SNAP_BLOCKS = 48;   // further than this is a teleport or a new arrival: no gliding

    /** Moves every shown marker towards where its player is. Call once per frame with the players about to be drawn. */
    static void update(Iterable<DynmapClient.Online> players) {
        long now = System.nanoTime() / 1_000_000L;
        Minecraft mc = Minecraft.getInstance();
        Set<String> seen = new HashSet<>();
        long glide = Math.max(250, DynmapClient.playersEveryMs() + 150);
        for (DynmapClient.Online p : players) {
            seen.add(p.account());
            double tx = p.x(), tz = p.z();
            long tau = glide;
            Player near = nearby(mc, p);
            if (near != null) { tx = near.getX(); tz = near.getZ(); tau = 120; }   // the game moves players every tick: follow closely
            Spot s = spots.get(p.account());
            if (s == null) { s = new Spot(); s.x = tx; s.z = tz; s.at = now; spots.put(p.account(), s); continue; }
            double dt = Math.max(0, Math.min(500, now - s.at));
            s.at = now;
            double dx = tx - s.x, dz = tz - s.z;
            if (dx * dx + dz * dz > SNAP_BLOCKS * SNAP_BLOCKS) { s.x = tx; s.z = tz; continue; }
            double k = 1 - Math.exp(-dt / (tau / 3.0));   // about 95% of the way after `tau`
            s.x += dx * k; s.z += dz * k;
        }
        spots.keySet().retainAll(seen);
    }

    private static Player nearby(Minecraft mc, DynmapClient.Online p) {
        if (mc.level == null) return null;
        for (Player other : mc.level.players()) {
            String name = other.getScoreboardName();
            if (name.equalsIgnoreCase(p.account()) || name.equalsIgnoreCase(p.name())) return other;
        }
        return null;
    }

    static double x(DynmapClient.Online p) { Spot s = spots.get(p.account()); return s != null ? s.x : p.x(); }
    static double z(DynmapClient.Online p) { Spot s = spots.get(p.account()); return s != null ? s.z : p.z(); }
}
