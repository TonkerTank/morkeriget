package com.morkeriget.elite;

import com.morkeriget.Morkeriget;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

// Extra powers an elite can spawn with, like in Diablo. What they do when the elite hits or ticks is in Elites
public enum EliteModifier {
    BURNING("Brændende", ChatFormatting.RED),
    FROST("Frostende", ChatFormatting.AQUA),
    FAST("Hurtig", ChatFormatting.YELLOW),
    TELEPORTING("Teleporterende", ChatFormatting.LIGHT_PURPLE),
    VAMPIRIC("Vampyrisk", ChatFormatting.DARK_RED),
    ARMORED("Pansret", ChatFormatting.GRAY),
    // These two are for the leader's whole pack, see ElitePacks. Without a pack they only work on the elite itself
    SHIELDING("Beskyttende", ChatFormatting.BLUE),
    HEALING("Helende", ChatFormatting.GREEN);

    public final String displayName;
    public final ChatFormatting color;
    private final String tag;

    EliteModifier(String displayName, ChatFormatting color) {
        this.displayName = displayName;
        this.color = color;
        this.tag = "morkeriget_elite_" + name().toLowerCase();
    }

    public boolean isOn(Entity entity) {
        return entity.getPersistentData().getBoolean(tag);
    }

    // Gives the monster this modifier. Attribute modifiers and effects are saved with the monster by the game itself
    public void apply(Monster monster) {
        monster.getPersistentData().putBoolean(tag, true);
        switch (this) {
            case BURNING -> monster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            case FAST -> boost(monster, Attributes.MOVEMENT_SPEED, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            case ARMORED -> {
                boost(monster, Attributes.ARMOR, 5, AttributeModifier.Operation.ADD_VALUE);
                boost(monster, Attributes.KNOCKBACK_RESISTANCE, 0.8, AttributeModifier.Operation.ADD_VALUE);
            }
            default -> {
            }
        }
    }

    private void boost(Monster monster, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = monster.getAttribute(attribute);
        if (instance != null) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "elite_" + name().toLowerCase());
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
