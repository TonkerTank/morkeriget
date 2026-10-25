package com.morkeriget.elite;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.PlayerBars;
import com.morkeriget.journey.Achievement;
import com.morkeriget.journey.Journey;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// Every so often a huge boss wakes up somewhere near the players, like the world bosses in Diablo 4. Everyone is told
// where and when, and everyone close by when it dies gets their own rewards. Server only
public final class WorldBoss {
    private static final String WORLD_BOSS_TAG = "morkeriget_world_boss";
    // In seconds, since the world boss ticks once a second
    private static final int WARNING = 5 * 60;
    private static final int LIFETIME = 15 * 60;
    private static final int MIN_DISTANCE = 60;
    private static final int MAX_DISTANCE = 120;
    // It wakes up when a player is this close, so its chunk is loaded. Players this close when it dies get rewards
    private static final double RANGE = 48.0;
    // Inside this the normal boss bar shows, so the bar with the way to it is taken away
    private static final double NEAR = 32.0;
    private static final float MAX_HIT = 0.35f;

    // What the world boss can be. extraScale is added on top of what all bosses get
    private record Kind(EntityType<? extends Monster> type, String name, double extraScale) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind(EntityType.RAVAGER, "Ødelæggeren", 0.5),
            new Kind(EntityType.VINDICATOR, "Bøddelen fra Dybet", 1.0),
            new Kind(EntityType.WITHER_SKELETON, "Askekongen", 1.0));

    // Seconds until the next world boss is announced. Only in memory, so it starts over when the server starts
    private static int untilNext = -1;
    // The world boss that is coming or awake, or null
    private static Event event;

    private WorldBoss() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(WorldBoss::onServerTick);
        NeoForge.EVENT_BUS.addListener(WorldBoss::onDeath);
        NeoForge.EVENT_BUS.addListener(WorldBoss::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(WorldBoss::onTravel);
        NeoForge.EVENT_BUS.addListener(WorldBoss::registerCommands);
        // In singleplayer the game can go on to another world, which must start without this one's boss
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent stopping) -> {
            if (event != null) {
                event.end(null);
                event = null;
            }
            untilNext = -1;
        });
    }

    // "/morkeriget worldboss" makes a world boss come in 10 seconds, for testing. Needs cheats
    private static void registerCommands(RegisterCommandsEvent commands) {
        commands.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("worldboss")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (event != null) {
                        event.removeBoss();
                        event.end(null);
                    }
                    event = new Event(player.serverLevel(), player, 10);
                    return 1;
                })));
    }

    private static void onServerTick(ServerTickEvent.Post tick) {
        ServerLevel level = tick.getServer().overworld();
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        if (event != null) {
            if (event.tick()) {
                event = null;
                untilNext = Config.WORLD_BOSS_INTERVAL.get() * 60 - WARNING;
            }
            return;
        }
        if (untilNext < 0) {
            untilNext = Config.WORLD_BOSS_INTERVAL.get() * 60 - WARNING;
        }
        if (untilNext > 0) {
            untilNext--;
        }
        // Waits at 0 until someone is in the overworld, and no world boss comes on Peaceful, where it would vanish
        if (untilNext == 0 && !level.players().isEmpty() && level.getDifficulty() != Difficulty.PEACEFUL) {
            ServerPlayer player = level.players().get(level.getRandom().nextInt(level.players().size()));
            event = new Event(level, player, WARNING);
        }
    }

    private static void onDeath(LivingDeathEvent death) {
        if (event != null && death.getEntity().getUUID().equals(event.bossId)) {
            event.defeated(death.getEntity());
            event = null;
            untilNext = Config.WORLD_BOSS_INTERVAL.get() * 60 - WARNING;
        }
    }

    // The event only follows the boss in the overworld, so a world boss lured through a portal would just be gone
    private static void onTravel(EntityTravelToDimensionEvent travel) {
        if (travel.getEntity().getPersistentData().getBoolean(WORLD_BOSS_TAG)) {
            travel.setCanceled(true);
        }
    }

    // A world boss is saved with the world, but the event isn't, so one left over from before a restart is removed
    private static void onJoinLevel(EntityJoinLevelEvent join) {
        Entity entity = join.getEntity();
        if (!join.getLevel().isClientSide() && entity.getPersistentData().getBoolean(WORLD_BOSS_TAG)
                && (event == null || !entity.getUUID().equals(event.bossId))) {
            join.setCanceled(true);
        }
    }

    private static final class Event {
        private final ServerLevel level;
        private final Kind kind;
        // Where it wakes up, and once awake where it was last seen. The height is found when it spawns, since the chunk
        // may not be loaded before
        private int x;
        private int z;
        private int untilWake;
        private final int warning;
        private UUID bossId;
        // Seconds since it woke up, or since it should have if nobody came
        private int awake;
        // One for each player, since it says how far and which way the boss is for them
        private final PlayerBars bars = new PlayerBars(BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

        Event(ServerLevel level, ServerPlayer near, int warning) {
            this.level = level;
            RandomSource random = level.getRandom();
            kind = KINDS.get(random.nextInt(KINDS.size()));
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            x = (int) (near.getX() + Math.cos(angle) * distance);
            z = (int) (near.getZ() + Math.sin(angle) * distance);
            this.warning = warning;
            untilWake = warning;
            broadcast(Component.literal("☠ Verdensboss: ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal(kind.name + " vågner om " + time(warning) + "! Følg den røde bjælke, og saml jeres styrker")
                            .withStyle(ChatFormatting.RED)));
            for (ServerPlayer player : level.players()) {
                player.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.6f, 0.6f);
            }
        }

        // Once a second. Returns true when it is over without anyone killing it
        boolean tick() {
            if (bossId == null) {
                if (untilWake > 0) {
                    untilWake--;
                    if (untilWake == 60 || untilWake == 10) {
                        broadcast(Component.literal(kind.name + " vågner om " + untilWake + " sekunder!").withStyle(ChatFormatting.RED));
                    }
                } else if (++awake > LIFETIME) {
                    end(kind.name + " faldt i søvn igen. Ingen kom");
                    return true;
                } else {
                    ServerPlayer near = nearest();
                    if (near != null && distance(near) < RANGE) {
                        spawn();
                    }
                }
            } else {
                Entity boss = level.getEntity(bossId);
                if (boss != null) {
                    // Remembered, so the bar still points the right way if its chunk is unloaded
                    x = (int) boss.getX();
                    z = (int) boss.getZ();
                } else if (level.areEntitiesLoaded(ChunkPos.asLong(x >> 4, z >> 4))) {
                    // The monsters in its chunk are loaded but it isn't there, so something took it away without killing it.
                    // The chunk itself loads a moment before its monsters, so that isn't enough to tell
                    end(kind.name + " forsvandt");
                    return true;
                }
                if (++awake > LIFETIME) {
                    removeBoss();
                    end(kind.name + " forsvandt tilbage i mørket");
                    return true;
                }
            }
            updateBar();
            return false;
        }

        private void updateBar() {
            float progress = bossId == null && untilWake > 0
                    ? Mth.clamp((float) untilWake / warning, 0, 1)
                    : Mth.clamp(1 - (float) awake / LIFETIME, 0, 1);
            String state = bossId != null ? " er vågen!" : untilWake > 0 ? " vågner om " + time(untilWake) : " venter på jer";
            for (ServerPlayer player : level.players()) {
                // Close to the boss its own boss bar shows instead
                if (bossId == null || distance(player) >= NEAR) {
                    bars.show(player, Component.literal(kind.name + state + " - " + (int) distance(player) + " blokke mod " + direction(player)),
                            progress);
                }
            }
            bars.finish();
        }

        private void spawn() {
            Monster boss = kind.type.create(level);
            if (boss == null) {
                return;
            }
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            boss.moveTo(x + 0.5, pos.getY(), z + 0.5, level.getRandom().nextFloat() * 360, 0);
            // Gives it its normal gear, like an axe for vindicators
            boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            Elites.markChecked(boss);
            // Three levels above the strongest player close by
            if (Config.MONSTER_LEVELS.get()) {
                int monsterLevel = 1;
                for (ServerPlayer player : players()) {
                    monsterLevel = Math.max(monsterLevel, Progression.monsterLevel(player));
                }
                MonsterLevels.apply(boss, monsterLevel + 3);
            }
            Bosses.makeWorldBoss(boss, kind.name, Config.WORLD_BOSS_HEALTH.get(), kind.extraScale);
            Bosses.setMaxHit(boss, MAX_HIT);
            boss.getPersistentData().putBoolean(WORLD_BOSS_TAG, true);
            ServerPlayer near = nearest();
            if (near != null) {
                boss.setTarget(near);
            }
            bossId = boss.getUUID();
            awake = 0;
            level.addFreshEntity(boss);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY() + 1, boss.getZ(), 2, 1, 0.5, 1, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 1, boss.getZ(), 100, 2, 1.5, 2, 0.05);
            for (ServerPlayer player : level.players()) {
                player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 0.7f);
            }
            broadcast(Component.literal("☠ " + kind.name + " er vågnet!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }

        // Everyone close by gets their own rewards, right into their inventory, so nobody has to fight over the loot
        void defeated(Entity boss) {
            List<ServerPlayer> winners = players();
            RandomSource random = level.getRandom();
            int itemLevel = Math.max(1, MonsterLevels.get(boss));
            for (ServerPlayer player : winners) {
                List<ItemStack> rewards = new ArrayList<>();
                rewards.add(Loot.create(random, Rarity.LEGENDARY, itemLevel, Progression.playerClass(player)));
                rewards.add(Loot.create(random, Rarity.roll(random, Rarity.RARE), itemLevel, Progression.playerClass(player)));
                if (random.nextDouble() < Config.WORLD_BOSS_MYTHIC_CHANCE.get()) {
                    ItemStack mythic = Loot.createMythic(random, itemLevel, Progression.playerClass(player));
                    rewards.add(mythic);
                    Loot.announceMythic(player, mythic);
                }
                rewards.add(new ItemStack(Smithy.ESSENCE.get(), 25 + random.nextInt(16)));
                rewards.add(Gems.random(random, itemLevel, 1));
                rewards.add(Gems.random(random, itemLevel, 1));
                for (ItemStack reward : rewards) {
                    // Counts as found for the season journey, since it is never picked up off the ground
                    Journey.found(player, reward);
                    player.getInventory().placeItemBackInInventory(reward);
                }
                Progression.addXp(player, 500 + 50 * itemLevel);
                Journey.add(player, Achievement.Stat.WORLD_BOSSES);
                player.sendSystemMessage(Component.literal("Din belønning for " + kind.name + " er lagt i dit inventar")
                        .withStyle(ChatFormatting.GOLD));
            }
            List<String> names = winners.stream().map(player -> player.getGameProfile().getName()).toList();
            end(null);
            broadcast(Component.literal("☠ " + kind.name + " er besejret").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal(names.isEmpty() ? "!" : " af " + String.join(", ", names) + "!").withStyle(ChatFormatting.YELLOW)));
        }

        // Takes the boss out of the world, if it is awake and there
        void removeBoss() {
            Entity boss = bossId != null ? level.getEntity(bossId) : null;
            if (boss != null) {
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 80, 1, 1.5, 1, 0.1);
                boss.discard();
            }
        }

        // message is null when nobody needs to be told
        void end(String message) {
            bars.clear();
            if (message != null) {
                broadcast(Component.literal(message).withStyle(ChatFormatting.GRAY));
            }
        }

        private List<ServerPlayer> players() {
            return level.players().stream().filter(player -> player.isAlive() && distance(player) < RANGE).toList();
        }

        private ServerPlayer nearest() {
            ServerPlayer nearest = null;
            for (ServerPlayer player : level.players()) {
                if (player.isAlive() && (nearest == null || distance(player) < distance(nearest))) {
                    nearest = player;
                }
            }
            return nearest;
        }

        // To the boss once it is awake, otherwise to where it will wake up
        private double distance(ServerPlayer player) {
            return Math.sqrt(Mth.square(player.getX() - x) + Mth.square(player.getZ() - z));
        }

        // E.g. "nord-øst", from the player towards the boss
        private String direction(ServerPlayer player) {
            double angle = Math.toDegrees(Math.atan2(z - player.getZ(), x - player.getX()));
            // 0 degrees is east and 90 is south, since z goes south in Minecraft
            String[] names = {"øst", "syd-øst", "syd", "syd-vest", "vest", "nord-vest", "nord", "nord-øst"};
            return names[Math.floorMod((int) Math.round(angle / 45), 8)];
        }

        private void broadcast(Component message) {
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    // Seconds as minutes and seconds, e.g. "4:05"
    private static String time(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }
}
