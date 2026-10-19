package com.cumania.territories.compat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

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

    /** Same, for text drawn in bold (measured as bold, whose glyphs are wider). */
    public static String bold(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0 || boldWidth(font, text) <= maxWidth) return text;
        int cut = text.length();
        while (cut > 0 && boldWidth(font, text.substring(0, cut) + "...") > maxWidth) cut--;
        return text.substring(0, cut).stripTrailing() + "...";
    }

    public static int boldWidth(Font font, String text) {
        return font.width(Component.literal(text).withStyle(ChatFormatting.BOLD));
    }
}
