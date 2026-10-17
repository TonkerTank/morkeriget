package com.morkeriget.elite;

import com.morkeriget.Morkeriget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// Some monsters spawn as elites: golden name, glowing outline, much more health and damage, and they always drop good loot
public final class Elites {
    private static final String ELITE_TAG = "morkeriget_elite";
    private static final String CHECKED_TAG = "morkeriget_elite_checked";
    private static final float ELITE_CHANCE = 0.05f;
    private static final ResourceLocation ELITE_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "elite_boost");

    private Elites() {
    }

    public static void register(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(Elites::onJoinLevel);
    }

    public static boolean isElite(Entity entity) {
        return entity.getPersistentData().getBoolean(ELITE_TAG);
    }

    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster)) {
            return;
        }
        // Only roll once per monster, so it doesn't happen again when the chunk is loaded later
        if (monster.getPersistentData().getBoolean(CHECKED_TAG)) {
            return;
        }
        monster.getPersistentData().putBoolean(CHECKED_TAG, true);
        if (monster.getRandom().nextFloat() >= ELITE_CHANCE) {
            return;
        }
        monster.getPersistentData().putBoolean(ELITE_TAG, true);

        boost(monster.getAttribute(Attributes.MAX_HEALTH), 2.0);
        boost(monster.getAttribute(Attributes.ATTACK_DAMAGE), 0.5);
        monster.setHealth(monster.getMaxHealth());
        monster.setCustomName(Component.literal("Elite ").append(monster.getType().getDescription()).withStyle(ChatFormatting.GOLD));
        monster.setCustomNameVisible(true);
        monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
    }

    private static void boost(AttributeInstance attribute, double amount) {
        if (attribute != null) {
            attribute.addPermanentModifier(new AttributeModifier(ELITE_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
