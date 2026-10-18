package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.DynmapClient;
import com.cumania.territories.data.FaceCache;
import com.cumania.territories.data.FlagCache;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The players who are online right now (from the site's dynmap), drawn on Xaero's World Map as map elements, the same mechanism as the cities:
 * their face (the same picture the site uses), the flag of their nation beside it and the name the site gives them. Players the game can
 * see are placed from the game itself and everyone glides between dynmap's answers (see PlayerTracker). Click one to open that player's card.
 */
public final class PlayerRenderer extends ElementRenderer<DynmapClient.Online, PlayerRenderer.Context, PlayerRenderer> {

    public static final class Context {
        ResourceKey<Level> dimension;
        double scale = 1;
    }

    private static final double NAME_FROM = 0.12;   // zoom above which names show without hovering
    private static final int HEAD = FaceCache.SIZE;   // the face: one map unit per skin pixel
    private static final int FLAG_W = 13;             // the nation's flag beside it

    /** A flag taken down to the few cells it is drawn with. */
    private record Small(int w, int h, int[] argb) {}
    private static final Map<String, Small> smallFlags = new HashMap<>();

    /** The flag of the player's nation, small, or null when they have none (or it is still downloading). */
    private static Small flagOf(DynmapClient.Online p) {
        var index = TerritoryManager.index();
        int nation = index.nationOfPlayer(p.siteName());
        if (nation < 0) return null;
        var info = index.countryData(nation).info;
        String rel = info == null ? null : info.get("flag"), base = TerritoryManager.config().url;
        if (rel == null || rel.isBlank() || base == null || !base.contains("/")) return null;
        String url = base.substring(0, base.lastIndexOf('/') + 1) + rel;
        Small known = smallFlags.get(url);
        if (known != null) return known;
        FlagCache.Flag flag = FlagCache.get(url);
        if (flag == null) return null;
        int h = Math.max(6, Math.min(12, Math.round(FLAG_W * (float) flag.h() / flag.w())));
        int[] out = new int[FLAG_W * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < FLAG_W; x++) {
            int sx = Math.min(flag.w() - 1, (2 * x + 1) * flag.w() / (2 * FLAG_W)), sy = Math.min(flag.h() - 1, (2 * y + 1) * flag.h() / (2 * h));
            out[y * FLAG_W + x] = flag.argb()[sy * flag.w() + sx];
        }
        Small small = new Small(FLAG_W, h, out);
        smallFlags.put(url, small);
        return small;
    }

    /** Rows of coloured cells as rectangles: a run of the same colour along a row is one. */
    private static void cells(MapElementGraphics g, int[] argb, int w, int h, int x0, int y0) {
        for (int row = 0; row < h; row++) {
            int col = 0;
            while (col < w) {
                int color = argb[row * w + col], end = col + 1;
                while (end < w && argb[row * w + end] == color) end++;
                if (color != 0) g.fill(x0 + col, y0 + row, x0 + end, y0 + row + 1, color);
                col = end;
            }
        }
    }

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
            PlayerTracker.update(list);
            visible = list; next = 0;
        }
        @Override public boolean hasNext(ElementRenderLocation location, Context context) { return next < visible.size(); }
        @Override public DynmapClient.Online getNext(ElementRenderLocation location, Context context) { return visible.get(next++); }
        @Override public void end(ElementRenderLocation location, Context context) { visible = List.of(); }
    }

    private static final class Reader extends ElementReader<DynmapClient.Online, Context, PlayerRenderer> {
        @Override public boolean isHidden(DynmapClient.Online p, Context c) { return false; }
        @Override public double getRenderX(DynmapClient.Online p, Context c, float partialTicks) { return PlayerTracker.x(p); }
        @Override public double getRenderZ(DynmapClient.Online p, Context c, float partialTicks) { return PlayerTracker.z(p); }
        @Override public int getInteractionBoxLeft(DynmapClient.Online p, Context c, float t) { return -HEAD / 2 - 2; }
        @Override public int getInteractionBoxRight(DynmapClient.Online p, Context c, float t) { return HEAD / 2 + 3 + FLAG_W + 2; }
        @Override public int getInteractionBoxTop(DynmapClient.Online p, Context c, float t) { return -HEAD / 2 - 2; }
        @Override public int getInteractionBoxBottom(DynmapClient.Online p, Context c, float t) { return HEAD / 2 + 2; }
        @Override public int getRenderBoxLeft(DynmapClient.Online p, Context c, float t) { return -90; }
        @Override public int getRenderBoxRight(DynmapClient.Online p, Context c, float t) { return 90; }
        @Override public int getRenderBoxTop(DynmapClient.Online p, Context c, float t) { return -HEAD / 2 - 3; }
        @Override public int getRenderBoxBottom(DynmapClient.Online p, Context c, float t) { return HEAD / 2 + 18; }
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
        int half = HEAD / 2;
        graphics.fill(-half - 1, -half - 1, half + 1, half + 1, hovered ? 0xFFFFD400 : 0xFFFFFFFF);   // the ring, as on the site
        int[] face = FaceCache.get(DynmapClient.faceUrl(p.account()));
        if (face != null) cells(graphics, face, FaceCache.SIZE, FaceCache.SIZE, -half, -half);
        else graphics.fill(-half, -half, half, half, 0xFF3A7D44);
        Small flag = flagOf(p);
        if (flag != null) {
            int fx = half + 3, fy = -flag.h() / 2;
            graphics.fill(fx - 1, fy - 1, fx + flag.w() + 1, fy + flag.h() + 1, 0xFF101010);
            cells(graphics, flag.argb(), flag.w(), flag.h(), fx, fy);
        }
        if (hovered || context.scale >= NAME_FROM) {
            var font = Minecraft.getInstance().font;
            String name = p.siteName();
            int w = font.width(name);
            graphics.drawString(font, name, -w / 2, half + 4, 0xFFC8FFC8, true);
        }
        return true;
    }

    @Override public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
        return location == ElementRenderLocation.WORLD_MAP && !shadow;
    }

    @Override public int getOrder() { return 170; }
}
