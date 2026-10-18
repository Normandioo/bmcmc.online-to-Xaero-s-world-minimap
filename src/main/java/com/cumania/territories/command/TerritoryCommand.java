package com.cumania.territories.command;

import com.cumania.territories.TerritoryManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

/** /territories [toggle | reload | status] */
public final class TerritoryCommand {
    private TerritoryCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("territories")
                        .executes(ctx -> toggle(ctx.getSource()))
                        .then(ClientCommandManager.literal("toggle").executes(ctx -> toggle(ctx.getSource())))
                        .then(ClientCommandManager.literal("reload").executes(ctx -> {
                            TerritoryManager.reload();
                            ctx.getSource().sendFeedback(Component.literal("Updating territories from the site..."));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("war").executes(ctx -> {
                            com.cumania.territories.compat.WarMenu.toggle();
                            ctx.getSource().sendFeedback(Component.literal("War menu toggled (it shows on Xaero's World Map)"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(ctx -> {
                            var index = TerritoryManager.index();
                            ctx.getSource().sendFeedback(Component.literal(
                                    (TerritoryManager.config().enabled ? "Showing " : "Hidden: ")
                                            + index.countryCount() + " countries, " + index.provinceCount()
                                            + " provinces (data from " + (TerritoryManager.generated().isEmpty() ? "?" : TerritoryManager.generated()) + ")"));
                            return 1;
                        }))));
    }

    private static int toggle(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        boolean on = TerritoryManager.toggle();
        source.sendFeedback(Component.literal(on ? "Territories shown" : "Territories hidden"));
        return 1;
    }
}
