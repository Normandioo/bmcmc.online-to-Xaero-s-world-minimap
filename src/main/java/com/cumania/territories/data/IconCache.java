package com.cumania.territories.data;

import com.cumania.territories.CumaniaTerritories;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The site's city markers (icons/Small.png, icons/LargeCapital.png ...), downloaded on demand and shrunk to {@link #SIZE} pixels.
 * Each icon is kept as a short list of coloured rectangles, so drawing it takes a handful of plain fills and no textures.
 */
public final class IconCache {
    private IconCache() {}

    public static final int SIZE = 18;

    public record Rect(int x, int y, int w, int h, int argb) {}
    public record Icon(int size, List<Rect> rects) {}

    private static final Icon LOADING = new Icon(0, List.of()), MISSING = new Icon(0, List.of());
    private static final Map<String, Icon> icons = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** The icon for this marker kind (as exported: "Small", "LargeCapital", "Fort"...), or null while it downloads or if the site has none. */
    public static Icon get(String baseUrl, String kind) {
        if (baseUrl == null || baseUrl.isBlank() || kind == null || !kind.matches("[A-Za-z]+")) return null;
        return fetch((baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + kind + ".png", SIZE);
    }

    /** Any picture of the site, shrunk to size x size pixels; null while it downloads. */
    public static Icon fetch(String url, int size) {
        String key = size + "|" + url;
        Icon known = icons.get(key);
        if (known != null) return known == LOADING || known == MISSING ? null : known;
        if (icons.putIfAbsent(key, LOADING) != null) return null;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "cumania-territories").build(), HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) throw new RuntimeException(error);
                        if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode());
                        try (InputStream in = response.body()) { icons.put(key, build(ImageIO.read(in), size)); }
                    } catch (Exception e) {
                        CumaniaTerritories.LOGGER.debug("No marker icon from {}: {}", url, e.toString());
                        icons.put(key, MISSING);
                    }
                });
        return null;
    }

    /** Forget everything (the site's icons may have changed). */
    public static void clear() { icons.clear(); }

    static Icon build(BufferedImage image, int SIZE) {
        if (image == null) throw new IllegalArgumentException("not a picture");
        int[] px = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int x0 = x * image.getWidth() / SIZE, x1 = Math.max(x0 + 1, (x + 1) * image.getWidth() / SIZE);
                int y0 = y * image.getHeight() / SIZE, y1 = Math.max(y0 + 1, (y + 1) * image.getHeight() / SIZE);
                long a = 0, r = 0, g = 0, b = 0; int n = 0;
                for (int yy = y0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++) {
                    int p = image.getRGB(xx, yy), pa = p >>> 24;
                    a += pa; r += ((p >> 16) & 255) * pa; g += ((p >> 8) & 255) * pa; b += (p & 255) * pa; n++;
                }
                if (a == 0 || a / n < 110) continue;
                px[y * SIZE + x] = 0xFF000000 | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
            }
        }
        // rows of equal colour, then identical neighbouring rows merged into taller rectangles
        List<Rect> rects = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            int x = 0;
            while (x < SIZE) {
                int color = px[y * SIZE + x];
                if (color == 0) { x++; continue; }
                int end = x + 1;
                while (end < SIZE && px[y * SIZE + end] == color) end++;
                                boolean merged = false;
                for (int i = rects.size() - 1; i >= 0 && !merged; i--) {
                    Rect r = rects.get(i);
                    if (r.y + r.h == y && r.x == x && r.w == end - x && r.argb == color) { rects.set(i, new Rect(r.x, r.y, r.w, r.h + 1, color)); merged = true; }
                    else if (r.y + r.h < y) break;
                }
                if (!merged) rects.add(new Rect(x, y, end - x, 1, color));
                x = end;
            }
        }
        return new Icon(SIZE, List.copyOf(rects));
    }
}
