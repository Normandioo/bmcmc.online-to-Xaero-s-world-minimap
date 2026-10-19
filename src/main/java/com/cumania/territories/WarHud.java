package com.cumania.territories;

import com.cumania.territories.data.DynmapClient;
import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.WarFront;
import com.cumania.territories.data.WarPaint;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A small table on the game screen, next to the minimap: each nation of the war you are following with how close it is to Capitulation
 * (7.9: its score is the value of its settlements held by another side, and it capitulates over 100 once its Capital is taken).
 * Off until turned on in the map settings. Follows the first war that is running (a live one first).
 */
public final class WarHud {
    private WarHud() {}

    private static final int ROW = 11, BAR_W = 34, PAD = 4;

    public static void register() {
        int[] ticks = {0};
        // a live war keeps being followed in the background while the table is on, even with the map closed
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++ticks[0] < 20) return;
            ticks[0] = 0;
            var config = TerritoryManager.config();
            if (!config.warHud || !config.liveWar || !TerritoryManager.active() || client.player == null) return;
            var war = followed();
            if (war != null && war.live) DynmapClient.wantMarkersInBackground();
        });
        HudRenderCallback.EVENT.register(WarHud::render);
    }

    private static boolean running(TerritoryFile.WarInfo war) {
        if (war.ended == null) return true;
        try { return Instant.parse(war.ended).isAfter(Instant.now()); } catch (RuntimeException e) { return false; }
    }

    private static TerritoryFile.WarInfo followed() {
        var wars = TerritoryManager.index().war().wars;
        if (wars == null) return null;
        TerritoryFile.WarInfo first = null;
        for (var w : wars) {
            if (w == null || w.sides == null || !running(w)) continue;
            if (w.live) return w;
            if (first == null) first = w;
        }
        return first;
    }

    private static void render(GuiGraphics g, Object tick) {
        var config = TerritoryManager.config();
        Minecraft mc = Minecraft.getInstance();
        if (!config.warHud || !TerritoryManager.active() || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        var war = followed();
        if (war == null) return;
        var index = TerritoryManager.index();
        WarFront front = WarFront.of(index, war);
        List<WarFront.Score> scores = new ArrayList<>(front.scores());
        if (scores.isEmpty()) return;
        scores.sort(Comparator.comparingInt(WarFront.Score::side).thenComparingDouble(s -> -s.score()));
        if (scores.size() > 12) scores = scores.subList(0, 12);

        Font font = mc.font;
        String title = "Capitulation";
        int nameW = font.width(title);
        for (var s : scores) nameW = Math.max(nameW, font.width(shorten(font, s.nation())) + 8);
        int w = PAD + nameW + 6 + BAR_W + 4 + font.width("100%") + PAD;
        int h = PAD + 11 + scores.size() * ROW + PAD;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = "right".equalsIgnoreCase(config.warHudCorner) ? screenW - w - config.warHudX : config.warHudX;
        int y = config.warHudY > 0 ? config.warHudY : 100;
        int[] box = "minimap".equalsIgnoreCase(config.warHudCorner) ? minimap(screenW, screenH) : null;
        if (box != null) {
            // beside the minimap, on the side that faces the middle of the screen, level with its top
            boolean onLeft = box[0] + box[2] / 2 < screenW / 2;
            x = onLeft ? box[0] + box[2] + config.warHudX : box[0] - w - config.warHudX;
            y = box[1] + Math.max(0, config.warHudY);
        } else if ("minimap".equalsIgnoreCase(config.warHudCorner)) x = config.warHudX;
        x = Math.max(0, Math.min(x, screenW - w));
        y = Math.max(0, Math.min(y, screenH - h));

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        g.fill(x, y, x + w, y + h, 0x90101010);
        g.drawString(font, title, x + PAD, y + PAD, 0xFFF4F4F4, true);
        int ry = y + PAD + 11;
        for (var s : scores) {
            int side = WarPaint.sideColor(war.sides.get(s.side()), s.side());
            g.fill(x + PAD, ry + 2, x + PAD + 5, ry + 7, 0xFF000000 | side);
            g.drawString(font, shorten(font, s.nation()), x + PAD + 8, ry, s.capitulated() ? 0xFFFF6060 : 0xFFE8E8E8, true);
            int bx = x + w - PAD - font.width("100%") - 4 - BAR_W;
            double pct = Math.max(0, s.score());
            int fill = (int) Math.round(BAR_W * Math.min(pct, 100) / 100.0);
            g.fill(bx, ry + 2, bx + BAR_W, ry + 7, 0xFF303030);
            int barRgb = s.capitulated() ? 0xFF5050 : s.capitalTaken() ? 0xFFA030 : pct >= 66 ? 0xE0C040 : 0x7CD67C;
            if (fill > 0) g.fill(bx, ry + 2, bx + fill, ry + 7, 0xFF000000 | barRgb);
            String text = s.capitulated() ? "CAP" : Math.round(pct) + "%";
            g.drawString(font, text, x + w - PAD - font.width(text), ry, s.capitulated() ? 0xFFFF6060 : s.capitalTaken() ? 0xFFFFB050 : 0xFFE8E8E8, true);
            ry += ROW;
        }
    }

    private static int[] minimap(int screenW, int screenH) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("xaerominimap")) return null;
        try { return com.cumania.territories.compat.MinimapAnchor.rect(screenW, screenH); } catch (Throwable t) { return null; }
    }

    private static String shorten(Font font, String s) {
        String name = s == null ? "" : s;
        return font.width(name) <= 64 ? name : font.plainSubstrByWidth(name, 60) + "..";
    }
}
