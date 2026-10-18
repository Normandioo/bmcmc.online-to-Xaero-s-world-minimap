package com.cumania.territories.compat;

/** Read access to Xaero's World Map camera (implemented by the GuiMap mixin). */
public interface CameraView {
    double cumania$cameraX();
    double cumania$cameraZ();
    /** Real (framebuffer) pixels per block. */
    double cumania$scale();
    /** The GUI scale factor Xaero's divides real pixels by. */
    double cumania$screenScale();
    /** The block Xaero's says is under the mouse. */
    int cumania$mouseBlockX();
    int cumania$mouseBlockZ();
}
