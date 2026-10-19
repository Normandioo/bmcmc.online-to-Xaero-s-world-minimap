package com.cumania.territories;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CumaniaTerritories implements ClientModInitializer {
    public static final String ID = "cumania_territories";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    @Override
    public void onInitializeClient() {
        boolean worldMapLoaded = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
        com.cumania.territories.data.DynmapClient.onMarkersChanged(() -> {
            if (worldMapLoaded) com.cumania.territories.compat.WarMenu.liveChanged();
        });
        // the war effects (swords, flags); a live war keeps being followed in the background while they are on
        com.cumania.territories.WarEffects.register();
        TerritoryManager.init(FabricLoader.getInstance().getConfigDir());
        com.cumania.territories.command.TerritoryCommand.register();
        if (worldMapLoaded) com.cumania.territories.compat.WorldMapScreenHooks.register();
        // Pick up changes the site published since last time whenever you join a world
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            TerritoryManager.reload();
            if (worldMapLoaded) {
                com.cumania.territories.compat.CountryPanel.clear();
                com.cumania.territories.compat.WorldMapElements.registerOnce();   // Xaero's has finished loading by now
            }
        });
    }
}
