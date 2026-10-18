package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.TerritoryColors;
import com.cumania.territories.data.TerritoryIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.AbstractHighlighter;

import java.util.List;

/**
 * Paints the territories on Xaero's World Map (the full-screen map), block by block: Xaero's asks for
 * 16x16 colours per chunk, and blocks with alpha 0 are left untouched.
 */
public final class WorldMapTerritoryHighlighter extends AbstractHighlighter {

    public WorldMapTerritoryHighlighter() {
        super(true);   // also over parts of the map you haven't explored
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
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return regionHasHighlights(dimension, regionX, regionZ) ? 31 * TerritoryManager.version() + 1 : 0;
    }

    @Override
    public int[] getChunkHighlitColor(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!applies(dimension)) return null;
        var config = TerritoryManager.config();
        return TerritoryColors.compute(TerritoryManager.index(), chunkX, chunkZ, config.fillAlpha, config.borderAlpha, resultStore)
                ? resultStore : null;
    }

    private static Component countryName(TerritoryIndex index, int country) {
        int rgb = index.countryRgb(country);
        return Component.literal(index.countryName(country)).withStyle(style -> style.withColor(rgb));
    }

    @Override
    public Component getBlockHighlightSubtleTooltip(ResourceKey<Level> dimension, int blockX, int blockZ) {
        if (!applies(dimension)) return null;
        TerritoryIndex index = TerritoryManager.index();
        int country = index.countryAtBlock(blockX, blockZ);
        return country < 0 ? null : countryName(index, country);
    }

    @Override
    public Component getBlockHighlightBluntTooltip(ResourceKey<Level> dimension, int blockX, int blockZ) {
        if (!applies(dimension)) return null;
        TerritoryIndex index = TerritoryManager.index();
        int country = index.countryAtBlock(blockX, blockZ);
        if (country < 0) return null;
        Component name = countryName(index, country);
        if (!TerritoryManager.config().showProvince) return name;
        int province = index.provinceAtBlock(blockX, blockZ);
        return province < 0 ? name : Component.empty().append(name).append(Component.literal(" - " + index.provinceLabel(province)));
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> tooltips, ResourceKey<Level> dimension, int blockX, int blockZ, int unused) {
        Component line = getBlockHighlightBluntTooltip(dimension, blockX, blockZ);
        if (line != null) tooltips.add(line);
    }
}
