package com.cumania.territories.command;

import com.cumania.territories.TerritoryManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

/** /territories [toggle | reload | status] */
public final class TerritoryCommand {
    private TerritoryCommand() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("territories")
                        .executes(ctx -> toggle(ctx.getSource()))
                        .then(ClientCommands.literal("toggle").executes(ctx -> toggle(ctx.getSource())))
                        .then(ClientCommands.literal("reload").executes(ctx -> {
                            TerritoryManager.reload();
                            ctx.getSource().sendFeedback(Component.literal("Updating territories from the site..."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("war").executes(ctx -> {
                            com.cumania.territories.compat.WarMenu.toggle();
                            ctx.getSource().sendFeedback(Component.literal("War menu toggled (it shows on Xaero's World Map)"));
                            return 1;
                        }))
                        .then(ClientCommands.literal("status").executes(ctx -> {
                            var index = TerritoryManager.index();
                            ctx.getSource().sendFeedback(Component.literal(
                                    (TerritoryManager.config().enabled ? "Showing " : "Hidden: ")
                                            + index.countryCount() + " countries, " + index.provinceCount()
                                            + " provinces (" + TerritoryManager.mapName() + ", data from " + (TerritoryManager.generated().isEmpty() ? "?" : TerritoryManager.generated()) + ")"
                                            + ", you are in " + (net.minecraft.client.Minecraft.getInstance().level == null ? "?" : net.minecraft.client.Minecraft.getInstance().level.dimension())
                                            + (TerritoryManager.loadError().isEmpty() ? "" : "; last download failed: " + TerritoryManager.loadError())));
                            ctx.getSource().sendFeedback(Component.literal(warStatus(index)));
                            return 1;
                        }))));
    }

    /** What the war effects work from: the war followed, whether dynmap is being read, how many cities it matched, who holds how many. */
    private static String warStatus(com.cumania.territories.data.TerritoryIndex index) {
        var war = com.cumania.territories.data.WarMarkers.followed(index, com.cumania.territories.compat.WarMenu.viewedWarId());
        if (war == null) return "War: none running";
        var markers = com.cumania.territories.data.DynmapClient.markers();
        long age = com.cumania.territories.data.DynmapClient.markersAt() == 0 ? -1 : (System.currentTimeMillis() - com.cumania.territories.data.DynmapClient.markersAt()) / 1000;
        var colours = com.cumania.territories.data.WarMarkers.iconColours(index, com.cumania.territories.compat.WarMenu.viewedWarId());
        java.util.TreeMap<String, Integer> count = new java.util.TreeMap<>();
        colours.values().forEach(c -> count.merge(c, 1, Integer::sum));
        String sample = markers.isEmpty() ? "-" : markers.get(0).id() + " = " + markers.get(0).icon();
        return "War: " + war.name + ", live=" + war.live + ", reading dynmap=" + com.cumania.territories.data.LiveControl.isLive(war)
                + ", dynmap markers=" + markers.size() + (age < 0 ? " (never answered)" : " (" + age + "s ago)") + ", e.g. " + sample
                + "; held: " + count + "; war view on=" + (com.cumania.territories.data.WarPaint.current() != null);
    }

    private static int toggle(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        boolean on = TerritoryManager.toggle();
        source.sendFeedback(Component.literal(on ? "Territories shown" : "Territories hidden"));
        return 1;
    }
}
