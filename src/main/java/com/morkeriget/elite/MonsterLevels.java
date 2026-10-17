package com.morkeriget.elite;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.progress.Progression;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// Monsters get a level when they spawn, based on the level of the nearest player, so the game stays hard as you level up.
// The level is saved on the monster and synced to clients for the target frame
public final class MonsterLevels {
    // Monsters further away than this from every player get level 1
    private static final double PLAYER_RANGE = 128.0;
    private static final ResourceLocation LEVEL_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "monster_level");

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    // 0 means the monster has no level, e.g. it spawned before this feature existed
    public static final Supplier<AttachmentType<Integer>> LEVEL = ATTACHMENTS.register("monster_level",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).sync(ByteBufCodecs.VAR_INT).build());

    private MonsterLevels() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static int get(Entity entity) {
        return entity.getData(LEVEL);
    }

    // The nearest player's level, give or take one, plus a bonus for elites and bosses
    static int roll(Monster monster, int bonus) {
        Player nearest = monster.level().getNearestPlayer(monster, PLAYER_RANGE);
        int playerLevel = nearest instanceof ServerPlayer player ? Progression.level(player) : 1;
        int spread = Config.MONSTER_LEVEL_SPREAD.get();
        int level = playerLevel + monster.getRandom().nextInt(spread * 2 + 1) - spread + bonus;
        return Math.max(1, Math.min(Progression.MAX_LEVEL + 2, level));
    }

    // Gives the monster its level and makes it stronger for every level above 1
    public static void apply(Monster monster, int level) {
        monster.setData(LEVEL, level);
        int above = level - 1;
        boost(monster.getAttribute(Attributes.MAX_HEALTH), above * Config.HEALTH_PER_LEVEL.get());
        boost(monster.getAttribute(Attributes.ATTACK_DAMAGE), above * Config.DAMAGE_PER_LEVEL.get());
        monster.setHealth(monster.getMaxHealth());
    }

    // Added to the base value, so it stacks nicely with the elite and boss multipliers
    private static void boost(AttributeInstance attribute, double amount) {
        if (attribute != null && amount > 0) {
            attribute.addPermanentModifier(new AttributeModifier(LEVEL_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
