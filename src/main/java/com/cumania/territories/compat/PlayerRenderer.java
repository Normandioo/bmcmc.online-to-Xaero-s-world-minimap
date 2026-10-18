package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.DynmapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.element.MapElementGraphics;
import xaero.map.element.render.ElementReader;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.element.render.ElementRenderProvider;
import xaero.map.element.render.ElementRenderer;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * The players who are online right now (from the site's dynmap), drawn on Xaero's World Map as map elements, the same mechanism as the cities:
 * a green dot and the name the site gives them. Click one to open that player's card.
 */
public final class PlayerRenderer extends ElementRenderer<DynmapClient.Online, PlayerRenderer.Context, PlayerRenderer> {

    public static final class Context {
        ResourceKey<Level> dimension;
        double scale = 1;
    }

    private static final double NAME_FROM = 0.12;   // zoom above which names show without hovering

    public static PlayerRenderer create() {
        Context context = new Context();
        return new PlayerRenderer(context, new Provider(), new Reader());
    }

    private PlayerRenderer(Context context, Provider provider, Reader reader) {
        super(context, provider, reader);
    }

    private static final class Provider extends ElementRenderProvider<DynmapClient.Online, Context> {
        private List<DynmapClient.Online> visible = List.of();
        private int next;

        @Override public void begin(ElementRenderLocation location, Context context) {
            var config = TerritoryManager.config();
            List<DynmapClient.Online> list = new ArrayList<>();
            if (config.showOnlinePlayers && context.dimension == TerritoryManager.dimension()) {
                DynmapClient.wantPlayers();   // the map is open: keep the list fresh
                if (DynmapClient.playersKnown()) for (var p : DynmapClient.players()) if (p.world().equalsIgnoreCase(config.dynmapWorld)) list.add(p);
            }
            visible = list; next = 0;
        }
        @Override public boolean hasNext(ElementRenderLocation location, Context context) { return next < visible.size(); }
        @Override public DynmapClient.Online getNext(ElementRenderLocation location, Context context) { return visible.get(next++); }
        @Override public void end(ElementRenderLocation location, Context context) { visible = List.of(); }
    }

    private static final class Reader extends ElementReader<DynmapClient.Online, Context, PlayerRenderer> {
        @Override public boolean isHidden(DynmapClient.Online p, Context c) { return false; }
        @Override public double getRenderX(DynmapClient.Online p, Context c, float partialTicks) { return p.x(); }
        @Override public double getRenderZ(DynmapClient.Online p, Context c, float partialTicks) { return p.z(); }
        @Override public int getInteractionBoxLeft(DynmapClient.Online p, Context c, float t) { return -6; }
        @Override public int getInteractionBoxRight(DynmapClient.Online p, Context c, float t) { return 6; }
        @Override public int getInteractionBoxTop(DynmapClient.Online p, Context c, float t) { return -6; }
        @Override public int getInteractionBoxBottom(DynmapClient.Online p, Context c, float t) { return 6; }
        @Override public int getRenderBoxLeft(DynmapClient.Online p, Context c, float t) { return -90; }
        @Override public int getRenderBoxRight(DynmapClient.Online p, Context c, float t) { return 90; }
        @Override public int getRenderBoxTop(DynmapClient.Online p, Context c, float t) { return -8; }
        @Override public int getRenderBoxBottom(DynmapClient.Online p, Context c, float t) { return 22; }
        @Override public int getLeftSideLength(DynmapClient.Online p, Minecraft mc) { return 0; }
        @Override public String getMenuName(DynmapClient.Online p) { return p.siteName(); }
        @Override public String getFilterName(DynmapClient.Online p) { return p.siteName(); }
        @Override public int getMenuTextFillLeftPadding(DynmapClient.Online p) { return 0; }
        @Override public int getRightClickTitleBackgroundColor(DynmapClient.Online p) { return 0; }
        @Override public boolean shouldScaleBoxWithOptionalScale() { return true; }
        @Override public boolean isInteractable(ElementRenderLocation location, DynmapClient.Online p) { return true; }
    }

    @Override public void preRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers, boolean shadow) {
        context.dimension = info.mapDimension;
        context.scale = info.scale;
    }

    @Override public void postRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers, boolean shadow) {}

    @Override public void renderElementShadow(DynmapClient.Online p, boolean hovered, float optionalScale, double partialX, double partialY, ElementRenderInfo info,
                                              MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers) {}

    @Override public boolean renderElement(DynmapClient.Online p, boolean hovered, double optionalDepth, float optionalScale, double partialX, double partialY, ElementRenderInfo info,
                                           MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers) {
        int r = hovered ? 4 : 3;
        graphics.fill(-r - 1, -r - 1, r + 1, r + 1, 0xFF101010);
        graphics.fill(-r, -r, r, r, hovered ? 0xFF9BFF9B : 0xFF3CD65A);
        if (hovered || context.scale >= NAME_FROM) {
            var font = Minecraft.getInstance().font;
            String name = p.siteName();
            int w = font.width(name);
            graphics.drawString(font, name, -w / 2, r + 4, 0xFFC8FFC8, true);
        }
        return true;
    }

    @Override public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return location == ElementRenderLocation.WORLD_MAP && !shadow;
    }

    @Override public int getOrder() { return 170; }
}
