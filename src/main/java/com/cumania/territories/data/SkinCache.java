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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player skins for the player card, downloaded from the site on demand and turned into a front view
 * (16x32 coloured cells: head, body, arms, legs, outer layers included) drawn as plain rectangles.
 * Nothing here touches Minecraft.
 */
public final class SkinCache {
    private SkinCache() {}

    public static final int W = 16, H = 32;
    /** A front view as W*H ARGB cells; 0 is transparent. */
    public record Figure(int[] argb) {}

    private static final Figure LOADING = new Figure(new int[0]);
    private static final Map<String, Figure> figures = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** The figure for the skin at this URL, or null while it is downloading (or when it could not be had). */
    public static Figure get(String url) {
        if (url == null || url.isBlank()) return null;
        Figure known = figures.get(url);
        if (known != null) return known == LOADING || known.argb.length == 0 ? null : known;
        if (figures.putIfAbsent(url, LOADING) != null) return null;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "cumania-territories").build(), HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) throw new RuntimeException(error);
                        if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode());
                        try (InputStream in = response.body()) { figures.put(url, front(ImageIO.read(in))); }
                    } catch (Exception e) {
                        CumaniaTerritories.LOGGER.debug("No skin from {}: {}", url, e.toString());
                        figures.put(url, new Figure(new int[0]));
                    }
                });
        return null;
    }

    /** Builds the front view from a 64x64 (or old 64x32) skin texture. */
    public static Figure front(BufferedImage skin) {
        if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32)) throw new IllegalArgumentException("not a skin");
        boolean modern = skin.getHeight() == 64;
        int[] out = new int[W * H];
        // base layer first, then the outer layer over it (the hat is on old skins too)
        part(skin, out, 8, 8, 8, 8, 4, 0);                         // head
        part(skin, out, 20, 20, 8, 12, 4, 8);                      // body
        part(skin, out, 44, 20, 4, 12, 0, 8);                      // right arm (on the viewer's left)
        part(skin, out, 4, 20, 4, 12, 4, 20);                      // right leg
        if (modern) {
            part(skin, out, 36, 52, 4, 12, 12, 8);                // left arm
            part(skin, out, 20, 52, 4, 12, 8, 20);                // left leg
        } else {
            part(skin, out, 44, 20, 4, 12, 12, 8, true);          // old skins reuse the right limbs, mirrored
            part(skin, out, 4, 20, 4, 12, 8, 20, true);
        }
        part(skin, out, 40, 8, 8, 8, 4, 0);                        // hat
        if (modern) {
            part(skin, out, 20, 36, 8, 12, 4, 8);                  // jacket
            part(skin, out, 44, 36, 4, 12, 0, 8);                  // right sleeve
            part(skin, out, 4, 36, 4, 12, 4, 20);                  // right trouser leg
            part(skin, out, 52, 52, 4, 12, 12, 8);                 // left sleeve
            part(skin, out, 4, 52, 4, 12, 8, 20);                  // left trouser leg
        }
        return new Figure(out);
    }

    private static void part(BufferedImage skin, int[] out, int sx, int sy, int w, int h, int dx, int dy) { part(skin, out, sx, sy, w, h, dx, dy, false); }

    private static void part(BufferedImage skin, int[] out, int sx, int sy, int w, int h, int dx, int dy, boolean mirror) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = skin.getRGB(sx + (mirror ? w - 1 - x : x), sy + y);
            if ((p >>> 24) < 128) continue;   // transparent pixels leave what is underneath
            out[(dy + y) * W + dx + x] = 0xFF000000 | (p & 0xFFFFFF);
        }
    }
}
