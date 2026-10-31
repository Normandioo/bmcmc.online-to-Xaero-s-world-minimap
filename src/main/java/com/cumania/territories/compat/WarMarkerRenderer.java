package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.WarMarkers;
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
 * What a war puts on Xaero's World Map besides the colours (see WarMarkers): a sword on a
 * city where players of both sides are, a flag on a city just taken. Pixel art in map units like the city icons, so it keeps its size at
 * any zoom. Not clickable.
 */
public final class WarMarkerRenderer extends ElementRenderer<WarMarkers.Marker, WarMarkerRenderer.Context, WarMarkerRenderer> {

    public static final class Context {
        ResourceKey<Level> dimension;
    }

    /** How many map units each pixel of the sword and the flag takes. */
    private static final int SCALE = 2;

    public static WarMarkerRenderer create() {
        Context context = new Context();
        return new WarMarkerRenderer(context, new Provider(), new Reader());
    }

    private WarMarkerRenderer(Context context, Provider provider, Reader reader) { super(context, provider, reader); }

    private static final class Provider extends ElementRenderProvider<WarMarkers.Marker, Context> {
        private List<WarMarkers.Marker> visible = List.of();
        private int next;

        @Override public void begin(ElementRenderLocation location, Context context) {
            var config = TerritoryManager.config();
            List<WarMarkers.Marker> list = new ArrayList<>();
            if (TerritoryManager.active() && TerritoryManager.inDimension(context.dimension)) {
                for (WarMarkers.Marker m : WarMarkers.current()) {
                    boolean on = switch (m.kind()) {
                        case SWORD -> config.showSwords;
                        case FLAG -> config.showFlags;
                    };
                    if (on) list.add(m);
                }
            }
            visible = list; next = 0;
        }
        @Override public boolean hasNext(ElementRenderLocation location, Context context) { return next < visible.size(); }
        @Override public WarMarkers.Marker getNext(ElementRenderLocation location, Context context) { return visible.get(next++); }
        @Override public void end(ElementRenderLocation location, Context context) { visible = List.of(); }
    }

    private static final class Reader extends ElementReader<WarMarkers.Marker, Context, WarMarkerRenderer> {
        @Override public boolean isHidden(WarMarkers.Marker m, Context c) { return false; }
        @Override public double getRenderX(WarMarkers.Marker m, Context c, float partialTicks) { return m.x(); }
        @Override public double getRenderZ(WarMarkers.Marker m, Context c, float partialTicks) { return m.z(); }
        @Override public int getInteractionBoxLeft(WarMarkers.Marker m, Context c, float t) { return 0; }
        @Override public int getInteractionBoxRight(WarMarkers.Marker m, Context c, float t) { return 0; }
        @Override public int getInteractionBoxTop(WarMarkers.Marker m, Context c, float t) { return 0; }
        @Override public int getInteractionBoxBottom(WarMarkers.Marker m, Context c, float t) { return 0; }
        @Override public int getRenderBoxLeft(WarMarkers.Marker m, Context c, float t) { return -60; }
        @Override public int getRenderBoxRight(WarMarkers.Marker m, Context c, float t) { return 60; }
        @Override public int getRenderBoxTop(WarMarkers.Marker m, Context c, float t) { return -60; }
        @Override public int getRenderBoxBottom(WarMarkers.Marker m, Context c, float t) { return 60; }
        @Override public int getLeftSideLength(WarMarkers.Marker m, Minecraft mc) { return 0; }
        @Override public String getMenuName(WarMarkers.Marker m) { return ""; }
        @Override public String getFilterName(WarMarkers.Marker m) { return ""; }
        @Override public int getMenuTextFillLeftPadding(WarMarkers.Marker m) { return 0; }
        @Override public int getRightClickTitleBackgroundColor(WarMarkers.Marker m) { return 0; }
        @Override public boolean shouldScaleBoxWithOptionalScale() { return true; }
        @Override public boolean isInteractable(ElementRenderLocation location, WarMarkers.Marker m) { return false; }
    }

    @Override public void preRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers, boolean shadow) {
        context.dimension = info.mapDimension;
    }

    @Override public void postRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers, boolean shadow) {}

    @Override public void renderElementShadow(WarMarkers.Marker m, boolean hovered, float optionalScale, double partialX, double partialY, ElementRenderInfo info,
                                              MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers) {}

    @Override public boolean renderElement(WarMarkers.Marker m, boolean hovered, double optionalDepth, float optionalScale, double partialX, double partialY, ElementRenderInfo info,
                                           MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers) {
        long t = System.currentTimeMillis();
        switch (m.kind()) {
            case SWORD -> {
                // up and to the left of the city's icon, trembling a pixel: fighting here
                WarArt.Art art = WarArt.SWORD;
                int jitter = (t / 240) % 2 == 0 ? 0 : SCALE / 2;
                WarArt.rects(art, -art.w() * SCALE - 6 + jitter, -art.h() * SCALE - 6 - jitter, SCALE, (x0, y0, x1, y1, c) -> graphics.fill(x0, y0, x1, y1, c));
            }
            case FLAG -> {
                WarArt.Art art = WarArt.flag(m.rgb(), (int) ((t / 380) % 2));
                WarArt.rects(art, 6, -art.h() * SCALE - 6, SCALE, (x0, y0, x1, y1, c) -> graphics.fill(x0, y0, x1, y1, c));
            }
        }
        return true;
    }

    @Override public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return location == ElementRenderLocation.WORLD_MAP && !shadow;
    }

    @Override public int getOrder() { return 170; }
}
