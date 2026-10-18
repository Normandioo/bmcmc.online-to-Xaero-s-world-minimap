package com.cumania.territories.compat;

import com.cumania.territories.TerritoryManager;
import com.cumania.territories.data.TerritoryIndex;
import com.cumania.territories.data.WarPaint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The war and alliance colours, drawn by us over Xaero's World Map instead of asking Xaero's to repaint every region (which takes seconds):
 * the countries are cut into small cells, the cells of one colour are merged into rectangles, and each frame the rectangles in view are filled.
 * Changing the view only rebuilds that short list, so it is instant.
 *
 * It needs to know where a block is on screen, which it works out from Xaero's camera; every frame it checks that against the block Xaero's
 * says is under the mouse, and if the two ever disagree it switches itself off and the old (slow) way takes over.
 */
public final class WarOverlay {
    private WarOverlay() {}

    private static final int CELL = 4;
    private record Rect(int x0, int z0, int x1, int z1, int argb) {}   // in blocks, x1/z1 exclusive

    private static volatile Rect[] rects = new Rect[0];
    private static volatile boolean broken;
    private static int misses, lastMouseX, lastMouseY;
    private static double lastCamX, lastCamZ, lastScale;

    /** Whether the overlay is in charge of the war colours (otherwise Xaero's paints them). */
    public static boolean handles() { return TerritoryManager.config().warOverlay && !broken; }

    public static void clear() { rects = new Rect[0]; }

    /** Cuts the paint into rectangles. Fast enough to do on the spot. */
    public static void set(TerritoryIndex index, WarPaint.Paint paint) {
        if (paint == null || index.isEmpty()) { clear(); return; }
        int[] b = index.blockBounds();
        int minX = b[0], minZ = b[1], w = (b[2] + CELL - 1) / CELL, h = (b[3] + CELL - 1) / CELL;
        boolean grey = TerritoryManager.config().warGreyOthers;
        int[] colour = new int[w * h];   // ARGB, 0 = nothing
        for (int cz = 0; cz < h; cz++) {
            for (int cx = 0; cx < w; cx++) {
                int country = index.countryAtBlock(minX + cx * CELL + CELL / 2, minZ + cz * CELL + CELL / 2);
                if (country < 0) continue;
                int rgb = country < paint.countryRgb().length ? paint.countryRgb()[country] : -1;
                if (rgb >= 0) colour[cz * w + cx] = (0xB4 << 24) | rgb;
                else if (grey) colour[cz * w + cx] = (0xA8 << 24) | WarPaint.NEUTRAL;
            }
        }
        // captured provinces take their captor's colour
        for (Map.Entry<Integer, Integer> e : paint.provinceRgb().entrySet()) {
            int[] box = paint.provinceBounds().get(e.getKey());
            if (box == null) continue;
            for (int cz = Math.max(0, (box[1] - minZ) / CELL); cz <= Math.min(h - 1, (box[3] - minZ) / CELL); cz++) {
                for (int cx = Math.max(0, (box[0] - minX) / CELL); cx <= Math.min(w - 1, (box[2] - minX) / CELL); cx++) {
                    if (index.provinceAtBlock(minX + cx * CELL + CELL / 2, minZ + cz * CELL + CELL / 2) == e.getKey())
                        colour[cz * w + cx] = (0xD2 << 24) | e.getValue();
                }
            }
        }
        // runs along each row, then identical runs on consecutive rows merged
        List<Rect> out = new ArrayList<>();
        Map<Long, int[]> open = new HashMap<>();   // (x0, x1, colour) -> {index in out}
        Map<Long, int[]> next = new HashMap<>();
        for (int cz = 0; cz < h; cz++) {
            next.clear();
            int cx = 0;
            while (cx < w) {
                int c = colour[cz * w + cx];
                if (c == 0) { cx++; continue; }
                int end = cx + 1;
                while (end < w && colour[cz * w + end] == c) end++;
                long key = ((long) cx << 40) ^ ((long) end << 20) ^ (c & 0xFFFFFFFFL) * 0x9E3779B1L;
                int[] above = open.get(key);
                Rect r;
                if (above != null && out.get(above[0]).argb == c && out.get(above[0]).x0 == minX + cx * CELL && out.get(above[0]).x1 == minX + end * CELL
                        && out.get(above[0]).z1 == minZ + cz * CELL) {
                    Rect old = out.get(above[0]);
                    out.set(above[0], new Rect(old.x0, old.z0, old.x1, minZ + (cz + 1) * CELL, c));
                    next.put(key, above);
                } else {
                    out.add(new Rect(minX + cx * CELL, minZ + cz * CELL, minX + end * CELL, minZ + (cz + 1) * CELL, c));
                    next.put(key, new int[]{out.size() - 1});
                }
                cx = end;
            }
            Map<Long, int[]> t = open; open = next; next = t;
        }
        rects = out.toArray(new Rect[0]);
    }

    /** Draws the rectangles in view. Call after the map has rendered, before our panels. */
    public static void render(GuiGraphics g, int mouseX, int mouseY, CameraView view) {
        Rect[] list = rects;
        if (list.length == 0 || !handles()) return;
        Minecraft mc = Minecraft.getInstance();
        double scale = view.cumania$scale(), gui = view.cumania$screenScale();
        if (scale <= 0 || gui <= 0) return;
        double realW = mc.getWindow().getWidth(), realH = mc.getWindow().getHeight();
        double camX = view.cumania$cameraX(), camZ = view.cumania$cameraZ();

        // is our idea of where blocks are the same as Xaero's? (block under the mouse, as Xaero's sees it vs as we compute it)
        double bx = camX + (mouseX * gui - realW / 2) / scale, bz = camZ + (mouseY * gui - realH / 2) / scale;
        double tolerance = Math.max(4, 3 * gui / scale);
        boolean steady = mouseX == lastMouseX && mouseY == lastMouseY && camX == lastCamX && camZ == lastCamZ && scale == lastScale;   // Xaero's number is up to date
        lastMouseX = mouseX; lastMouseY = mouseY; lastCamX = camX; lastCamZ = camZ; lastScale = scale;
        boolean off = Math.abs(bx - view.cumania$mouseBlockX()) > tolerance || Math.abs(bz - view.cumania$mouseBlockZ()) > tolerance;
        if (!off) misses = 0;
        else if (steady && ++misses >= 20) {
            broken = true;   // never mind: hand the colours back to Xaero's (slower, but right)
            clear();
            com.cumania.territories.TerritoryManager.repaint();
            com.cumania.territories.CumaniaTerritories.LOGGER.warn("War overlay disabled: it does not line up with Xaero's map");
            return;
        }

        int guiW = mc.getWindow().getGuiScaledWidth(), guiH = mc.getWindow().getGuiScaledHeight();
        double k = scale / gui;
        double ox = realW / 2 / gui, oz = realH / 2 / gui;
        for (Rect r : list) {
            int sx0 = (int) Math.floor((r.x0 - camX) * k + ox), sx1 = (int) Math.ceil((r.x1 - camX) * k + ox);
            int sz0 = (int) Math.floor((r.z0 - camZ) * k + oz), sz1 = (int) Math.ceil((r.z1 - camZ) * k + oz);
            if (sx1 <= 0 || sz1 <= 0 || sx0 >= guiW || sz0 >= guiH) continue;
            g.fill(Math.max(0, sx0), Math.max(0, sz0), Math.min(guiW, sx1), Math.min(guiH, sz1), r.argb);
        }
    }
}
