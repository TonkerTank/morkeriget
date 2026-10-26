package com.morkeriget.elite;

import java.util.Locale;

import com.morkeriget.Morkeriget;
import com.morkeriget.Text;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
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
    // Names are in the language files, under elite.<name>
    BURNING(ChatFormatting.RED),
    FROST(ChatFormatting.AQUA),
    FAST(ChatFormatting.YELLOW),
    TELEPORTING(ChatFormatting.LIGHT_PURPLE),
    VAMPIRIC(ChatFormatting.DARK_RED),
    ARMORED(ChatFormatting.GRAY),
    // These two are for the leader's whole pack, see ElitePacks. Without a pack they only work on the elite itself
    SHIELDING(ChatFormatting.BLUE),
    HEALING(ChatFormatting.GREEN);

    public final ChatFormatting color;
    private final String tag;

    EliteModifier(ChatFormatting color) {
        this.color = color;
        this.tag = "morkeriget_elite_" + name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Text.tr("elite." + name().toLowerCase(Locale.ROOT));
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
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "elite_" + name().toLowerCase(Locale.ROOT));
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
