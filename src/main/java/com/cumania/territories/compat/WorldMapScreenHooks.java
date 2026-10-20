package com.cumania.territories.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/** Draws the info panel over Xaero's World Map screen. Only loaded when it is installed. */
public final class WorldMapScreenHooks {
    private WorldMapScreenHooks() {}

    private static boolean swallowed;   // the left press went to one of our menus, so its release does too

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> { CountryPanel.render(graphics, mouseX, mouseY); WarMenu.render(graphics, mouseX, mouseY); SettingsMenu.render(graphics, mouseX, mouseY); });
                // the wheel over the war menu scrolls the menu, not the map's zoom
                // a press on our buttons or menus is ours: Xaero must not start dragging the map (the sliders are dragged)
                ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> {
                    if (click.button() != 0 || !(SettingsMenu.onClick() || WarMenu.onClick())) return true;
                    swallowed = true;
                    return false;
                });
                ScreenMouseEvents.allowMouseRelease(screen).register((s, click) -> {
                    boolean mine = swallowed;
                    swallowed = false;
                    return !mine;
                });
                ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> !WarMenu.onScroll(mouseX, mouseY, vertical));
            }
        });
    }
}
