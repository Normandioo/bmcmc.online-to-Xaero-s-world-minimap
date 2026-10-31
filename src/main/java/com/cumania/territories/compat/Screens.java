package com.cumania.territories.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The screen being shown. Minecraft moved it between versions (a field of Minecraft, then screen() on its Gui), so it is looked up once at run time. */
final class Screens {
    private Screens() {}

    private static Method method;
    private static Field field;
    private static boolean looked;

    static Screen current() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (!looked) {
                looked = true;
                try { method = mc.gui.getClass().getMethod("screen"); }
                catch (ReflectiveOperationException | RuntimeException e) { field = Minecraft.class.getField("screen"); }
            }
            return (Screen) (method != null ? method.invoke(mc.gui) : field.get(mc));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
