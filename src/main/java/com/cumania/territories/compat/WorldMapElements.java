package com.cumania.territories.compat;

import xaero.map.WorldMap;
import xaero.map.element.MapElementRenderHandler;

/** Hooks our additions into Xaero's World Map once it has finished loading. Only loaded when it is installed. */
public final class WorldMapElements {
    private WorldMapElements() {}

    private static MapElementRenderHandler registeredOn;

    /** Safe to call often: adds the city renderer once per Xaero's render handler. */
    public static void registerOnce() {
        MapElementRenderHandler handler = WorldMap.mapElementRenderHandler;
        if (handler == null || handler == registeredOn) return;
        handler.add(LocalityRenderer.create());
        handler.add(PlayerRenderer.create());
        handler.add(WarMarkerRenderer.create());
        registeredOn = handler;
    }
}
