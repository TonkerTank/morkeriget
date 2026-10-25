package com.morkeriget.helltide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.PlayerBars;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.journey.Achievement;
import com.morkeriget.journey.Journey;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.Progression;
import com.morkeriget.rift.Rifts;
import com.morkeriget.skill.Minions;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Helltide, like in Diablo 4: every night an area in the overworld is taken over. More and stronger monsters keep
// coming there, and the ones you kill in it drop cinders. Cinders open the helltide chests in the area, which have
// good loot. At dawn the helltide is over and its chests and monsters go away, but you keep your cinders.
// Only kept in memory, so a helltide is over if the server stops
public final class Helltide {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> CINDER = ITEMS.registerSimpleItem("cinder", new Item.Properties());

    private static final String HELLTIDE_TAG = "morkeriget_helltide";
    // Night in Minecraft is from 13000 to 23000 in the day's 24000 ticks
    private static final long NIGHT_START = 13000;
    private static final long NIGHT_END = 23000;
    // How far from the player the helltide starts, and how big it is
    private static final int MIN_DISTANCE = 100;
    private static final int MAX_DISTANCE = 160;
    private static final int RADIUS = 64;
    private static final int CHESTS = 3;
    public static final int CHEST_COST = 50;
    // Extra monsters are spawned until there are this many helltide monsters close to each player in the area
    private static final int MONSTERS_PER_PLAYER = 10;
    private static final double ELITE_CHANCE = 0.08;
    // Helltide monsters are two levels above you and come in big groups, so each one hits a bit softer
    private static final float DAMAGE_SCALE = 0.75f;
    private static final List<EntityType<? extends Monster>> MONSTERS = List.of(EntityType.ZOMBIE, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK, EntityType.STRAY, EntityType.WITCH);

    // The helltide going on now, or null
    private static Active active;

    private Helltide() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(Helltide::onServerTick);
        NeoForge.EVENT_BUS.addListener(Helltide::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Helltide::onDrops);
        NeoForge.EVENT_BUS.addListener(Helltide::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(Helltide::onBreak);
        NeoForge.EVENT_BUS.addListener(Helltide::onServerStopping);
        NeoForge.EVENT_BUS.addListener(Helltide::onDamagePre);
    }

