package com.morkeriget.loot;

import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import com.morkeriget.combat.CameraShake;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

// Draws a coloured light beam over rare and legendary loot on the ground, so you can spot it from far away. The
// first time a legendary, set or mythic item shows up it also gets a chime and a burst of light, and a mythic one a
// deeper sound and a small shake, so a good drop is noticed even in a big fight. Client only
public final class LootBeams {
    // The items that have had their chime, so it comes once and not again when the item is seen again
    private static final Set<Integer> ANNOUNCED = new HashSet<>();
    private static final int MAX_ANNOUNCED = 4096;

    private LootBeams() {
    }

    public static void reset() {
        ANNOUNCED.clear();
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !item.level().isClientSide()) {
            return;
        }
        Rarity rarity = Loot.getRarity(item.getItem());
        if (rarity == null || rarity.ordinal() < Rarity.RARE.ordinal()) {
            return;
        }
        if (rarity.ordinal() >= Rarity.LEGENDARY.ordinal() && ANNOUNCED.add(item.getId())) {
            announce(item, rarity);
        }
        if (item.tickCount % 3 != 0) {
            return;
        }
        DustParticleOptions dust = dust(rarity, 1.2f);
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

    private static void announce(ItemEntity item, Rarity rarity) {
        if (ANNOUNCED.size() > MAX_ANNOUNCED) {
            ANNOUNCED.clear();
            ANNOUNCED.add(item.getId());
        }
        boolean mythic = rarity == Rarity.MYTHIC;
        double x = item.getX();
        double y = item.getY() + 0.4;
        double z = item.getZ();
        item.level().playLocalSound(x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, mythic ? 0.7f : 1.2f, false);
        item.level().playLocalSound(x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8f, mythic ? 0.5f : 0.9f, false);
        if (mythic) {
            item.level().playLocalSound(x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.3f, false);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.player.distanceTo(item) < 24) {
                CameraShake.add(0.25f);
            }
        }
        // A ring of sparks bursting out from the item, in its colour
        DustParticleOptions dust = dust(rarity, 1.5f);
        int sparks = mythic ? 40 : 24;
        double speed = mythic ? 0.35 : 0.25;
        for (int i = 0; i < sparks; i++) {
            double angle = Math.PI * 2 * i / sparks;
            item.level().addParticle(ParticleTypes.END_ROD, x, y, z, Math.cos(angle) * speed, 0.05, Math.sin(angle) * speed);
            item.level().addParticle(dust, x + Math.cos(angle) * 0.6, y, z + Math.sin(angle) * 0.6, 0, 0, 0);
        }
    }

    private static DustParticleOptions dust(Rarity rarity, float size) {
        int color = rarity.beamColor;
        return new DustParticleOptions(new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f), size);
    }
}
