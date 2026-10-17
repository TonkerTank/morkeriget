package com.morkeriget.rift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// A rift stone opens a rift where the player stands: waves of monsters come out around them, and after the last wave
// the rift's guardian, a boss. Killing it gives extra loot, essence and XP. The rift closes if the player dies,
// logs out, walks too far away or runs out of time
public final class Rifts {
    static final int WAVES = 3;
    private static final String RIFT_TAG = "morkeriget_rift";
    // 20 ticks is one second
    private static final int TIME_LIMIT = 5 * 60 * 20;
    private static final int WAVE_PAUSE = 3 * 20;
    private static final double MAX_DISTANCE = 48.0;
    private static final double ELITE_CHANCE = 0.08;
    private static final double ELITE_STONE_CHANCE = 0.1;
    // Monsters that don't burn in the sun or stop attacking in daylight. Husks are in twice, so there are more of them
    private static final List<EntityType<? extends Monster>> MONSTERS = List.of(
            EntityType.HUSK, EntityType.HUSK, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITCH);
    private static final List<EntityType<? extends Monster>> GUARDIANS = List.of(EntityType.HUSK, EntityType.VINDICATOR);

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> RIFT_STONE = ITEMS.registerItem("rift_stone", RiftStoneItem::new,
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    // The rift each player is in. Only kept in memory, so rifts are gone if the server stops
    private static final Map<UUID, Rift> RIFTS = new HashMap<>();

    private Rifts() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(Rifts::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(Rifts::onServerTick);
        NeoForge.EVENT_BUS.addListener(Rifts::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Rifts::onDrops);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> RIFTS.clear());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(RIFT_STONE);
        }
    }

    // Returns false if the rift couldn't open, so the stone isn't used up
    static boolean start(ServerPlayer player) {
        if (RIFTS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.literal("Du er allerede i en rift"), true);
            return false;
        }
        Rift rift = new Rift(player);
        RIFTS.put(player.getUUID(), rift);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 150, 2, 1, 2, 0.1);
        player.sendSystemMessage(Component.literal("Riften åbner sig! ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("Overlev " + WAVES + " bølger og dræb riftens vogter.").withStyle(ChatFormatting.LIGHT_PURPLE)));
        return true;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Rift> rifts = RIFTS.values().iterator();
        while (rifts.hasNext()) {
            Rift rift = rifts.next();
            if (rift.tick()) {
                rift.bar.removeAllPlayers();
                rifts.remove();
            }
        }
    }

    // Rift monsters are saved with the world, but the rift isn't. When the world is loaded again they would just
    // stand around, so they are removed
    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.getEntity().getPersistentData().getBoolean(RIFT_TAG)) {
            return;
        }
        UUID id = event.getEntity().getUUID();
        if (RIFTS.values().stream().noneMatch(rift -> rift.alive.contains(id))) {
            event.setCanceled(true);
        }
    }

    // Bosses always drop a rift stone and elites sometimes do. Not the ones in a rift, so rifts don't pay for themselves
    private static void onDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !(event.getEntity().getKillCredit() instanceof Player)
                || entity.getPersistentData().getBoolean(RIFT_TAG)) {
            return;
        }
        boolean drop = Bosses.isBoss(entity) || (Elites.isElite(entity) && entity.getRandom().nextDouble() < ELITE_STONE_CHANCE);
        if (drop) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), new ItemStack(RIFT_STONE.get())));
        }
    }

    private static final class Rift {
        private final ServerLevel level;
        private final UUID playerId;
        private final Vec3 center;
        private final int monsterLevel;
        // The monsters of the current wave that are still alive
        private final Set<UUID> alive = new HashSet<>();
        private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Rift"),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        // 0 before the first wave, then 1 to WAVES, and WAVES + 1 is the guardian
        private int wave;
        private int waveSize;
        private int pause = WAVE_PAUSE;
        private int ticks;

        Rift(ServerPlayer player) {
            level = player.serverLevel();
            playerId = player.getUUID();
            center = player.position();
            // The player's own level. Rifts are already harder than outside because of how many monsters come at once
            monsterLevel = Progression.level(player);
            bar.setName(Component.literal("Riften åbner sig...").withStyle(ChatFormatting.LIGHT_PURPLE));
            bar.addPlayer(player);
        }

        // Returns true when the rift is over
        boolean tick() {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive()) {
                return fail(player, "Du faldt - riften lukkede");
            }
            if (player.level() != level || player.position().distanceTo(center) > MAX_DISTANCE) {
                return fail(player, "Du gik for langt væk - riften lukkede");
            }
            if (++ticks > TIME_LIMIT) {
                return fail(player, "Tiden løb ud - riften lukkede");
            }
            if (pause > 0) {
                if (--pause == 0) {
                    nextWave(player);
                }
                return false;
            }
            alive.removeIf(id -> {
                Entity entity = level.getEntity(id);
                return entity == null || !entity.isAlive();
            });
            bar.setProgress(waveSize > 0 ? (float) alive.size() / waveSize : 0);
            if (!alive.isEmpty()) {
                return false;
            }
            if (wave > WAVES) {
                complete(player);
                return true;
            }
            player.displayClientMessage(Component.literal("Bølge " + wave + " klaret!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            pause = WAVE_PAUSE;
            return false;
        }

        private void nextWave(ServerPlayer player) {
            wave++;
            RandomSource random = level.getRandom();
            if (wave > WAVES) {
                spawn(player, GUARDIANS.get(random.nextInt(GUARDIANS.size())), true, false);
                bar.setName(Component.literal("Riftens vogter").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
            } else {
                int count = 3 + wave * 2;
                for (int i = 0; i < count; i++) {
                    spawn(player, MONSTERS.get(random.nextInt(MONSTERS.size())), false, random.nextDouble() < ELITE_CHANCE);
                }
                bar.setName(Component.literal("Rift - bølge " + wave + "/" + WAVES).withStyle(ChatFormatting.LIGHT_PURPLE));
                level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 0.8f);
            }
            waveSize = alive.size();
            bar.setProgress(1);
        }

        private void spawn(ServerPlayer player, EntityType<? extends Monster> type, boolean guardian, boolean elite) {
            Monster monster = type.create(level);
            if (monster == null) {
                return;
            }
            Vec3 pos = findSpot(monster);
            monster.moveTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
            // Gives it its normal gear, like a crossbow for pillagers
            monster.finalizeSpawn(level, level.getCurrentDifficultyAt(monster.blockPosition()), MobSpawnType.EVENT, null);
            Elites.markChecked(monster);
            if (Config.MONSTER_LEVELS.get()) {
                MonsterLevels.apply(monster, guardian ? monsterLevel + 1 : monsterLevel);
            }
            if (guardian) {
                Bosses.makeBoss(monster);
            } else if (elite) {
                Elites.makeElite(monster, 1);
            }
            monster.getPersistentData().putBoolean(RIFT_TAG, true);
            monster.setPersistenceRequired();
            monster.setTarget(player);
            // In the list before it joins the world, or onJoinLevel would think it is left over from an old rift
            alive.add(monster.getUUID());
            level.addFreshEntity(monster);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 25, 0.3, 0.6, 0.3, 0.05);
        }

        // A free spot 6 to 12 blocks from the center with ground under it. Falls back to the center itself
        private Vec3 findSpot(Monster monster) {
            RandomSource random = level.getRandom();
            for (int attempt = 0; attempt < 20; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 6 + random.nextDouble() * 6;
                double x = center.x + Math.cos(angle) * distance;
                double z = center.z + Math.sin(angle) * distance;
                for (int dy = 4; dy >= -4; dy--) {
                    double y = Math.floor(center.y) + dy;
                    monster.moveTo(x, y, z);
                    if (level.noCollision(monster) && !level.containsAnyLiquid(monster.getBoundingBox())
                            && !level.noCollision(monster, monster.getBoundingBox().move(0, -0.1, 0))) {
                        return new Vec3(x, y, z);
                    }
                }
            }
            return center;
        }

        private void complete(ServerPlayer player) {
            RandomSource random = level.getRandom();
            List<ItemStack> rewards = new ArrayList<>();
            // One set piece and one normal item
            rewards.add(Loot.createSet(random, monsterLevel));
            rewards.add(Loot.create(random, Rarity.roll(random, Rarity.MAGIC), monsterLevel));
            rewards.add(new ItemStack(Smithy.ESSENCE.get(), 5 + random.nextInt(6)));
            for (ItemStack reward : rewards) {
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), reward));
            }
            Progression.addXp(player, 100 + 20 * monsterLevel);

            player.sendSystemMessage(Component.literal("Riften er lukket! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Du fik en sæt-genstand, ekstra loot, essens og XP.").withStyle(ChatFormatting.YELLOW)));
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 80, 0.5, 1, 0.5, 0.4);
        }

        // Takes the rift's monsters back out of the world. Always returns true, so tick can return it
        private boolean fail(ServerPlayer player, String message) {
            for (UUID id : alive) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
                    entity.discard();
                }
            }
            alive.clear();
            if (player != null) {
                player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
            }
            return true;
        }
    }
}
