package com.morkeriget.smithy;

import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

// Gems go into the sockets on loot in the smithy. Each kind gives one bonus, and comes in five tiers from chipped
// to perfect. Three gems of the same kind and tier can be combined into one of the next tier.
// Sapphire's mana bonus isn't an attribute, so it is stored on the item like a special affix and used by Mana
public enum Gem {
    // Names and bonus texts are in the language files, under gem.<name>
    RUBY(0xE0284A, Attributes.ATTACK_DAMAGE, 2, 1),
    EMERALD(0x2EC45A, Attributes.MAX_HEALTH, 4, 2),
    TOPAZ(0xF2B92A, Attributes.ARMOR, 2, 1),
    SAPPHIRE(0x3A6BEF, null, 20, 1);

    public static final int MAX_TIER = 5;
    // How much stronger each tier is than a chipped gem
    private static final double[] MULTIPLIERS = {1, 1.5, 2, 3, 4};
    // The gem's item is a white quartz picture coloured with this
    public final int color;
    // null for sapphire
    final Holder<Attribute> attribute;
    // The bonus of a chipped gem
    private final double baseAmount;
    // Health is in half hearts, so the emerald's number is halved for the text
    private final int shownDivisor;

    Gem(int color, Holder<Attribute> attribute, double baseAmount, int shownDivisor) {
        this.color = color;
        this.attribute = attribute;
        this.baseAmount = baseAmount;
        this.shownDivisor = shownDivisor;
    }

    public MutableComponent displayName() {
        return Text.tr("gem." + name().toLowerCase(Locale.ROOT));
    }

    double amount(int tier) {
        return Math.round(baseAmount * MULTIPLIERS[clamp(tier) - 1]);
    }

    // E.g. "+6 attack damage"
    public MutableComponent bonus(int tier) {
        return Text.tr("gem." + name().toLowerCase(Locale.ROOT) + ".bonus", Text.number(amount(tier) / shownDivisor));
    }

    // E.g. "Flawless Ruby", or just "Ruby" for tier 3
    public MutableComponent name(int tier) {
        return Text.tr("gem.tier." + clamp(tier), Text.tr("gem." + name().toLowerCase(Locale.ROOT) + ".in_name"));
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
