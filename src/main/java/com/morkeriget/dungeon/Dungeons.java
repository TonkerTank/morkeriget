package com.morkeriget.dungeon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.progress.Progression;
import com.morkeriget.rift.Rifts;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Dungeon keys open a dungeon, like the dungeons in Diablo 4: you and the players near you are taken to a dungeon built
// for you in its own dark dimension. It is a row of stone rooms, and the monsters in a room come out when you step into
// it. The room before the boss is sealed off with bars until its elite and the monsters with it are dead, and the boss
// waits in the last room. Killing it gives loot and a key for the next tier, and sends you home a little later.
// Dungeons are only kept in memory, so after a restart players in the dungeon dimension are sent back where they came from
public final class Dungeons {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "dungeon"));
    static final int MAX_TIER = 10;
    private static final String TIER_TAG = "morkeriget_dungeon_tier";
    static final String MONSTER_TAG = "morkeriget_dungeon";
    // Where each player was before the dungeon, so they can be sent back there
    private static final String RETURN_TAG = "morkeriget_dungeon_return";
    // The inventory of a player who is dying in a dungeon, and the one to give back once the death has happened
    private static final String DYING_ITEMS_TAG = "morkeriget_dungeon_dying_items";
    private static final String KEPT_ITEMS_TAG = "morkeriget_dungeon_kept_items";
    // Players this close to the one using the key come along
    private static final double PARTY_RANGE = 8.0;
    // Keys drop from bosses and elites outside dungeons. Inside, the boss gives one for the next tier
    private static final double BOSS_KEY_CHANCE = 0.5;
    private static final double ELITE_KEY_CHANCE = 0.05;
    // A dungeon boss loses at most this share of its max health in one hit, so even a very strong group needs a while
    private static final float BOSS_MAX_HIT = 0.05f;
    // Dungeon rooms are full of monsters, so each one gives half the XP it would outside
    private static final double MONSTER_XP = 0.5;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> KEY = ITEMS.registerItem("dungeon_key", DungeonKeyItem::new,
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    private static final List<Dungeon> ACTIVE = new ArrayList<>();

    private Dungeons() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(Dungeons::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(Dungeons::onServerTick);
        NeoForge.EVENT_BUS.addListener(Dungeons::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Dungeons::onTravel);
        NeoForge.EVENT_BUS.addListener(Dungeons::onConversion);
        NeoForge.EVENT_BUS.addListener(Dungeons::onDeath);
        NeoForge.EVENT_BUS.addListener(Dungeons::onDrops);
        NeoForge.EVENT_BUS.addListener(Dungeons::onPlayerDrops);
        NeoForge.EVENT_BUS.addListener(Dungeons::onClone);
        NeoForge.EVENT_BUS.addListener(Dungeons::onRespawn);
        NeoForge.EVENT_BUS.addListener(Dungeons::onLogin);
        NeoForge.EVENT_BUS.addListener(Dungeons::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(Dungeons::onBreak);
        NeoForge.EVENT_BUS.addListener(Dungeons::onPlace);
        NeoForge.EVENT_BUS.addListener(Dungeons::onExplosion);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Dungeons::onBossDamage);
        NeoForge.EVENT_BUS.addListener(Dungeons::registerCommands);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ACTIVE.clear());
    }

    // "/morkeriget dungeon 7" gives a key of that tier, for testing. Needs cheats
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("dungeon")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("tier", IntegerArgumentType.integer(1, MAX_TIER)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack key = key(IntegerArgumentType.getInteger(context, "tier"));
                    // The name before the stack goes into the inventory, which empties it
                    Component name = key.getHoverName();
                    player.getInventory().placeItemBackInInventory(key);
                    context.getSource().sendSuccess(() -> Component.literal("Du fik ").append(name), false);
                    return 1;
                }))));
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(KEY);
        }
    }

    // A key of this tier. Keys without a tier count as tier I
    public static ItemStack key(int tier) {
        ItemStack stack = new ItemStack(KEY.get());
        if (tier > 1) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TIER_TAG, Math.min(MAX_TIER, tier)));
        }
        return stack;
    }

    static int tier(ItemStack key) {
        int tier = key.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TIER_TAG);
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    public static boolean isInDungeon(Player player) {
        return player.level().dimension() == LEVEL;
    }

    // Returns false if the dungeon couldn't open, so the key isn't used up
    static boolean start(ServerPlayer player, int tier) {
        ServerLevel dungeonLevel = player.server.getLevel(LEVEL);
        if (dungeonLevel == null) {
            player.displayClientMessage(Component.literal("Dungeon-dimensionen mangler i denne verden"), true);
            return false;
        }
        if (isInDungeon(player)) {
            player.displayClientMessage(Component.literal("Du er allerede i et dungeon"), true);
            return false;
        }
        if (Rifts.isInRift(player)) {
            player.displayClientMessage(Component.literal("Luk riften først"), true);
            return false;
        }
        // Monsters are taken away right away on Peaceful
        if (player.serverLevel().getDifficulty() == Difficulty.PEACEFUL) {
            player.displayClientMessage(Component.literal("Dungeons virker ikke på Peaceful"), true);
            return false;
        }
        List<ServerPlayer> party = new ArrayList<>();
        party.add(player);
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other != player && other.isAlive() && !other.isSpectator() && other.distanceTo(player) <= PARTY_RANGE && !Rifts.isInRift(other)) {
                party.add(other);
            }
        }
        // As hard as for the strongest of them, plus a level for each tier above I
        int monsterLevel = 1;
        for (ServerPlayer member : party) {
            monsterLevel = Math.max(monsterLevel, Progression.monsterLevel(member));
        }
        Dungeon dungeon = new Dungeon(dungeonLevel, freeSlot(), tier, monsterLevel + tier - 1, party);
        dungeon.build(player.getRandom());
        ACTIVE.add(dungeon);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);
        for (ServerPlayer member : party) {
            saveReturn(member);
            dungeon.toEntrance(member);
            member.sendSystemMessage(Component.literal("Dungeon " + Rifts.roman(tier) + "! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                    .append(Component.literal("Ryd rummene, bryd seglet og dræb bossen i det sidste rum. I har " + Dungeon.LIVES
                            + " liv tilsammen.").withStyle(ChatFormatting.RED)));
            member.sendSystemMessage(Component.literal("Lodestenen ved indgangen fører dig ud igen.").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    // Each dungeon is built in its own place, far enough from the others that they never meet. A place is used again
    // once its dungeon is over, and building over it takes away what was there
    private static int freeSlot() {
        int slot = 0;
        while (true) {
            int candidate = slot;
            if (ACTIVE.stream().noneMatch(dungeon -> dungeon.slot == candidate)) {
                return slot;
            }
            slot++;
        }
    }

    static Dungeon dungeonOf(Player player) {
        for (Dungeon dungeon : ACTIVE) {
            if (dungeon.members.contains(player.getUUID())) {
                return dungeon;
            }
        }
        return null;
    }

    private static Dungeon dungeonWith(Entity monster) {
        for (Dungeon dungeon : ACTIVE) {
            if (dungeon.owns(monster.getUUID())) {
                return dungeon;
            }
        }
        return null;
    }

    private static void saveReturn(ServerPlayer player) {
        CompoundTag back = new CompoundTag();
        back.putString("dimension", player.level().dimension().location().toString());
        back.putDouble("x", player.getX());
        back.putDouble("y", player.getY());
        back.putDouble("z", player.getZ());
        player.getPersistentData().put(RETURN_TAG, back);
    }

    // Back to where the player was before the dungeon, or the world spawn if that is gone
    static void sendBack(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag back = data.getCompound(RETURN_TAG);
        data.remove(RETURN_TAG);
        if (!isInDungeon(player)) {
            return;
        }
        ServerLevel level = back.contains("dimension")
                ? player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(back.getString("dimension"))))
                : null;
        Vec3 to;
        if (level != null && level.dimension() != LEVEL) {
            to = new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z"));
        } else {
            level = player.server.overworld();
            to = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos()));
        }
        player.serverLevel().sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.8, 0.4, 0.5);
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Dungeon> dungeons = ACTIVE.iterator();
        while (dungeons.hasNext()) {
            Dungeon dungeon = dungeons.next();
            if (dungeon.tick()) {
                dungeons.remove();
                dungeon.close();
            }
        }
    }

    // Dungeon monsters are saved with the world, but the dungeon isn't. Ones left over from before a restart are removed
    private static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity().getPersistentData().getBoolean(MONSTER_TAG)
                && dungeonWith(event.getEntity()) == null) {
            event.setCanceled(true);
        }
    }

    // How much of a monster's XP to give, since XP comes from its health (see Progression). The extra health from the
    // dungeon tier doesn't count, and dungeon monsters give half
    public static double xpScale(LivingEntity monster) {
        if (!monster.getPersistentData().getBoolean(MONSTER_TAG)) {
            return 1;
        }
        AttributeInstance health = monster.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier boost = health != null ? health.getModifier(Dungeon.TIER_BOOST) : null;
        return MONSTER_XP / (boost != null ? 1 + boost.amount() : 1);
    }

    private static void onBossDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentData().getBoolean(MONSTER_TAG) && Bosses.isBoss(entity)) {
            event.setNewDamage(Math.min(event.getNewDamage(), entity.getMaxHealth() * BOSS_MAX_HIT));
        }
    }

    // Monsters stay in their dungeon
    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity().getPersistentData().getBoolean(MONSTER_TAG)) {
            event.setCanceled(true);
        }
    }

    // A husk that turns into a zombie would come back as a monster the dungeon doesn't know
    private static void onConversion(LivingConversionEvent.Pre event) {
        if (event.getEntity().getPersistentData().getBoolean(MONSTER_TAG)) {
            event.setCanceled(true);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        // What a player carries is saved when they die, and only kept if the death is in a dungeon and really happens
        if (entity instanceof ServerPlayer player) {
            player.getPersistentData().remove(DYING_ITEMS_TAG);
            if (isInDungeon(player)) {
                player.getPersistentData().put(DYING_ITEMS_TAG, player.getInventory().save(new ListTag()));
            }
        }
        if (entity.getPersistentData().getBoolean(MONSTER_TAG)) {
            Dungeon dungeon = dungeonWith(entity);
            if (dungeon != null) {
                dungeon.monsterDied(entity);
            }
        } else if (entity instanceof ServerPlayer player && isInDungeon(player)) {
            Dungeon dungeon = dungeonOf(player);
            if (dungeon != null) {
                dungeon.playerDied(player);
            }
        }
    }

    // Dying in a dungeon never costs you your things, like in Diablo 4: nothing is dropped, and everything is put back in
    // the same slots when you respawn, see onClone. It is saved with the player, so it survives a restart before respawning
    private static void onPlayerDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInDungeon(player) && player.getPersistentData().contains(DYING_ITEMS_TAG)) {
            CompoundTag data = player.getPersistentData();
            data.put(KEPT_ITEMS_TAG, data.get(DYING_ITEMS_TAG));
            data.remove(DYING_ITEMS_TAG);
            event.setCanceled(true);
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (event.isWasDeath() && old.contains(KEPT_ITEMS_TAG)) {
            event.getEntity().getInventory().load(old.getList(KEPT_ITEMS_TAG, Tag.TAG_COMPOUND));
        }
    }

    // Keys drop outside dungeons, so dungeons don't pay for themselves
    private static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel) || !(entity.getKillCredit() instanceof Player)
                || entity.getPersistentData().getBoolean(MONSTER_TAG)) {
            return;
        }
        double chance = Bosses.isBoss(entity) ? BOSS_KEY_CHANCE : Elites.isElite(entity) ? ELITE_KEY_CHANCE : 0;
        if (entity.getRandom().nextDouble() < chance) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), key(1)));
        }
    }

    // A player who died comes back at the entrance while the group still has lives left
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered()) {
            return;
        }
        Dungeon dungeon = dungeonOf(player);
        if (dungeon != null && dungeon.isOpen()) {
            dungeon.toEntrance(player);
        } else {
            player.getPersistentData().remove(RETURN_TAG);
        }
    }

    // After a restart, or if the dungeon ended while the player was away
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isInDungeon(player) && dungeonOf(player) == null) {
            sendBack(player);
            player.displayClientMessage(Component.literal("Dungeonet var lukket, så du er tilbage, hvor du kom fra").withStyle(ChatFormatting.GRAY), false);
        }
    }

    // The lodestone at the entrance is the way out
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isInDungeon(player)) {
            return;
        }
        Dungeon dungeon = dungeonOf(player);
        BlockPos pos = event.getPos();
        if (dungeon != null ? dungeon.isExit(pos) : player.level().getBlockState(pos).is(Blocks.LODESTONE)) {
            event.setCanceled(true);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                if (dungeon != null) {
                    dungeon.leave(player);
                } else {
                    sendBack(player);
                }
            }
        }
    }

    // The dungeon can't be dug through or built in, except in creative
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer().level().dimension() == LEVEL && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player && player.level().dimension() == LEVEL && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() == LEVEL) {
            event.getAffectedBlocks().clear();
        }
    }
}
