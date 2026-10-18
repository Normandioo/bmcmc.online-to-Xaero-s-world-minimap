package com.cumania.territories.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/** Draws the info panel over Xaero's World Map screen. Only loaded when it is installed. */
public final class WorldMapScreenHooks {
    private WorldMapScreenHooks() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> { CountryPanel.render(graphics, mouseX, mouseY); WarMenu.render(graphics, mouseX, mouseY); SettingsMenu.render(graphics, mouseX, mouseY); });
                // the wheel over the war menu scrolls the menu, not the map's zoom
                ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) -> !WarMenu.onScroll(mouseX, mouseY, vertical));
            }
        });
    }
}
