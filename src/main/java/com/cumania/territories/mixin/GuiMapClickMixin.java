package com.cumania.territories.mixin;

import com.cumania.territories.compat.CameraTarget;
import com.cumania.territories.compat.CountryPanel;
import com.cumania.territories.data.TerritoryFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.element.HoveredMapElementHolder;
import xaero.map.gui.GuiMap;

/**
 * Xaero's calls mapClicked(button, x, y) when you click the map without dragging it. We only look
 * (button 0 = left): which block is under the mouse, and which of our cities, if any, was clicked.
 */
@Mixin(value = GuiMap.class, remap = false)
public abstract class GuiMapClickMixin implements CameraTarget {
    /** The same field Xaero fills for "hop to coordinates": the map then glides there on its own. */
    @Shadow private int[] cameraDestination;
    /** The zoom Xaero is gliding towards (static, shared by every map screen). */
    @Shadow private static double destScale;

    @Shadow private int mouseBlockPosX;
    @Shadow private int mouseBlockPosZ;
    @Shadow private HoveredMapElementHolder<?, ?> viewedOnMousePress;

    @Inject(method = "mapClicked", at = @At("HEAD"), remap = false)
    private void cumania$mapClicked(int button, int x, int y, CallbackInfo ci) {
        if (button != 0) return;
        Object element = viewedOnMousePress == null ? null : viewedOnMousePress.getElement();
        CountryPanel.onMapClick(mouseBlockPosX, mouseBlockPosZ, element instanceof TerritoryFile.Locality locality ? locality : null);
    }

    @Override
    public void cumania$goTo(int blockX, int blockZ, double minZoom) {
        com.cumania.territories.compat.Focus.set(blockX, blockZ);
        cameraDestination = new int[]{blockX, blockZ};
        if (minZoom > 0 && destScale < minZoom) destScale = minZoom;
    }
}
