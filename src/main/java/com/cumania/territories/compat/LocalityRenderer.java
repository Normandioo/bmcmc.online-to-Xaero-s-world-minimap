package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.IconCache;
import com.cumania.territories.data.TerritoryFile;
import com.cumania.territories.data.TerritoryIndex;
import com.cumania.territories.data.WarFront;
import com.cumania.territories.data.WarPaint;
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
                for (TerritoryFile.Locality l : TerritoryManager.index().localities()) if (scale >= ICON_FROM[rank(l)] || Focus.is(l)) list.add(l);
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
        @Override public int getRenderBoxTop(TerritoryFile.Locality l, Context c, float p) { return -Math.max(box(l), 40); }
        @Override public int getRenderBoxBottom(TerritoryFile.Locality l, Context c, float p) { return Math.max(box(l) + 14, 40); }
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
        var config = TerritoryManager.config();
        int r, o = isCapital(l) ? 2 : 1;
        IconCache.Icon icon = "simple".equalsIgnoreCase(config.markerStyle) ? null : IconCache.get(config.markerIconUrl, l.iconKind != null ? l.iconKind : l.kind);
        if (icon != null) {
            // the site's own marker, centred on the city
            int half = icon.size() / 2;
            for (IconCache.Rect rect : icon.rects()) graphics.fill(rect.x() - half, rect.y() - half, rect.x() + rect.w() - half, rect.y() + rect.h() - half, rect.argb());
            if (hovered) frame(graphics, half + 1, 1, 0xFFFFD23C);
            r = half - o;
        } else {
            r = RADIUS[rank] + (hovered ? 1 : 0);
            int country = index.countryIndex(l.country);
            int rgb = country < 0 ? 0xCCCCCC : index.countryRgb(country);
            int outline = isCapital(l) ? 0xFFFFFFFF : 0xFF101010;
            graphics.fill(-r - o, -r - o, r + o, r + o, outline);
            graphics.fill(-r, -r, r, r, 0xFF000000 | rgb);
            if (rank >= 3) graphics.fill(-1, -1, 1, 1, 0xFFFFFFFF);   // towns and up get a pip
        }

        boolean focus = Focus.is(l);
        if (focus) drawFocus(graphics, r + o);
        else if (WarPaint.current() != null && TerritoryManager.config().showInPlayRings) {
            Integer play = WarFront.playRgb(l);
            if (play != null) drawInPlay(graphics, r + o, play);
        }

        if (TerritoryManager.config().showLocalityNames && (hovered || focus || effectiveScale(context) >= NAME_FROM[rank])) {
            var font = Minecraft.getInstance().font;
            int w = font.width(l.name);
            graphics.drawString(font, l.name, -w / 2, r + o + 2, 0xFFFFFFFF, true);
        }
        return true;
    }

    /** An In Play city: two rings in its holder's colour that swell and fade, over and over (a calmer cousin of the focus effect). */
    private static void drawInPlay(MapElementGraphics g, int base, int rgb) {
        long period = 1600;
        float t0 = (System.currentTimeMillis() % period) / (float) period;
        frame(g, base + 2, 1, argb(230, rgb));
        for (int ring = 0; ring < 2; ring++) {
            float t = (t0 + ring * 0.5f) % 1f;
            int alpha = (int) (210 * (1f - t));
            if (alpha > 4) frame(g, base + 3 + (int) (t * 14), 2, argb(alpha, rgb));
        }
    }

    /** A steady gold frame plus two rings that grow outwards and fade, repeating while the focus lasts. */
    private static void drawFocus(MapElementGraphics g, int base) {
        float strength = Focus.strength();
        frame(g, base + 3, 2, argb((int) (255 * strength), 0xFFD23C));
        for (int ring = 0; ring < 2; ring++) {
            float t = (Focus.pulse() + ring * 0.5f) % 1f;
            int radius = base + 4 + (int) (t * 22);
            int alpha = (int) (200 * (1f - t) * strength);
            if (alpha > 4) frame(g, radius, 2, argb(alpha, 0xFFFFFF));
        }
    }

    private static int argb(int alpha, int rgb) { return (Math.max(0, Math.min(255, alpha)) << 24) | rgb; }

    /** A hollow square of the given half-size and thickness around (0, 0). */
    private static void frame(MapElementGraphics g, int half, int thick, int color) {
        g.fill(-half, -half, half, -half + thick, color);
        g.fill(-half, half - thick, half, half, color);
        g.fill(-half, -half + thick, -half + thick, half - thick, color);
        g.fill(half - thick, -half + thick, half, half - thick, color);
    }

    @Override public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return location == ElementRenderLocation.WORLD_MAP && !shadow;
    }

    @Override public int getOrder() { return 160; }
}
