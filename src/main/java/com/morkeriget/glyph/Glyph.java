package com.morkeriget.glyph;

import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;

// The kinds of glyph, like in Diablo 4. Each one gives its bonus for every level, up to MAX_LEVEL. See Glyphs
public enum Glyph {
    WRATH(0.75, ChatFormatting.RED),
    FLAME(2.0, ChatFormatting.GOLD),
    DOMINION(1.5, ChatFormatting.DARK_PURPLE),
    LEGION(2.0, ChatFormatting.DARK_GREEN),
    FROST(2.0, ChatFormatting.AQUA),
    BULWARK(0.75, ChatFormatting.GRAY);

    public static final int MAX_LEVEL = 20;

    // Percent per level
    private final double perLevel;
    public final ChatFormatting color;

    // Names and effects are in the language files, under glyph.<name>
    Glyph(double perLevel, ChatFormatting color) {
        this.perLevel = perLevel;
        this.color = color;
    }

    // As a share, e.g. 0.3 for 30%
    public double bonus(int level) {
        return perLevel * Math.min(level, MAX_LEVEL) / 100;
    }

    public MutableComponent displayName() {
        return Text.tr("glyph." + name().toLowerCase(Locale.ROOT) + ".name");
    }

    // E.g. "+30% more damage to elites and bosses"
    public MutableComponent describe(int level) {
        String percent = String.format(Locale.ROOT, "%.1f", bonus(level) * 100).replace(".0", "");
        return Text.tr("glyph." + name().toLowerCase(Locale.ROOT) + ".effect", percent);
    }

    public static Glyph byName(String name) {
        for (Glyph glyph : values()) {
            if (glyph.name().equals(name)) {
                return glyph;
            }
        }
        return null;
    }

    // How many glyphs can be socketed at this paragon level: one from the first, and more at 10, 25 and 50
    public static int slots(int paragonLevel) {
        return paragonLevel >= 50 ? 4 : paragonLevel >= 25 ? 3 : paragonLevel >= 10 ? 2 : paragonLevel >= 1 ? 1 : 0;
    }

    public static final int MAX_SLOTS = 4;
}
