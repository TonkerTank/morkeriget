package com.morkeriget.skill;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.morkeriget.Text;
import com.morkeriget.loot.LootFilter;
import com.morkeriget.loot.OpenLootBagPayload;
import com.morkeriget.progress.ClassScreen;
import com.morkeriget.progress.ClientProgress;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.SkillScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// Keys for the skills. Can be changed under Options > Controls > Key Binds
public final class SkillKeys {
    public static final KeyMapping DODGE = new KeyMapping("key.morkeriget.dodge",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.morkeriget");
    // The four skills the player picked are on 1-4 like in Diablo. Those keys also pick hotbar slots, which onClientTickPre stops
    public static final List<KeyMapping> SKILL_SLOTS = List.of(
            slot(1, GLFW.GLFW_KEY_1), slot(2, GLFW.GLFW_KEY_2), slot(3, GLFW.GLFW_KEY_3), slot(4, GLFW.GLFW_KEY_4));
    public static final KeyMapping HEALTH_POTION = new KeyMapping("key.morkeriget.health_potion",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.morkeriget");
    public static final KeyMapping MANA_POTION = new KeyMapping("key.morkeriget.mana_potion",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.morkeriget");
    private static final List<KeyMapping> SKILL_KEYS = List.of(SKILL_SLOTS.get(0), SKILL_SLOTS.get(1), SKILL_SLOTS.get(2),
            SKILL_SLOTS.get(3), HEALTH_POTION, MANA_POTION);
    public static final KeyMapping SKILLS = new KeyMapping("key.morkeriget.skills",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.morkeriget");
    // Switches what the loot filter hides, see LootFilter
    public static final KeyMapping LOOT_FILTER = new KeyMapping("key.morkeriget.loot_filter",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.morkeriget");
    // Opens the loot bag, see LootBag
    public static final KeyMapping LOOT_BAG = new KeyMapping("key.morkeriget.loot_bag",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.morkeriget");

    // 20 ticks is one second
    static final int DODGE_COOLDOWN = 2 * 20;
    private static int dodgeCooldown;

    private SkillKeys() {
    }

    private static KeyMapping slot(int number, int key) {
        return new KeyMapping("key.morkeriget.skill_" + number, InputConstants.Type.KEYSYM, key, "key.categories.morkeriget");
    }

    // Ticks left before you can dodge again, for the skill bar
    static int dodgeCooldown() {
        return dodgeCooldown;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
        for (KeyMapping slot : SKILL_SLOTS) {
            event.register(slot);
        }
        event.register(HEALTH_POTION);
        event.register(MANA_POTION);
        event.register(SKILLS);
        event.register(LOOT_FILTER);
        event.register(LOOT_BAG);
    }

    // A hotbar key that is also a skill key would switch item every time you use the skill, so its presses are thrown
    // away before the game handles them. The skill key still gets them, since every key mapping counts its own presses.
    // Other hotbar keys, the scroll wheel and the number keys in the inventory still work
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        for (KeyMapping hotbar : Minecraft.getInstance().options.keyHotbarSlots) {
            if (SKILL_KEYS.stream().anyMatch(skill -> skill.getKey().equals(hotbar.getKey()))) {
                while (hotbar.consumeClick()) {
                    // Nothing, the press belongs to the skill
                }
            }
        }
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
        PlayerClass playerClass = ClientProgress.get().chosenClass();
        for (int i = 0; i < SKILL_SLOTS.size(); i++) {
            while (SKILL_SLOTS.get(i).consumeClick()) {
                Skill skill = ClientProgress.get().slot(i);
                if (playerClass == null) {
                    player.displayClientMessage(Text.tr("skill.pick_class"), true);
                } else if (skill == null) {
                    player.displayClientMessage(Text.tr("skill.empty_key"), true);
                } else {
                    PacketDistributor.sendToServer(new SkillPayload(skill));
                }
            }
        }
        while (HEALTH_POTION.consumeClick()) {
            PacketDistributor.sendToServer(new SkillPayload(Skill.HEALTH_POTION));
        }
        while (MANA_POTION.consumeClick()) {
            PacketDistributor.sendToServer(new SkillPayload(Skill.MANA_POTION));
        }
        while (SKILLS.consumeClick()) {
            // The first time, you pick a class
            Minecraft.getInstance().setScreen(playerClass == null ? new ClassScreen() : new SkillScreen());
        }
        while (LOOT_FILTER.consumeClick()) {
            PacketDistributor.sendToServer(new LootFilter.CyclePayload());
        }
        while (LOOT_BAG.consumeClick()) {
            PacketDistributor.sendToServer(OpenLootBagPayload.INSTANCE);
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
