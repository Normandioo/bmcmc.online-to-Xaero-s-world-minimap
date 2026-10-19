package com.cumania.territories.compat;

import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;

/** Where Xaero's Minimap sits on the screen, so the war table can go beside it. Only loaded when Xaero's Minimap is installed. */
public final class MinimapAnchor {
    private MinimapAnchor() {}

    /** The minimap's box in GUI pixels {x, y, width, height}, or null when it is not on screen. */
    public static int[] rect(int screenW, int screenH) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null || !session.isActive()) return null;
        int w = session.getWidth(1.0), h = session.getHeight(1.0);
        if (w <= 0 || h <= 0) return null;
        return new int[]{session.getEffectiveX(screenW, 1.0), session.getEffectiveY(screenH, 1.0), w, h};
    }
}
