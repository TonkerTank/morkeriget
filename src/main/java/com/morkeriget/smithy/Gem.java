package com.morkeriget.smithy;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

// Gems go into the sockets on loot in the smithy. Each kind gives one bonus, and comes in five tiers from chipped
// to perfect. Three gems of the same kind and tier can be combined into one of the next tier.
// Sapphire's mana bonus isn't an attribute, so it is stored on the item like a special affix and used by Mana
public enum Gem {
    RUBY("Rubin", 0xE0284A, Attributes.ATTACK_DAMAGE, 2, "+%s angrebsskade", 1),
    EMERALD("Smaragd", 0x2EC45A, Attributes.MAX_HEALTH, 4, "+%s hjerter", 2),
    TOPAZ("Topas", 0xF2B92A, Attributes.ARMOR, 2, "+%s rustning", 1),
    SAPPHIRE("Safir", 0x3A6BEF, null, 20, "+%s%% hurtigere mana", 1);

    public static final int MAX_TIER = 5;
    // How much stronger each tier is than a chipped gem
    private static final double[] MULTIPLIERS = {1, 1.5, 2, 3, 4};
    // Tier 3 is just "Rubin"
    private static final String[] TIER_NAMES = {"Splintret", "Ru", "", "Fejlfri", "Perfekt"};

    public final String displayName;
    // The gem's item is a white quartz picture coloured with this
    public final int color;
    // null for sapphire
    final Holder<Attribute> attribute;
    // The bonus of a chipped gem
    private final double baseAmount;
    private final String bonusText;
    // Health is in half hearts, so the emerald's number is halved for the text
    private final int shownDivisor;

    Gem(String displayName, int color, Holder<Attribute> attribute, double baseAmount, String bonusText, int shownDivisor) {
        this.displayName = displayName;
        this.color = color;
        this.attribute = attribute;
        this.baseAmount = baseAmount;
        this.bonusText = bonusText;
        this.shownDivisor = shownDivisor;
    }

    double amount(int tier) {
        return Math.round(baseAmount * MULTIPLIERS[clamp(tier) - 1]);
    }

    // E.g. "+6 angrebsskade"
    public String bonus(int tier) {
        double shown = amount(tier) / shownDivisor;
        String number = shown == Math.floor(shown) ? String.valueOf((int) shown) : String.valueOf(shown);
        return String.format(bonusText, number);
    }

    // E.g. "Fejlfri Rubin", or just "Rubin" for tier 3
    public String name(int tier) {
        String prefix = TIER_NAMES[clamp(tier) - 1];
        return prefix.isEmpty() ? displayName : prefix + " " + displayName.toLowerCase();
    }

    AttributeModifier.Operation operation() {
        return AttributeModifier.Operation.ADD_VALUE;
    }

    static int clamp(int tier) {
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    // The gem this item is, or null
    public static Gem of(ItemStack stack) {
        for (Gem gem : values()) {
            if (stack.is(Smithy.GEMS.get(gem).get())) {
                return gem;
            }
        }
        return null;
    }

    public static Gem byName(String name) {
        for (Gem gem : values()) {
            if (gem.name().equals(name)) {
                return gem;
            }
        }
        return null;
    }
}
