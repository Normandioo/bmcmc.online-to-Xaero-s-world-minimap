package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.TerritoryIndex;
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
 * Draws the site's cities on Xaero's World Map as map elements, the same mechanism Xaero's uses for waypoints
 * and player heads: Xaero's positions and culls them, we only draw a marker and a name at (0, 0).
 */
public final class LocalityRenderer extends ElementRenderer<TerritoryFile.Locality, LocalityRenderer.Context, LocalityRenderer> {

    /** Per-frame state shared by the provider, reader and renderer. */
    public static final class Context {
        ResourceKey<Level> dimension;
        double scale = 1;
    }

    // development -> rank; the zoom (blocks per pixel's inverse) above which the marker / the name appears
    private static final double[] ICON_FROM = {0.5, 0.2, 0.15, 0.08, 0, 0};
    private static final double[] NAME_FROM = {1.5, 0.5, 0.4, 0.25, 0.12, 0.05};
    private static final int[] RADIUS = {2, 3, 3, 4, 5, 6};

    public static LocalityRenderer create() {
        Context context = new Context();
        return new LocalityRenderer(context, new Provider(), new Reader());
    }

    private LocalityRenderer(Context context, Provider provider, Reader reader) {
        super(context, provider, reader);
    }

    static int rank(TerritoryFile.Locality l) {
        if (l.development == null) return 0;
        return switch (l.development) {
            case "Village" -> 1;
            case "Hub" -> 2;
            case "Town" -> 3;
            case "City" -> 4;
            case "Metropolis" -> 5;
            default -> 0;
        };
    }

    static boolean isCapital(TerritoryFile.Locality l) { return l.kind != null && l.kind.endsWith("Capital"); }

    static double effectiveScale(Context c) { return c.scale * Math.max(0.05, TerritoryManager.config().localityZoomBias); }

    // ---- provider: which cities to draw this frame ----

    private static final class Provider extends ElementRenderProvider<TerritoryFile.Locality, Context> {
        private List<TerritoryFile.Locality> visible = List.of();
        private int next;

        @Override public void begin(ElementRenderLocation location, Context context) {
            var config = TerritoryManager.config();
            List<TerritoryFile.Locality> list = new ArrayList<>();
            if (TerritoryManager.active() && config.showLocalities && context.dimension == TerritoryManager.dimension()) {
                double scale = effectiveScale(context);
                for (TerritoryFile.Locality l : TerritoryManager.index().localities()) if (scale >= ICON_FROM[rank(l)]) list.add(l);
            }
            visible = list; next = 0;
        }
        @Override public boolean hasNext(ElementRenderLocation location, Context context) { return next < visible.size(); }
        @Override public TerritoryFile.Locality getNext(ElementRenderLocation location, Context context) { return visible.get(next++); }
        @Override public void end(ElementRenderLocation location, Context context) { visible = List.of(); }
    }

    // ---- reader: where each city is and how big its boxes are ----

    private static final class Reader extends ElementReader<TerritoryFile.Locality, Context, LocalityRenderer> {
        private static int box(TerritoryFile.Locality l) { return RADIUS[rank(l)] + (isCapital(l) ? 2 : 1) + 2; }

        @Override public boolean isHidden(TerritoryFile.Locality l, Context c) { return false; }
        @Override public double getRenderX(TerritoryFile.Locality l, Context c, float partialTicks) { return l.x + 0.5; }
        @Override public double getRenderZ(TerritoryFile.Locality l, Context c, float partialTicks) { return l.z + 0.5; }
        @Override public int getInteractionBoxLeft(TerritoryFile.Locality l, Context c, float p) { return -box(l); }
        @Override public int getInteractionBoxRight(TerritoryFile.Locality l, Context c, float p) { return box(l); }
        @Override public int getInteractionBoxTop(TerritoryFile.Locality l, Context c, float p) { return -box(l); }
        @Override public int getInteractionBoxBottom(TerritoryFile.Locality l, Context c, float p) { return box(l); }
        @Override public int getRenderBoxLeft(TerritoryFile.Locality l, Context c, float p) { return -90; }    // wide enough for the name
        @Override public int getRenderBoxRight(TerritoryFile.Locality l, Context c, float p) { return 90; }
        @Override public int getRenderBoxTop(TerritoryFile.Locality l, Context c, float p) { return -box(l); }
        @Override public int getRenderBoxBottom(TerritoryFile.Locality l, Context c, float p) { return box(l) + 14; }
        @Override public int getLeftSideLength(TerritoryFile.Locality l, Minecraft mc) { return 0; }
        @Override public String getMenuName(TerritoryFile.Locality l) { return l.name; }
        @Override public String getFilterName(TerritoryFile.Locality l) { return l.name; }
        @Override public int getMenuTextFillLeftPadding(TerritoryFile.Locality l) { return 0; }
        @Override public int getRightClickTitleBackgroundColor(TerritoryFile.Locality l) { return 0; }
        @Override public boolean shouldScaleBoxWithOptionalScale() { return true; }
        @Override public boolean isInteractable(ElementRenderLocation location, TerritoryFile.Locality l) { return true; }
    }

    // ---- renderer ----

    @Override public void preRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers, boolean shadow) {
        context.dimension = info.mapDimension;
        context.scale = info.scale;
    }

    @Override public void postRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers, boolean shadow) {}

    @Override public void renderElementShadow(TerritoryFile.Locality l, boolean hovered, float optionalScale, double partialX, double partialY, ElementRenderInfo info,
                                              MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers) {}

    @Override public boolean renderElement(TerritoryFile.Locality l, boolean hovered, double optionalDepth, float optionalScale, double partialX, double partialY, ElementRenderInfo info,
                                           MapElementGraphics graphics, MultiBufferSource.BufferSource buffers, MultiTextureRenderTypeRendererProvider providers) {
        TerritoryIndex index = TerritoryManager.index();
        int rank = rank(l);
        int r = RADIUS[rank] + (hovered ? 1 : 0);
        int country = index.countryIndex(l.country);
        int rgb = country < 0 ? 0xCCCCCC : index.countryRgb(country);
        int outline = isCapital(l) ? 0xFFFFFFFF : 0xFF101010;
        int o = isCapital(l) ? 2 : 1;
        graphics.fill(-r - o, -r - o, r + o, r + o, outline);
        graphics.fill(-r, -r, r, r, 0xFF000000 | rgb);
        if (rank >= 3) graphics.fill(-1, -1, 1, 1, 0xFFFFFFFF);   // towns and up get a pip

        if (TerritoryManager.config().showLocalityNames && (hovered || effectiveScale(context) >= NAME_FROM[rank])) {
            var font = Minecraft.getInstance().font;
            int w = font.width(l.name);
            graphics.drawString(font, l.name, -w / 2, r + o + 2, 0xFFFFFFFF, true);
        }
        return true;
    }

    @Override public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return location == ElementRenderLocation.WORLD_MAP && !shadow;
    }

    @Override public int getOrder() { return 160; }
}
