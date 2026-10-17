package com.morkeriget.skill;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Keys for the skills. Can be changed under Options > Controls > Key Binds
public final class SkillKeys {
    public static final KeyMapping DODGE = new KeyMapping("key.morkeriget.dodge",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.morkeriget");
    public static final KeyMapping WHIRLWIND = new KeyMapping("key.morkeriget.whirlwind",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.morkeriget");

    // 20 ticks is one second
    private static final int DODGE_COOLDOWN = 2 * 20;
    private static int dodgeCooldown;

    private SkillKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
        event.register(WHIRLWIND);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
        }
        while (DODGE.consumeClick()) {
            dodge(player);
        }
        while (WHIRLWIND.consumeClick()) {
            PacketDistributor.sendToServer(WhirlwindPayload.INSTANCE);
        }
    }

    // A quick jump in the direction you are walking (or looking, if you stand still), like the dodge in Diablo.
    // The player's own movement is decided on the client, so this doesn't need to go through the server
    private static void dodge(LocalPlayer player) {
        if (dodgeCooldown > 0) {
            return;
        }
        dodgeCooldown = DODGE_COOLDOWN;
        Vec3 direction = new Vec3(player.input.leftImpulse, 0, player.input.forwardImpulse);
        if (direction.lengthSqr() < 0.01) {
            direction = new Vec3(0, 0, 1);
        }
        // Turn the direction so it follows where the player is facing
        direction = direction.normalize().yRot(-player.getYRot() * (float) (Math.PI / 180));
        player.setDeltaMovement(direction.x * 1.4, 0.25, direction.z * 1.4);
        player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.6f);
        for (int i = 0; i < 8; i++) {
            player.level().addParticle(ParticleTypes.CLOUD, player.getRandomX(0.5), player.getY() + 0.2, player.getRandomZ(0.5), 0, 0, 0);
        }
    }
}
