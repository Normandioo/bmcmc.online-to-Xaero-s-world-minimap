package com.cumania.territories.compat;

/** Implemented onto Xaero's map screen by GuiMapClickMixin: glide the map's camera to a block position. */
public interface CameraTarget {
    /** Glide to the block; if minZoom > 0 and the map is zoomed out further than that, zoom in to it. */
    void cumania$goTo(int blockX, int blockZ, double minZoom);
}
