package com.cumania.territories.compat;

import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.highlight.DimensionHighlighterHandler;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;

/** Makes Xaero's Minimap redraw its highlights. Only loaded when Xaero's Minimap is installed. */
public final class MinimapBridge {
    private MinimapBridge() {}

    public static void refresh() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return;
        MinimapProcessor processor = session.getProcessor();
        if (processor == null || processor.getMinimapWriter() == null) return;
        DimensionHighlighterHandler handler = processor.getMinimapWriter().getDimensionHighlightHandler();
        if (handler != null) handler.requestRefresh();
    }
}
