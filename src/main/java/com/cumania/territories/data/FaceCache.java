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
 * Players' faces for the map, the same pictures the site shows (dynmap's tiles/faces/32x32/&lt;account&gt;.png), taken down to the 8x8
 * skin pixels they are made of, so they can be drawn as plain coloured squares. Nothing here touches Minecraft.
 */
public final class FaceCache {
    private FaceCache() {}

    public static final int SIZE = 8;
    private static final int[] LOADING = new int[0];
    private static final Map<String, int[]> faces = new ConcurrentHashMap<>();
    private static final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    /** The face at this URL as 8x8 ARGB cells (row by row), or null while it downloads (or could not be had; asked again after a minute). */
    public static int[] get(String url) {
        if (url == null || url.isBlank()) return null;
        int[] known = faces.get(url);
        if (known != null) {
            if (known == LOADING) return null;
            if (known.length == 0) {
                Long when = failedAt.get(url);
                if (when != null && System.currentTimeMillis() - when > 60_000 && faces.replace(url, known, LOADING)) fetch(url);
                return null;
            }
            return known;
        }
        if (faces.putIfAbsent(url, LOADING) != null) return null;
        fetch(url);
        return null;
    }

    private static void fetch(String url) {
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("User-Agent", "cumania-territories").build(), HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete((response, error) -> {
                    try {
                        if (error != null) throw new RuntimeException(error);
                        if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode());
                        try (InputStream in = response.body()) { faces.put(url, shrink(ImageIO.read(in))); }
                    } catch (Exception e) {
                        CumaniaTerritories.LOGGER.debug("No face from {}: {}", url, e.toString());
                        failedAt.put(url, System.currentTimeMillis());
                        faces.put(url, new int[0]);
                    }
                });
    }

    /** One pixel for each 8x8 skin pixel (the middle one of the block it was blown up from), whatever size the picture is. */
    public static int[] shrink(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("not a picture");
        int[] out = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int sx = Math.min(image.getWidth() - 1, (2 * x + 1) * image.getWidth() / (2 * SIZE));
            int sy = Math.min(image.getHeight() - 1, (2 * y + 1) * image.getHeight() / (2 * SIZE));
            int p = image.getRGB(sx, sy);
            out[y * SIZE + x] = (p >>> 24) < 128 ? 0xFF3A7D44 : 0xFF000000 | (p & 0xFFFFFF);
        }
        return out;
    }
}
