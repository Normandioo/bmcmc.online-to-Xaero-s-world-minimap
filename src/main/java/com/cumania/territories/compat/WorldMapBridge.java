package com.cumania.territories.compat;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

/** Makes Xaero's World Map ask the highlighter again. Only loaded when Xaero's World Map is installed. */
public final class WorldMapBridge {
    private WorldMapBridge() {}

    public static void refresh(ResourceKey<Level> dimension) {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) return;
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) return;
        MapWorld world = processor.getMapWorld();
        if (world == null) return;
        MapDimension mapDimension = world.getDimension(dimension);
        if (mapDimension != null) mapDimension.getHighlightHandler().clearCachedHashes();   // what Open Parties and Claims does on a claim change
    }

    /** Like refresh, but only for the given regions ({regionX, regionZ}). */
    public static void refreshRegions(ResourceKey<Level> dimension, java.util.List<int[]> regions) {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) return;
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) return;
        MapWorld world = processor.getMapWorld();
        if (world == null) return;
        MapDimension mapDimension = world.getDimension(dimension);
        if (mapDimension == null) return;
        // nearest to the player first: the part of the map you are looking at is the first to be asked for again
        var player = net.minecraft.client.Minecraft.getInstance().player;
        int px = player == null ? 0 : player.getBlockX() >> 9, pz = player == null ? 0 : player.getBlockZ() >> 9;
        java.util.List<int[]> sorted = new java.util.ArrayList<>(regions);
        sorted.sort(java.util.Comparator.comparingLong(r -> (long) (r[0] - px) * (r[0] - px) + (long) (r[1] - pz) * (r[1] - pz)));
        for (int[] r : sorted) mapDimension.getHighlightHandler().clearCachedHash(r[0], r[1]);
    }
}
