package com.morkeriget.combat;

import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.skill.Skill;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Makes fights feel heavier: the camera shakes on big skills and when elites and bosses die, and their deaths get a
// burst of particles and a sound. Crushing blows and good loot get theirs on the client, see DamageNumbers and
// LootBeams. Server side
public final class Juice {
    private static final float ELITE_KILL_SHAKE = 0.3f;
    private static final float BOSS_KILL_SHAKE = 0.7f;
    // Players this close to a dying boss feel it too
    private static final double BOSS_SHAKE_RANGE = 24;

    private Juice() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Juice::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Juice::onDeath);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The lambda keeps CameraShake (client only) from being loaded on a dedicated server
        event.registrar("1").playToClient(ShakePayload.TYPE, ShakePayload.STREAM_CODEC, (payload, context) -> CameraShake.add(payload.strength()));
    }

    public static void shake(ServerPlayer player, float strength) {
        if (strength > 0) {
            PacketDistributor.sendToPlayer(player, new ShakePayload(strength));
        }
    }

    // How hard the camera shakes when the skill is used: the slams and big area skills shake, the rest don't
    public static float skillShake(Skill skill) {
        return switch (skill) {
            case DOOM_SLAM -> 0.65f;
            case HEAVENS_JUDGEMENT -> 0.6f;
            case EARTHQUAKE -> 0.5f;
            case METEOR, ARMY_OF_THE_DEAD -> 0.4f;
            case LEAP, SHIELD_BASH, THUNDERSTORM, ARROW_HURRICANE -> 0.3f;
            case HOLY_HAMMER, BLOOD_NOVA, FROST_NOVA, CONSECRATION -> 0.22f;
            default -> 0;
        };
    }

    public static void skillUsed(ServerPlayer player, Skill skill) {
        if (skill.isPotion()) {
            return;
        }
        shake(player, skillShake(skill));
        castFlash(player);
    }

    // Every skill starts with a ring of light in the class colour around the feet and a short sound for the class, on
    // top of the skill's own sounds and effects, so using a skill always feels like something happened
    private static void castFlash(ServerPlayer player) {
        PlayerClass playerClass = Progression.playerClass(player);
        if (playerClass == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int color = playerClass.color;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f), 1.1f);
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2 * i / 18;
            level.sendParticles(dust, player.getX() + Math.cos(angle) * 0.9, player.getY() + 0.1, player.getZ() + Math.sin(angle) * 0.9, 1, 0, 0, 0, 0);
        }
        level.sendParticles(dust, player.getX(), player.getY() + 1, player.getZ(), 6, 0.3, 0.5, 0.3, 0);
        SoundEvent sound = switch (playerClass) {
            case BARBARIAN -> SoundEvents.PLAYER_ATTACK_KNOCKBACK;
            case SORCERER -> SoundEvents.ILLUSIONER_CAST_SPELL;
            case RANGER -> SoundEvents.ARROW_SHOOT;
            case NECROMANCER -> SoundEvents.EVOKER_CAST_SPELL;
            case PALADIN -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.35f, 1.3f);
    }

    // A burst of soul fire and smoke and a deep thud where an elite dies, and a bigger one with an explosion for a boss.
    // The killer's camera shakes, and for a boss everyone's close by
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity monster = event.getEntity();
        if (!(monster.level() instanceof ServerLevel level)) {
            return;
        }
        boolean boss = Bosses.isBoss(monster);
        if (!boss && !Elites.isElite(monster)) {
            return;
        }
        double x = monster.getX();
        double y = monster.getY() + monster.getBbHeight() / 2;
        double z = monster.getZ();
        double spread = monster.getBbWidth() / 2 + 0.3;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, boss ? 60 : 25, spread, monster.getBbHeight() / 3, spread, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, boss ? 30 : 10, spread, monster.getBbHeight() / 3, spread, 0.03);
        if (boss) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.7f);
            for (Player player : level.players()) {
                if (player instanceof ServerPlayer nearby && nearby.distanceTo(monster) <= BOSS_SHAKE_RANGE) {
                    shake(nearby, BOSS_KILL_SHAKE);
                }
            }
        } else {
            level.playSound(null, x, y, z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 0.9f, 0.8f);
            if (monster.getKillCredit() instanceof ServerPlayer killer) {
                shake(killer, ELITE_KILL_SHAKE);
            }
        }
    }
}
