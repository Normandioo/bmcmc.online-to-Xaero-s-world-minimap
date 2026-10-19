package com.cumania.territories.compat;

import com.cumania.territories.Config;
import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.IconCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The BMC button on Xaero's World Map (left of the WAR button) and its settings menu: how strong the territory colours and borders
 * are, and what is shown. Changes apply at once and are saved to config/cumania_territories.json.
 */
public final class SettingsMenu {
    private SettingsMenu() {}

    private static final int PW = 224, PAD = 9, ROW = 17, TOP = 30, HEADER_H = 24, BTN = 20;
    private static final int TEXT = 0xFFF4F4F4, MUTED = 0xFFB4B4B4, LINK = 0xFF8FD0FF, ON = 0xFF7CD67C, OFF = 0xFF8A8A8A, ACCENT = 0xFF6AA8E8;

    private static boolean open;
    private static int mouseX, mouseY, btnX, panelX, panelH;
    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {}
    private static final List<Hit> hits = new ArrayList<>();
    private static long repaintAt;   // clicking +/- many times redraws the map once, shortly after the last click

    public static boolean isOpen() { return open; }
    public static void close() { open = false; }
    public static void toggle() { if (!open) WarMenu.close(); open = !open; }

    /** Left of the WAR button. */
    static int buttonX(int screenW) { return WarMenu.chipLeft(screenW) - BTN - 4; }

    public static boolean onClick() {
        if (mouseX >= btnX && mouseX < btnX + BTN && mouseY >= 6 && mouseY < 6 + 18) { toggle(); return true; }
        if (!open || panelH <= 0 || mouseX < panelX || mouseX >= panelX + PW || mouseY < TOP || mouseY >= TOP + panelH) return false;
        for (Hit hit : hits) {
            if (mouseX >= hit.x0 && mouseX < hit.x1 && mouseY >= hit.y0 && mouseY < hit.y1) { hit.action.run(); break; }
        }
        return true;
    }

    private static void changed(boolean redraw) {
        TerritoryManager.saveConfig();
        if (redraw) repaintAt = System.currentTimeMillis() + 350;
    }

    public static void render(GuiGraphics g, int mx, int my) {
        mouseX = mx; mouseY = my;
        hits.clear();
        if (repaintAt != 0 && System.currentTimeMillis() >= repaintAt) { repaintAt = 0; TerritoryManager.repaint(); }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();
        Config c = TerritoryManager.config();

        // the BMC button: the site's logo
        btnX = buttonX(screenW);
        boolean over = mx >= btnX && mx < btnX + BTN && my >= 6 && my < 24;
        g.fill(btnX - 1, 5, btnX + BTN + 1, 25, open ? 0xFFB8D8FF : 0xFFAAAAAA);
        g.fill(btnX, 6, btnX + BTN, 24, open ? 0xFF3A5E8A : over ? 0xFF4A4A4A : 0xFF2A2A2A);
        IconCache.Icon logo = IconCache.fetch(c.logoUrl, 16);
        if (logo != null) {
            for (IconCache.Rect r : logo.rects()) g.fill(btnX + 2 + r.x(), 7 + r.y(), btnX + 2 + r.x() + r.w(), 7 + r.y() + r.h(), r.argb());
        } else {
            g.drawString(font, "BMC", btnX + (BTN - font.width("BMC")) / 2, 11, 0xFFFFFFFF, true);
        }
        if (!open) { panelH = 0; return; }

        // rows
        int rowCount = 14;
        panelH = HEADER_H + 7 + rowCount * ROW + 14;
        panelH = Math.min(panelH, screenH - TOP - 6);
        int x0 = Math.max(2, Math.min(btnX + BTN - PW, screenW - PW - 2)), y0 = TOP;
        panelX = x0;
        g.fill(x0 - 1, y0 - 1, x0 + PW + 1, y0 + panelH + 1, ACCENT);
        g.fill(x0, y0, x0 + PW, y0 + panelH, 0xF2101010);
        g.fill(x0, y0, x0 + PW, y0 + HEADER_H, 0xFF1E3A5C);
        g.fill(x0, y0 + HEADER_H - 1, x0 + PW, y0 + HEADER_H, ACCENT);
        Component title = Component.literal("Map settings").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, x0 + (PW - font.width(title)) / 2, y0 + 8, 0xFFFFFFFF, true);
        g.drawString(font, "x", x0 + PW - 14, y0 + 8, 0xFFDDDDDD, true);
        hits.add(new Hit(x0 + PW - 22, y0, x0 + PW, y0 + HEADER_H, () -> open = false));

