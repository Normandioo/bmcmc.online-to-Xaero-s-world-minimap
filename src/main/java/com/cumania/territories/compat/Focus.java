package com.cumania.territories.compat;

import com.cumania.territories.data.TerritoryFile;

/** The city the camera was just sent to: drawn with a pulsing ring for a few seconds so it is easy to spot. */
public final class Focus {
    private Focus() {}

    public static final long DURATION_MS = 6000, PULSE_MS = 1100;
    private static volatile int x, z;
    private static volatile long since;

    public static void set(int blockX, int blockZ) { x = blockX; z = blockZ; since = System.currentTimeMillis(); }
    public static void clear() { since = 0; }

    private static long age() { return since == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - since; }
    public static boolean running() { return age() < DURATION_MS; }

    /** Whether this city is the one being shown (a few blocks of tolerance: the wars use their own coordinates). */
    public static boolean is(TerritoryFile.Locality l) {
        return running() && Math.abs(l.x - x) <= 4 && Math.abs(l.z - z) <= 4;
    }

    /** 0..1 through the current pulse. */
    public static float pulse() { return (age() % PULSE_MS) / (float) PULSE_MS; }

    /** 1 while the effect plays, fading to 0 over its last second. */
    public static float strength() {
        long left = DURATION_MS - age();
        return left <= 0 ? 0 : left >= 1000 ? 1f : left / 1000f;
    }
}
