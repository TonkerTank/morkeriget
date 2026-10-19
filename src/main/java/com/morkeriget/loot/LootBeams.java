package com.morkeriget.loot;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// Draws a coloured light beam over rare and legendary loot on the ground, so you can spot it from far away
public final class LootBeams {
    private LootBeams() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !item.level().isClientSide() || item.tickCount % 3 != 0) {
            return;
        }
        Rarity rarity = Loot.getRarity(item.getItem());
        if (rarity == null || rarity.ordinal() < Rarity.RARE.ordinal()) {
            return;
        }
        int color = rarity.beamColor;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f), 1.2f);
        int height = rarity == Rarity.MYTHIC ? 20 : rarity.ordinal() >= Rarity.LEGENDARY.ordinal() ? 12 : 6;
        for (int i = 0; i < height; i++) {
            item.level().addParticle(dust, item.getX(), item.getY() + 0.3 + i * 0.4, item.getZ(), 0, 0, 0);
        }
        // Mythic beams also have white sparks spiralling up around them
        if (rarity == Rarity.MYTHIC) {
            double angle = item.tickCount * 0.3;
            for (int i = 0; i < 2; i++) {
                double around = angle + i * Math.PI;
                item.level().addParticle(ParticleTypes.END_ROD, item.getX() + Math.cos(around) * 0.5, item.getY() + 0.3 + (item.tickCount % 30) * 0.25,
                        item.getZ() + Math.sin(around) * 0.5, 0, 0, 0);
            }
        }
    }
}
