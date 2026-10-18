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
}
