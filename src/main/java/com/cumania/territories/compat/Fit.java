package com.cumania.territories.compat;

import net.minecraft.client.gui.Font;

/** Keeps text inside its box: too long -> cut and ended with "...". */
public final class Fit {
    private Fit() {}

    public static String text(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0 || font.width(text) <= maxWidth) return text;
        int dots = font.width("...");
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) + dots > maxWidth) cut--;
        return text.substring(0, cut).stripTrailing() + "...";
    }

    /** Same, for text drawn in bold (bold glyphs are one pixel wider). */
    public static String bold(Font font, String text, int maxWidth) {
        if (text == null) return "";
        return text(font, text, maxWidth - Math.max(0, text.length() / 3));
    }
}
