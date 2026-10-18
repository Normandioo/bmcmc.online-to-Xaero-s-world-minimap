package com.cumania.territories.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/** Draws the info panel over Xaero's World Map screen. Only loaded when it is installed. */
public final class WorldMapScreenHooks {
    private WorldMapScreenHooks() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen.getClass().getName().equals("xaero.map.gui.GuiMap")) {
                ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, tickDelta) -> { CountryPanel.render(graphics, mouseX, mouseY); WarMenu.render(graphics, mouseX, mouseY); SettingsMenu.render(graphics, mouseX, mouseY); });
            }
        });
    }
}
