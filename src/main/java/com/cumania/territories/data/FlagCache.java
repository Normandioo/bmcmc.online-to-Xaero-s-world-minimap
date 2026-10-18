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
 * Country flags for the info panel, downloaded from the site on demand and shrunk to a few dozen
 * pixels so they can be drawn as plain coloured rectangles (no textures involved). Nothing here touches Minecraft.
 */
public final class FlagCache {
    private FlagCache() {}

    /** A flag as w*h ARGB cells; 0 is transparent. */
    public record Flag(int w, int h, int[] argb) {}

    private static final Flag LOADING = new Flag(0, 0, new int[0]);
    public static final int MAX_W = 60, MAX_H = 36;
    private static final Map<String, Flag> flags = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** The flag at this URL, or null while it is still downloading (or could not be). Starts the download the first time. */
    public static Flag get(String url) {
        if (url == null || url.isBlank()) return null;
        Flag known = flags.get(url);
        if (known != null) return known == LOADING || known.w == 0 ? null : known;
        if (flags.putIfAbsent(url, LOADING) != null) return null;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "cumania-territories").build(), HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) throw new RuntimeException(error);
                        if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode());
                        try (InputStream in = response.body()) { flags.put(url, shrink(ImageIO.read(in))); }
                    } catch (Exception e) {
                        CumaniaTerritories.LOGGER.debug("No flag from {}: {}", url, e.toString());
                        flags.put(url, new Flag(0, 0, new int[0]));   // remembered as missing, so it is not asked for every frame
                    }
                });
        return null;
    }

    /** Averages the picture down to at most MAX_W x MAX_H cells, keeping its proportions. */
    public static Flag shrink(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("not a picture");
        double scale = Math.min(1.0, Math.min((double) MAX_W / image.getWidth(), (double) MAX_H / image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale)), h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int x0 = x * image.getWidth() / w, x1 = Math.max(x0 + 1, (x + 1) * image.getWidth() / w);
                int y0 = y * image.getHeight() / h, y1 = Math.max(y0 + 1, (y + 1) * image.getHeight() / h);
                long a = 0, r = 0, g = 0, b = 0; int n = 0;
                for (int yy = y0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++) {
                    int p = image.getRGB(xx, yy), pa = p >>> 24;
                    a += pa; r += ((p >> 16) & 255) * pa; g += ((p >> 8) & 255) * pa; b += (p & 255) * pa; n++;
                }
                if (a == 0) { out[y * w + x] = 0; continue; }
                int alpha = (int) (a / n);
                out[y * w + x] = (alpha < 128 ? 0 : 0xFF000000) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                if (alpha < 128) out[y * w + x] = 0;
            }
        }
        return new Flag(w, h, out);
    }
}