        int y = y0 + HEADER_H + 7;
        y = stepper(g, font, x0, y, "Territory colour", c.fillAlpha, v -> c.fillAlpha = v);
        y = stepper(g, font, x0, y, "Borders", c.borderAlpha, v -> c.borderAlpha = v);
        y = toggle(g, font, x0, y, "Show territories", c.enabled, () -> { TerritoryManager.toggle(); });
        y = toggle(g, font, x0, y, "Cities", c.showLocalities, () -> { c.showLocalities = !c.showLocalities; changed(false); });
        y = toggle(g, font, x0, y, "City names", c.showLocalityNames, () -> { c.showLocalityNames = !c.showLocalityNames; changed(false); });
        boolean site = !"simple".equalsIgnoreCase(c.markerStyle);
        y = choice(g, font, x0, y, "City markers", site ? "Site icons" : "Simple", () -> { c.markerStyle = site ? "simple" : "site"; changed(false); });
        y = toggle(g, font, x0, y, "Info panel on click", c.showPanel, () -> { c.showPanel = !c.showPanel; changed(false); });
        y = toggle(g, font, x0, y, "Province in tooltip", c.showProvince, () -> { c.showProvince = !c.showProvince; changed(false); });
        y = toggle(g, font, x0, y, "Online players", c.showOnlinePlayers, () -> { c.showOnlinePlayers = !c.showOnlinePlayers; changed(false); });
        y = toggle(g, font, x0, y, "Live war control", c.liveWar, () -> { c.liveWar = !c.liveWar; changed(false); WarMenu.liveChanged(); });
        y = toggle(g, font, x0, y, TerritoryManager.index().limits().isEmpty() ? "Capture limits (none on the site)" : "Capture limits", c.showCaptureLimits, () -> { c.showCaptureLimits = !c.showCaptureLimits; changed(true); });
        y = toggle(g, font, x0, y, "Grey out others in wars", c.warGreyOthers, () -> { c.warGreyOthers = !c.warGreyOthers; changed(true); });
        g.drawString(font, Fit.text(font, "Saved to config/cumania_territories.json", PW - 2 * PAD), x0 + PAD, y + 3, 0xFF707070, false);
    }

    private static boolean over(int x0, int y, int x1) { return mouseX >= x0 && mouseX < x1 && mouseY >= y - 2 && mouseY < y + ROW - 3; }

    private static int toggle(GuiGraphics g, Font font, int x0, int y, String label, boolean on, Runnable action) {
        boolean hover = over(x0 + PAD, y, x0 + PW - PAD);
        g.fill(x0 + PAD, y, x0 + PAD + 10, y + 10, on ? ON : 0xFF444444);
        g.fill(x0 + PAD + 1, y + 1, x0 + PAD + 9, y + 9, on ? 0xFF1C3A1C : 0xFF181818);
        if (on) g.fill(x0 + PAD + 3, y + 3, x0 + PAD + 7, y + 7, ON);
        g.drawString(font, Fit.text(font, label, PW - 2 * PAD - 18), x0 + PAD + 16, y + 1, hover ? 0xFFFFFFFF : TEXT, true);
        hits.add(new Hit(x0 + PAD, y - 2, x0 + PW - PAD, y + ROW - 3, action));
        return y + ROW;
    }

    private static int choice(GuiGraphics g, Font font, int x0, int y, String label, String value, Runnable action) {
        boolean hover = over(x0 + PAD, y, x0 + PW - PAD);
        g.drawString(font, label, x0 + PAD, y + 1, hover ? 0xFFFFFFFF : TEXT, true);
        String shown = "[ " + value + " ]";
        g.drawString(font, shown, x0 + PW - PAD - font.width(shown), y + 1, hover ? 0xFFFFFFFF : LINK, true);
        hits.add(new Hit(x0 + PAD, y - 2, x0 + PW - PAD, y + ROW - 3, action));
        return y + ROW;
    }

    /** "Label        [-] 35% [+]" in steps of 10%. */
    private static int stepper(GuiGraphics g, Font font, int x0, int y, String label, int alpha, java.util.function.IntConsumer set) {
        int percent = Math.round(alpha * 100f / 255f);
        g.drawString(font, label, x0 + PAD, y + 1, TEXT, true);
        String value = percent + "%";
        int right = x0 + PW - PAD;
        int plusX = right - 14, valueX = plusX - 6 - 30, minusX = valueX - 6 - 14;
        button(g, font, minusX, y - 1, "-", () -> { set.accept(Math.max(0, Math.round((percent - 10) / 10f) * 10 * 255 / 100)); changed(true); });
        g.drawString(font, value, valueX + (30 - font.width(value)) / 2, y + 1, 0xFFFFFFFF, true);
        button(g, font, plusX, y - 1, "+", () -> { set.accept(Math.min(255, Math.round((percent + 10) / 10f) * 10 * 255 / 100)); changed(true); });
        return y + ROW;
    }

    private static void button(GuiGraphics g, Font font, int x, int y, String text, Runnable action) {
        boolean hover = mouseX >= x && mouseX < x + 14 && mouseY >= y && mouseY < y + 13;
        g.fill(x, y, x + 14, y + 13, hover ? 0xFF4A6A90 : 0xFF2A3A4C);
        g.drawString(font, text, x + (14 - font.width(text)) / 2, y + 3, 0xFFFFFFFF, true);
        hits.add(new Hit(x, y, x + 14, y + 13, action));
    }
}
