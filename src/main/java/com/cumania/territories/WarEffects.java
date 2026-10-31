package com.cumania.territories;

import com.cumania.territories.data.DynmapClient;
import com.cumania.territories.data.LiveControl;
import com.cumania.territories.data.WarMarkers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.List;

/**
 * Once a second, with the map open or not: works out what the war effects mark (WarMarkers) and, for a live war, keeps dynmap's markers
 * and players coming in the background. Runs on the game thread.
 */
public final class WarEffects {
    private WarEffects() {}

    public static void register() {
        int[] ticks = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks[0] < 20) return;
            ticks[0] = 0;
            tick();
        });
    }

    private static void tick() {
        var config = TerritoryManager.config();
        var mc = net.minecraft.client.Minecraft.getInstance();
        // Auto map: which dynmap world are you in? (dynmap lists every player with its world)
        if (config.autoMap && TerritoryManager.index() != null && mc.player != null) {
            DynmapClient.wantPlayers();
            var me = DynmapClient.find(mc.player.getName().getString());
            if (me != null) TerritoryManager.followWorld(me.world());
        }
        // a war view on the map keeps its colours up to date too, even with the war menu hidden or the map closed
        boolean viewing = com.cumania.territories.data.WarPaint.current() != null;
        if (!TerritoryManager.active() || mc.player == null || !(config.showSwords || config.showFlags || viewing)) { WarMarkers.clear(); return; }
        var index = TerritoryManager.index();
        var war = WarMarkers.followed(index, com.cumania.territories.compat.WarMenu.viewedWarId());
        if (war == null) { WarMarkers.clear(); return; }
        if (war.live && config.liveWar) {
            DynmapClient.wantMarkersInBackground();
        }
        if (config.showSwords) DynmapClient.wantPlayers();
        if (war.live && !LiveControl.isLive(war)) return;   // dynmap has not answered yet: only the site's last list is known
        List<DynmapClient.Online> players = config.showSwords && DynmapClient.playersKnown() ? DynmapClient.players() : List.of();
        WarMarkers.update(index, war, players, TerritoryManager.dynmapWorld(), System.currentTimeMillis());
    }
}
