package com.cumania.territories.mixin;

import com.cumania.territories.compat.MinimapTerritoryHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.minimap.highlight.HighlighterRegistry;

/** Same as WorldMapRegistryMixin, for Xaero's Minimap's own registry. */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class MinimapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void cumania$addTerritories(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new MinimapTerritoryHighlighter());
    }
}
