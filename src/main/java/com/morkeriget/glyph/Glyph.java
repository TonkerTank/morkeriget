package com.morkeriget.glyph;

import java.util.Locale;

import net.minecraft.ChatFormatting;

// The kinds of glyph, like in Diablo 4. Each one gives its bonus for every level, up to MAX_LEVEL. See Glyphs
public enum Glyph {
    WRATH("Vrede", "mere skade", 0.75, ChatFormatting.RED),
    FLAME("Flamme", "mere skade på brændende fjender", 2.0, ChatFormatting.GOLD),
    DOMINION("Herredømme", "mere skade på elites og bosser", 1.5, ChatFormatting.DARK_PURPLE),
    LEGION("Legion", "mere skade fra dine skeletter og ulve", 2.0, ChatFormatting.DARK_GREEN),
    FROST("Kulde", "mere skade på langsomme fjender", 2.0, ChatFormatting.AQUA),
    BULWARK("Bolværk", "skade taget", 0.75, ChatFormatting.GRAY);

    public static final int MAX_LEVEL = 20;

    public final String displayName;
    private final String effect;
    // Percent per level
    private final double perLevel;
    public final ChatFormatting color;

    Glyph(String displayName, String effect, double perLevel, ChatFormatting color) {
        this.displayName = displayName;
        this.effect = effect;
        this.perLevel = perLevel;
        this.color = color;
    }

    // As a share, e.g. 0.3 for 30%
    public double bonus(int level) {
        return perLevel * Math.min(level, MAX_LEVEL) / 100;
    }

    // E.g. "+30% mere skade på elites og bosser"
    public String describe(int level) {
        String percent = String.format(Locale.ROOT, "%.1f", bonus(level) * 100).replace(".0", "").replace('.', ',');
        return (this == BULWARK ? "-" : "+") + percent + "% " + effect;
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
