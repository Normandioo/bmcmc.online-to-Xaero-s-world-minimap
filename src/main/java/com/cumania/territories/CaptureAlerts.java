package com.cumania.territories;

import com.cumania.territories.data.DynmapClient;
import com.cumania.territories.data.LiveControl;
import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.WarPaint;
import com.cumania.territories.data.WarScore;
import com.cumania.territories.data.WarWatch;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Says in the chat when a city changes hands in a war ("Okeham (Greneshire) was captured by Attackers"), whether the map is open or not.
 * Live wars are followed through dynmap's markers in the background while one is going; the first reading of a war only sets the starting
 * point, so nothing already captured is announced when you join. Runs on the game thread.
 */
public final class CaptureAlerts {
    private CaptureAlerts() {}

    private static final int MAX_LINES = 5;   // more than this at once is one summary line

    private static boolean running(TerritoryFile.WarInfo war) {
        if (war.ended == null) return true;
        try { return Instant.parse(war.ended).isAfter(Instant.now()); } catch (RuntimeException e) { return false; }
    }

    private static boolean hasLiveWar() {
        var wars = TerritoryManager.index().war().wars;
        if (wars != null) for (var w : wars) if (w.live && running(w)) return true;
        return false;
    }

    /** Called about once a second while playing: keeps dynmap's markers coming for a live war even with the map closed. */
    public static void keepWatching() {
        var config = TerritoryManager.config();
        if (!config.chatAlerts || !config.liveWar || !TerritoryManager.active() || Minecraft.getInstance().player == null) return;
        if (hasLiveWar()) DynmapClient.wantMarkersInBackground();
    }

    /** The markers or the site's data changed: compare every running war with how it was and announce what moved. */
    public static void check() {
        var config = TerritoryManager.config();
        var index = TerritoryManager.index();
        var wars = index.war().wars;
        if (!config.chatAlerts || !TerritoryManager.active() || wars == null) return;
        for (TerritoryFile.WarInfo war : wars) {
            if (war.id == null || !running(war)) continue;
            if (war.live && !LiveControl.isLive(war)) continue;   // dynmap has not answered yet: only the site's last list is known
            List<WarWatch.Change> changes = WarWatch.update(war, WarScore.of(index, war));
            if (!changes.isEmpty()) announce(war, changes);
        }
    }

    /** Runs check() on the game thread (the markers arrive on another one). */
    public static void checkSoon() { Minecraft.getInstance().execute(CaptureAlerts::check); }

    private static void announce(TerritoryFile.WarInfo war, List<WarWatch.Change> changes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        List<Component> lines = new ArrayList<>();
        if (changes.size() > MAX_LINES) {
            lines.add(prefix(war).append(Component.literal(changes.size() + " cities changed hands. Open the war menu on the map for the details.").withStyle(ChatFormatting.GRAY)));
        } else {
            for (WarWatch.Change c : changes) lines.add(line(war, c));
        }
        for (Component line : lines) mc.player.displayClientMessage(line, false);
    }

    private static MutableComponent prefix(TerritoryFile.WarInfo war) {
        return Component.literal("[" + (war.name == null ? "War" : war.name) + "] ").withStyle(ChatFormatting.RED);
    }

    private static Component side(TerritoryFile.WarInfo war, int index) {
        if (war.sides == null || index < 0 || index >= war.sides.size()) return Component.literal("nobody").withStyle(ChatFormatting.GRAY);
        int rgb = WarPaint.sideColor(war.sides.get(index), index);
        return Component.literal(war.sides.get(index).name).withStyle(style -> style.withColor(rgb));
    }

    static Component line(TerritoryFile.WarInfo war, WarWatch.Change c) {
        String where = c.locality().name + (c.locality().country == null || c.locality().country.isBlank() ? "" : " (" + c.locality().country + ")");
        MutableComponent city = Component.literal(where).withStyle(ChatFormatting.WHITE);
        MutableComponent out = prefix(war).append(city);
        switch (c.kind()) {
            case CAPTURED -> out.append(Component.literal(" was captured by ").withStyle(ChatFormatting.GRAY)).append(side(war, c.toSide()));
            case RETAKEN -> out.append(Component.literal(" was retaken by ").withStyle(ChatFormatting.GRAY)).append(side(war, c.toSide()));
            case CONTESTED -> out.append(Component.literal(" is now contested").withStyle(ChatFormatting.YELLOW));
            case UNCONTROLLED -> out.append(Component.literal(" is no longer held by anyone").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }
}