    // Also counts arrows and potions, since the source entity is the monster that shot or threw them
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker.getPersistentData().getBoolean(HELLTIDE_TAG)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_SCALE);
        }
    }

    private static boolean isNight(ServerLevel level) {
        long time = level.getDayTime() % 24000;
        return time >= NIGHT_START && time < NIGHT_END;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        boolean night = isNight(level);
        if (active == null && night && !level.players().isEmpty()) {
            ServerPlayer player = level.players().get(level.getRandom().nextInt(level.players().size()));
            active = new Active(level, player);
        } else if (active != null && (!night || active.level != level)) {
            active.end("Det bliver lyst, og Helltide er ovre");
            active = null;
        } else if (active != null) {
            active.tick();
        }
    }

    // Helltide monsters are saved with the world, but the helltide isn't, so the ones left over are removed
    private static void onJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!event.getLevel().isClientSide() && entity.getPersistentData().getBoolean(HELLTIDE_TAG)
                && (active == null || !active.monsters.contains(entity.getUUID()))) {
            event.setCanceled(true);
        }
    }

    // Monsters killed by a player inside the helltide drop cinders, more for elites and bosses
    private static void onDrops(LivingDropsEvent event) {
        Entity entity = event.getEntity();
        if (active == null || entity.level() != active.level || !(entity instanceof Enemy) || Minions.isAlly(entity)
                || !(event.getEntity().getKillCredit() instanceof ServerPlayer) || !active.contains(entity.blockPosition())) {
            return;
        }
        RandomSource random = entity.getRandom();
        int cinders = Bosses.isBoss(entity) ? 15 : Elites.isElite(entity) ? 5 : 1 + random.nextInt(2);
        event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), new ItemStack(CINDER.get(), cinders)));
    }

    // Right-clicking a helltide chest opens it for cinders instead of like a normal chest
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (active == null || !active.chests.contains(event.getPos()) || event.getLevel() != active.level) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND) {
            active.open(player, event.getPos());
        }
    }

    // Helltide chests can't be broken, only opened
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (active != null && event.getLevel() == active.level && active.chests.contains(event.getPos())) {
            event.setCanceled(true);
        }
    }

    // So the chests and monsters aren't left in the world
    private static void onServerStopping(ServerStoppingEvent event) {
        if (active != null) {
            active.end(null);
            active = null;
        }
    }

    private static final class Active {
        private final ServerLevel level;
        private final BlockPos center;
        private final Set<BlockPos> chests = new HashSet<>();
        private final Set<UUID> monsters = new HashSet<>();
        // How many chests have been put out. That happens when a player comes close, so the chunks are loaded by then
        private int chestsPlaced;
        // One for each player, since it says how far and which way it is for them
        private final PlayerBars bars = new PlayerBars(BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

        Active(ServerLevel level, ServerPlayer player) {
            this.level = level;
            RandomSource random = level.getRandom();
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            this.center = new BlockPos((int) (player.getX() + Math.cos(angle) * distance), 64, (int) (player.getZ() + Math.sin(angle) * distance));
            for (ServerPlayer other : level.players()) {
                other.sendSystemMessage(Component.literal("En Helltide er begyndt! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                        .append(Component.literal("Dræb monstre i den for glødende aske, og brug asken på Helltide-kisterne. Følg den røde bjælke")
                                .withStyle(ChatFormatting.RED)));
                other.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 0.6f);
            }
        }

        boolean contains(BlockPos pos) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            return dx * dx + dz * dz <= RADIUS * RADIUS;
        }

        private double distance(ServerPlayer player) {
            return Math.sqrt(Mth.square(player.getX() - center.getX()) + Mth.square(player.getZ() - center.getZ()));
        }

        // Once a second
        void tick() {
            long time = level.getDayTime() % 24000;
            float progress = Mth.clamp(1 - (float) (time - NIGHT_START) / (NIGHT_END - NIGHT_START), 0, 1);
            for (ServerPlayer player : level.players()) {
                boolean inside = contains(player.blockPosition());
                if (inside) {
                    int cinders = player.getInventory().countItem(CINDER.get());
                    bars.show(player, Component.literal("Helltide - " + cinders + " glødende aske (kiste: " + CHEST_COST + ")"), progress);
                    if (!Rifts.isInRift(player)) {
                        spawnMonsters(player);
                    }
                } else {
                    bars.show(player, Component.literal("Helltide: " + (int) (distance(player) - RADIUS) + " blokke mod " + direction(player)), progress);
                }
                if (chestsPlaced < CHESTS && distance(player) < RADIUS + 48) {
                    placeChests();
                }
            }
            // Players who left the world or the server lose their bar
            bars.finish();
            // A column of flames over each chest, so they can be found
            for (BlockPos chest : chests) {
                level.sendParticles(ParticleTypes.FLAME, chest.getX() + 0.5, chest.getY() + 1.5, chest.getZ() + 0.5, 12, 0.1, 1.5, 0.1, 0.01);
                level.sendParticles(ParticleTypes.LAVA, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0);
            }
            monsters.removeIf(id -> level.getEntity(id) == null);
        }

        // E.g. "nord-øst", from the player towards the middle of the helltide
        private String direction(ServerPlayer player) {
            double angle = Math.toDegrees(Math.atan2(center.getZ() - player.getZ(), center.getX() - player.getX()));
            // 0 degrees is east and 90 is south, since z goes south in Minecraft
            String[] names = {"øst", "syd-øst", "syd", "syd-vest", "vest", "nord-vest", "nord", "nord-øst"};
            return names[Math.floorMod((int) Math.round(angle / 45), 8)];
        }

        // Tried again until all are placed, since some spots can be in chunks that aren't loaded yet, or on water
        private void placeChests() {
            RandomSource random = level.getRandom();
            for (int attempt = 0; attempt < 10 && chestsPlaced < CHESTS; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = random.nextDouble() * (RADIUS - 8);
                BlockPos column = center.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid()) {
                    level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
                    chests.add(pos);
                    chestsPlaced++;
                }
            }
        }

        private void spawnMonsters(ServerPlayer player) {
            long near = monsters.stream().map(level::getEntity).filter(entity -> entity != null && entity.distanceTo(player) < 40).count();
            RandomSource random = level.getRandom();
            for (int i = 0; i < 2 && near + i < MONSTERS_PER_PLAYER; i++) {
                Monster monster = MONSTERS.get(random.nextInt(MONSTERS.size())).create(level);
                if (monster == null) {
                    return;
                }
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 12 + random.nextDouble() * 12;
                BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
                BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
                // Not on water, in a tree or far above or below the player
                if (!contains(pos) || !level.getBlockState(pos.below()).isSolid() || Math.abs(pos.getY() - player.getY()) > 8) {
                    continue;
                }
                monster.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
                if (!level.noCollision(monster)) {
                    continue;
                }
                // Gives it its normal gear, like a bow for skeletons
                monster.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                Elites.markChecked(monster);
                // Two levels above the player
                if (Config.MONSTER_LEVELS.get()) {
                    MonsterLevels.apply(monster, Progression.monsterLevel(player) + 2);
                }
                if (random.nextDouble() < ELITE_CHANCE) {
                    Elites.makeElite(monster, 1);
                }
                monster.getPersistentData().putBoolean(HELLTIDE_TAG, true);
                monster.setTarget(player);
                // In the list before it joins the world, or onJoinLevel would think it is left over
                monsters.add(monster.getUUID());
                level.addFreshEntity(monster);
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.6, 0.3, 0.02);
            }
        }

        void open(ServerPlayer player, BlockPos pos) {
            int cinders = player.getInventory().countItem(CINDER.get());
            if (cinders < CHEST_COST) {
                player.displayClientMessage(Component.literal("Helltide-kisten koster " + CHEST_COST + " glødende aske (du har " + cinders + ")")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            ContainerHelper.clearOrCountMatchingItems(player.getInventory(), item -> item.is(CINDER.get()), CHEST_COST, false);
            chests.remove(pos);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (ItemStack reward : rewards(player)) {
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, reward));
            }
            level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
            level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
            level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0);
            player.displayClientMessage(Component.literal("Helltide-kisten er åbnet! " + chests.size() + " tilbage").withStyle(ChatFormatting.GOLD), true);
            Journey.add(player, Achievement.Stat.HELLTIDE_CHESTS);
        }

        // Two good items with a fair chance of a legendary, essence and a gem, at the player's level
        private List<ItemStack> rewards(ServerPlayer player) {
            RandomSource random = level.getRandom();
            int itemLevel = Progression.level(player);
            List<ItemStack> rewards = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Rarity rarity = random.nextDouble() < 0.3 ? Rarity.LEGENDARY : Rarity.roll(random, Rarity.RARE);
                rewards.add(Loot.create(random, rarity, itemLevel, Progression.playerClass(player)));
            }
            rewards.add(new ItemStack(Smithy.ESSENCE.get(), 10 + random.nextInt(11)));
            rewards.add(Gems.random(random, itemLevel, 0));
            return rewards;
        }

        // message is null when the server stops, since nobody would see it
        void end(String message) {
            for (BlockPos chest : chests) {
                if (level.getBlockState(chest).is(Blocks.CHEST)) {
                    level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
                }
            }
            chests.clear();
            for (UUID id : monsters) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    level.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 1, entity.getZ(), 15, 0.3, 0.6, 0.3, 0.02);
                    entity.discard();
                }
            }
            monsters.clear();
            bars.clear();
            if (message != null) {
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }
}
