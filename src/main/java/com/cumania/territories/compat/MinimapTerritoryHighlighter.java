package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.TerritoryColors;
import com.cumania.territories.data.TerritoryIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.AbstractHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

/** Paints the territories on Xaero's Minimap, block by block. */
public final class MinimapTerritoryHighlighter extends AbstractHighlighter {

    public MinimapTerritoryHighlighter() {
        super(true);
    }

    private static boolean applies(ResourceKey<Level> dimension) {
        return TerritoryManager.active() && dimension == TerritoryManager.dimension();
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return applies(dimension) && TerritoryManager.index().regionHasTerritory(regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return applies(dimension) && TerritoryManager.index().chunkHasTerritory(chunkX, chunkZ);
    }

    @Override
    public int[] getChunkHighlitColor(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!applies(dimension)) return null;
        var config = TerritoryManager.config();
        return TerritoryColors.compute(TerritoryManager.index(), chunkX, chunkZ, config.fillAlpha, config.borderAlpha, resultStore)
                ? resultStore : null;
    }

    @Override
    public void addBlockHighlightTooltips(InfoDisplayCompiler compiler, ResourceKey<Level> dimension, int blockX, int blockZ, int unused) {
        if (!applies(dimension)) return;
        TerritoryIndex index = TerritoryManager.index();
        int country = index.countryAtBlock(blockX, blockZ);
        if (country < 0) return;
        int rgb = index.countryRgb(country);
        int province = TerritoryManager.config().showProvince ? index.provinceAtBlock(blockX, blockZ) : -1;
        String text = index.countryName(country) + (province < 0 ? "" : " - " + index.provinceLabel(province));
        compiler.addLine(Component.literal(text).withStyle(style -> style.withColor(rgb)));
    }
}
