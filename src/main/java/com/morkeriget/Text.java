package com.morkeriget;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

// The game's texts, from the language files (assets/morkeriget/lang), so every player sees them in their own language.
// Keys are "morkeriget." followed by the key given here
public final class Text {
    private Text() {
    }

    public static MutableComponent tr(String key, Object... args) {
        return Component.translatable(Morkeriget.MODID + "." + key, args);
    }

    // Whether a line, e.g. in an item's lore, is this text. Lines are found again by their key, since what they read
    // depends on the language
    public static boolean is(Component line, String key) {
        return line.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(Morkeriget.MODID + "." + key);
    }

    // E.g. "north-east": the way to a spot dx and dz blocks away
    public static MutableComponent direction(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        // 0 degrees is east and 90 is south, since z goes south in Minecraft
        String[] names = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
        return tr("direction." + names[Math.floorMod((int) Math.round(angle / 45), 8)]);
    }

    // A whole number, or one decimal written the Danish and English way alike, e.g. "12.5"
    public static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
