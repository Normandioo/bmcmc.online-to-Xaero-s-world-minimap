package com.cumania.territories.mixin;

import com.cumania.territories.compat.WorldMapTerritoryHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Xaero's builds this registry, lets Open Parties and Claims add its highlighter, then calls end()
 * which freezes the list. Registering just before that puts us next to the claims.
 */
@Mixin(value = HighlighterRegistry.class, remap = false)
public abstract class WorldMapRegistryMixin {
    @Inject(method = "end", at = @At("HEAD"), remap = false)
    private void cumania$addTerritories(CallbackInfo ci) {
        ((HighlighterRegistry) (Object) this).register(new WorldMapTerritoryHighlighter());
    }
}
