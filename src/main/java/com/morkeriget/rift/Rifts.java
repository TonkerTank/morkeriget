package com.morkeriget.rift;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.bounty.Bounties;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.journey.Journey;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.portal.TownPortal;
import com.morkeriget.progress.Progression;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// A rift stone opens a rift where the player stands: waves of monsters come out around them, and after the last wave
// the rift's guardian, a boss. Killing it gives extra loot, essence and XP. The rift closes if the player dies,
// logs out, walks too far away or runs out of time
public final class Rifts {
    static final int WAVES = 3;
    // Rift stones have a tier. Each tier above I makes the monsters a level higher, adds more of them and more elites,
    // and gives better rewards. Closing a rift gives a stone for the next tier
    static final int MAX_TIER = 10;
    private static final String TIER_TAG = "morkeriget_rift_tier";
    private static final String RIFT_TAG = "morkeriget_rift";
    // 20 ticks is one second
    private static final int TIME_LIMIT = 5 * 60 * 20;
    private static final int WAVE_PAUSE = 3 * 20;
    private static final double MAX_DISTANCE = 48.0;
    // Every 5 seconds, monsters further than this from the player are moved back to them
    private static final int RECALL_INTERVAL = 5 * 20;
    private static final double RECALL_DISTANCE = 20.0;
    private static final double ELITE_CHANCE = 0.08;
    private static final double ELITE_STONE_CHANCE = 0.1;
    // The affixes: how long the warning is before an explosion or meteor goes off, how big they are and how much of
    // the player's max health they take
    private static final int HAZARD_WARNING = 30;
    private static final double EXPLOSION_RADIUS = 3.0;
    private static final float EXPLOSION_DAMAGE = 0.15f;
    private static final int METEOR_INTERVAL = 7 * 20;
    private static final double METEOR_RADIUS = 2.5;
    private static final float METEOR_DAMAGE = 0.25f;
    private static final ResourceLocation AFFIX_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "rift_affix");
    // Monsters that don't burn in the sun or stop attacking in daylight. Husks are in twice, so there are more of them
    private static final List<EntityType<? extends Monster>> MONSTERS = List.of(
            EntityType.HUSK, EntityType.HUSK, EntityType.VINDICATOR, EntityType.PILLAGER, EntityType.WITCH);
    private static final List<EntityType<? extends Monster>> GUARDIANS = List.of(EntityType.HUSK, EntityType.VINDICATOR);

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> RIFT_STONE = ITEMS.registerItem("rift_stone", RiftStoneItem::new,
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC));

    // The rift each player is in. Only kept in memory, so rifts are gone if the server stops
    private static final Map<UUID, Rift> RIFTS = new HashMap<>();

    // Each player's best time in ticks for each tier, with the tier as a string ("1" to "10"), since map keys in
    // saved data have to be strings. Kept when the player dies
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<Map<String, Integer>>> RECORDS = ATTACHMENTS.register("rift_records",
            () -> AttachmentType.<Map<String, Integer>>builder(() -> Map.of())
                    .serialize(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath().build());
    // Closing a rift in less than this gets more essence
    private static final int FAST_TIME = TIME_LIMIT / 2;

    private Rifts() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(Rifts::addToCreativeTab);
        NeoForge.EVENT_BUS.addListener(Rifts::onServerTick);
        NeoForge.EVENT_BUS.addListener(Rifts::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Rifts::onDrops);
        // Before the boss limit on how hard one hit can be, so Furious can't push the guardian past it
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Rifts::onDamagePre);
        NeoForge.EVENT_BUS.addListener(Rifts::onConversion);
        NeoForge.EVENT_BUS.addListener(Rifts::onLogout);
        NeoForge.EVENT_BUS.addListener(Rifts::onDamagePost);
        NeoForge.EVENT_BUS.addListener(Rifts::onHeal);
        NeoForge.EVENT_BUS.addListener(Rifts::onDeath);
        NeoForge.EVENT_BUS.addListener(Rifts::registerCommands);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> RIFTS.clear());
    }

    // "/morkeriget rift 7" gives a rift stone of that tier with random affixes, for testing. Needs cheats
    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(Morkeriget.MODID).then(Commands.literal("rift")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("tier", IntegerArgumentType.integer(1, MAX_TIER)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ItemStack stone = stone(IntegerArgumentType.getInteger(context, "tier"), player.getRandom());
                    // The name before the stack goes into the inventory, which empties it
                    Component name = stone.getHoverName();
                    player.getInventory().placeItemBackInInventory(stone);
                    context.getSource().sendSuccess(() -> Component.literal("Du fik ").append(name), false);
                    return 1;
                }))));
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(RIFT_STONE);
        }
    }

    public static boolean isInRift(ServerPlayer player) {
        return RIFTS.containsKey(player.getUUID());
    }

    // A rift stone of this tier. Stones without a tier, like crafted ones, are tier I
    public static ItemStack stone(int tier) {
        ItemStack stack = new ItemStack(RIFT_STONE.get());
        if (tier > 1) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(TIER_TAG, Math.min(MAX_TIER, tier)));
        }
        return stack;
    }

    // A rift stone of this tier with random affixes, like the ones closing a rift gives
    static ItemStack stone(int tier, RandomSource random) {
        ItemStack stack = stone(tier);
        RiftAffix.markOn(stack, RiftAffix.roll(random, tier));
        return stack;
    }

    static int tier(ItemStack stone) {
        int tier = stone.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TIER_TAG);
        return Math.max(1, Math.min(MAX_TIER, tier));
    }

    static String roman(int tier) {
        return List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").get(Math.max(1, Math.min(MAX_TIER, tier)) - 1);
    }

    // Ticks as minutes and seconds, e.g. "2:05"
    static String time(int ticks) {
        int seconds = (ticks + 19) / 20;
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    // The player's best time for the tier in ticks, or 0 if they haven't closed it yet
    private static int record(ServerPlayer player, int tier) {
        return player.getData(RECORDS).getOrDefault(String.valueOf(tier), 0);
    }

    private static void setRecord(ServerPlayer player, int tier, int ticks) {
        Map<String, Integer> records = new HashMap<>(player.getData(RECORDS));
        records.put(String.valueOf(tier), ticks);
        player.setData(RECORDS, Map.copyOf(records));
    }

    // Returns false if the rift couldn't open, so the stone isn't used up
    static boolean start(ServerPlayer player, int tier, List<RiftAffix> affixes) {
        if (RIFTS.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.literal("Du er allerede i en rift"), true);
            return false;
        }
        // Monsters are taken away right away on Peaceful
        if (player.serverLevel().getDifficulty() == Difficulty.PEACEFUL) {
            player.displayClientMessage(Component.literal("Rifts virker ikke på Peaceful"), true);
            return false;
        }
        Rift rift = new Rift(player, tier, affixes);
        RIFTS.put(player.getUUID(), rift);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 150, 2, 1, 2, 0.1);
        player.sendSystemMessage(Component.literal("Rift " + roman(tier) + " åbner sig! ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("Overlev " + WAVES + " bølger og dræb riftens vogter.").withStyle(ChatFormatting.LIGHT_PURPLE)));
        int record = record(player, tier);
        String recordText = record > 0 ? "Din rekord: " + time(record) + ". " : "";
        player.sendSystemMessage(Component.literal(recordText + "Under " + time(FAST_TIME) + " giver 50% mere essens.").withStyle(ChatFormatting.GRAY));
        for (RiftAffix affix : affixes) {
            player.sendSystemMessage(Component.literal("⚠ " + affix.displayName + ": ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(affix.description).withStyle(ChatFormatting.GRAY)));
        }
        return true;
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Rift> rifts = RIFTS.values().iterator();
        while (rifts.hasNext()) {
            Rift rift = rifts.next();
            if (rift.tick()) {
                rift.bar.removeAllPlayers();
                rift.giveBackFood();
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

    // Something that goes off at a place in a rift after a warning: an explosion from a dead monster or a meteor
    private record Hazard(Vec3 pos, int at, double radius, float damage, boolean meteor) {
    }

    private static Rift riftWith(Entity monster) {
        for (Rift rift : RIFTS.values()) {
            if (rift.alive.contains(monster.getUUID())) {
                return rift;
            }
        }
        return null;
    }

    // Furious: more damage from the rift's own monsters, also from their arrows and potions
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        Rift rift = RIFTS.get(event.getEntity().getUUID());
        Entity attacker = event.getSource().getEntity();
        if (rift != null && attacker != null && rift.alive.contains(attacker.getUUID()) && rift.has(RiftAffix.FURIOUS)) {
            event.setNewDamage(event.getNewDamage() * 1.25f);
        }
    }

    // Vampiric: a monster from the rift that hurts its player heals 5% of its health
    private static void onDamagePost(LivingDamageEvent.Post event) {
        Rift rift = RIFTS.get(event.getEntity().getUUID());
        if (rift != null && event.getNewDamage() > 0 && event.getSource().getEntity() instanceof LivingEntity attacker
                && rift.alive.contains(attacker.getUUID()) && rift.has(RiftAffix.VAMPIRIC)) {
            attacker.heal(attacker.getMaxHealth() * 0.05f);
            rift.level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, attacker.getX(), attacker.getY() + attacker.getBbHeight(), attacker.getZ(),
                    4, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // Cursed Ground: the player heals half as much
    private static void onHeal(LivingHealEvent event) {
        Rift rift = RIFTS.get(event.getEntity().getUUID());
        if (rift != null && rift.has(RiftAffix.CURSED_GROUND)) {
            event.setAmount(event.getAmount() * 0.5f);
        }
    }

    // Explosive: a rift monster that dies blows up a moment later
    private static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().getPersistentData().getBoolean(RIFT_TAG)) {
            return;
        }
        Rift rift = riftWith(event.getEntity());
        if (rift == null) {
            return;
        }
        rift.died.add(event.getEntity().getUUID());
        if (rift.has(RiftAffix.EXPLOSIVE)) {
            rift.explodeLater(event.getEntity());
        }
    }

    // A husk that turns into a zombie in water would come back as a new monster the rift doesn't know, so it can't
    private static void onConversion(LivingConversionEvent.Pre event) {
        if (event.getEntity().getPersistentData().getBoolean(RIFT_TAG)) {
            event.setCanceled(true);
        }
    }

    // The rift closes, and the player gets their food from before it back, since it is saved with them
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Rift rift = RIFTS.remove(event.getEntity().getUUID());
        if (rift != null && event.getEntity() instanceof ServerPlayer player) {
            rift.bar.removeAllPlayers();
            rift.giveBackFood(player);
            rift.fail(null, null);
        }
    }

    private static final class Rift {
        private final ServerLevel level;
        private final UUID playerId;
        private final Vec3 center;
        private final int tier;
        private final int monsterLevel;
        // The player's food from before the rift, given back when it ends, so a rift neither costs nor gives food
        private final int foodBefore;
        private final float saturationBefore;
        // The monsters of the current wave that are still alive
        private final Set<UUID> alive = new HashSet<>();
        // The ones that really died, as opposed to just being gone
        private final Set<UUID> died = new HashSet<>();
        private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Rift"),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        // 0 before the first wave, then 1 to WAVES, and WAVES + 1 is the guardian
        private int wave;
        private int waveSize;
        // "Rift II - bølge 1/3", shown on the bar with how many are left
        private String waveName = "";
        private int pause = WAVE_PAUSE;
        private int ticks;
        private final List<RiftAffix> affixes;
        // Explosions from dead monsters and meteors that are on their way, see RiftAffix
        private final List<Hazard> hazards = new ArrayList<>();

        Rift(ServerPlayer player, int tier, List<RiftAffix> affixes) {
            level = player.serverLevel();
            playerId = player.getUUID();
            center = player.position();
            this.tier = tier;
            this.affixes = affixes;
            foodBefore = player.getFoodData().getFoodLevel();
            saturationBefore = player.getFoodData().getSaturationLevel();
            // Tier I is the player's own level, since rifts are already hard because of how many monsters come at once.
            // Every tier above that adds a level. Paragon raises it too, see Progression.monsterLevel
            monsterLevel = Progression.monsterLevel(player) + tier - 1;
            bar.setName(Component.literal("Riften åbner sig...").withStyle(ChatFormatting.LIGHT_PURPLE));
            bar.addPlayer(player);
        }

        // A player who died gets new food when they respawn anyway
        void giveBackFood() {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                giveBackFood(player);
            }
        }

        void giveBackFood(ServerPlayer player) {
            if (player.isAlive()) {
                player.getFoodData().setFoodLevel(foodBefore);
                player.getFoodData().setSaturation(saturationBefore);
            }
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
            // No hunger in a rift: the food bar stays full, so you can always sprint. Saturation is kept at 0,
            // otherwise the fast regeneration from a full food bar would never use anything up
            FoodData food = player.getFoodData();
            food.setFoodLevel(20);
            food.setSaturation(0);
            food.setExhaustion(0);
            tickHazards(player);
            if (pause > 0) {
                if (--pause == 0) {
                    nextWave(player);
                }
                return false;
            }
            if (has(RiftAffix.METEORS) && ticks % METEOR_INTERVAL == 0 && !alive.isEmpty()) {
                hazards.add(new Hazard(player.position(), ticks + HAZARD_WARNING, METEOR_RADIUS, METEOR_DAMAGE, true));
                level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.5f);
            }
            // Only monsters that died count. One that is gone without dying, e.g. taken away on Peaceful, would
            // otherwise let the rift close by itself with all its rewards
            boolean vanished = false;
            for (Iterator<UUID> it = alive.iterator(); it.hasNext(); ) {
                UUID id = it.next();
                Entity entity = level.getEntity(id);
                if (entity == null || !entity.isAlive()) {
                    vanished |= !died.contains(id);
                    it.remove();
                }
            }
            if (vanished) {
                return fail(player, "Riftens monstre forsvandt - riften lukkede");
            }
            bar.setProgress(waveSize > 0 ? (float) alive.size() / waveSize : 0);
            if (!alive.isEmpty()) {
                // The time left in yellow, red in the last minute
                bar.setName(Component.literal(waveName + " - " + alive.size() + " tilbage  ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(time(TIME_LIMIT - ticks))
                                .withStyle(TIME_LIMIT - ticks < 60 * 20 ? ChatFormatting.RED : ChatFormatting.YELLOW)));
                if (ticks % RECALL_INTERVAL == 0) {
                    recallStragglers(player);
                }
                return false;
            }
            if (wave > WAVES) {
                complete(player);
                return true;
            }
            player.displayClientMessage(Component.literal("Bølge " + wave + " klaret!").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            bar.setName(Component.literal(waveName + " - klaret").withStyle(ChatFormatting.LIGHT_PURPLE));
            pause = WAVE_PAUSE;
            return false;
        }

        private void nextWave(ServerPlayer player) {
            wave++;
            RandomSource random = level.getRandom();
            if (wave > WAVES) {
                spawn(player, GUARDIANS.get(random.nextInt(GUARDIANS.size())), true, false);
                waveName = "Rift " + roman(tier) + " - riftens vogter";
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
            } else {
                // One more monster per tier, and 3% more elites per tier
                int count = 3 + wave * 2 + (tier - 1);
                double eliteChance = Math.min(0.4, ELITE_CHANCE + 0.03 * (tier - 1));
                if (has(RiftAffix.ELITE_ARMY)) {
                    eliteChance *= 2;
                }
                for (int i = 0; i < count; i++) {
                    spawn(player, MONSTERS.get(random.nextInt(MONSTERS.size())), false, random.nextDouble() < eliteChance);
                }
                waveName = "Rift " + roman(tier) + " - bølge " + wave + "/" + WAVES;
                level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 0.8f);
            }
            waveSize = alive.size();
            bar.setProgress(1);
        }

        // Monsters that spawned somewhere they can't get out of, or wandered off, would keep the wave from ever ending.
        // Every few seconds the ones far from the player, or far above or below them, are moved back next to the rift
        private void recallStragglers(ServerPlayer player) {
            for (UUID id : alive) {
                if (!(level.getEntity(id) instanceof Monster monster)) {
                    continue;
                }
                boolean far = monster.distanceTo(player) > RECALL_DISTANCE || Math.abs(monster.getY() - player.getY()) > 6;
                if (far) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, monster.getX(), monster.getY() + 1, monster.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
                    Vec3 pos = findSpot(monster, player.position());
                    monster.teleportTo(pos.x, pos.y, pos.z);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 20, 0.3, 0.6, 0.3, 0.05);
                }
                monster.setTarget(player);
            }
        }

        private void spawn(ServerPlayer player, EntityType<? extends Monster> type, boolean guardian, boolean elite) {
            Monster monster = type.create(level);
            if (monster == null) {
                return;
            }
            Vec3 pos = findSpot(monster, center);
            monster.moveTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
            // Gives it its normal gear, like a crossbow for pillagers
            monster.finalizeSpawn(level, level.getCurrentDifficultyAt(monster.blockPosition()), MobSpawnType.EVENT, null);
            Elites.markChecked(monster);
            if (Config.MONSTER_LEVELS.get()) {
                MonsterLevels.apply(monster, guardian ? monsterLevel + 1 : monsterLevel);
            }
            if (guardian) {
                Bosses.makeBoss(monster);
                // The guardian can take 30% of your health in one hit in tier I, and 5% more per tier
                Bosses.setMaxHit(monster, 0.3f + 0.05f * (tier - 1));
            } else if (elite) {
                Elites.makeElite(monster, 1);
            }
            if (has(RiftAffix.ARMORED)) {
                addModifier(monster, Attributes.MAX_HEALTH, 0.4);
                monster.setHealth(monster.getMaxHealth());
            }
            if (has(RiftAffix.SWIFT)) {
                addModifier(monster, Attributes.MOVEMENT_SPEED, 0.25);
            }
            monster.getPersistentData().putBoolean(RIFT_TAG, true);
            monster.setPersistenceRequired();
            // Seen through walls, so you can find the last ones
            monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            monster.setTarget(player);
            // In the list before it joins the world, or onJoinLevel would think it is left over from an old rift
            alive.add(monster.getUUID());
            level.addFreshEntity(monster);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 25, 0.3, 0.6, 0.3, 0.05);
        }

        boolean has(RiftAffix affix) {
            return affixes.contains(affix);
        }

        private static void addModifier(Monster monster, Holder<Attribute> attribute, double amount) {
            AttributeInstance instance = monster.getAttribute(attribute);
            if (instance != null) {
                instance.addPermanentModifier(new AttributeModifier(AFFIX_BOOST, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }

        // A dead monster that is about to explode, from Explosive
        void explodeLater(Entity monster) {
            hazards.add(new Hazard(monster.position(), ticks + HAZARD_WARNING, EXPLOSION_RADIUS, EXPLOSION_DAMAGE, false));
        }

        // Each hazard shows a warning where it will go off, then hurts the player if they are still too close
        private void tickHazards(ServerPlayer player) {
            Iterator<Hazard> pending = hazards.iterator();
            while (pending.hasNext()) {
                Hazard hazard = pending.next();
                Vec3 pos = hazard.pos;
                if (ticks < hazard.at) {
                    if (ticks % 4 == 0) {
                        for (int i = 0; i < 16; i++) {
                            double angle = i * Math.PI * 2 / 16;
                            level.sendParticles(hazard.meteor ? ParticleTypes.FLAME : ParticleTypes.SMOKE, pos.x + Math.cos(angle) * hazard.radius,
                                    pos.y + 0.1, pos.z + Math.sin(angle) * hazard.radius, 1, 0, 0, 0, 0);
                        }
                        if (hazard.meteor) {
                            // The meteor coming down
                            double height = (hazard.at - ticks) * 0.5;
                            level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + height, pos.z, 3, 0.2, 0.2, 0.2, 0);
                        }
                    }
                    continue;
                }
                pending.remove();
                level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 3, hazard.radius / 3, 0.3, hazard.radius / 3, 0);
                if (hazard.meteor) {
                    level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.5, pos.z, 20, hazard.radius / 2, 0.3, hazard.radius / 2, 0);
                }
                level.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, hazard.meteor ? 0.7f : 1.2f);
                if (player.position().distanceTo(pos) <= hazard.radius) {
                    player.hurt(level.damageSources().explosion(null, null), player.getMaxHealth() * hazard.damage);
                }
            }
        }

        // A free spot 6 to 12 blocks from around with ground under it. Falls back to around itself
        private Vec3 findSpot(Monster monster, Vec3 around) {
            RandomSource random = level.getRandom();
            for (int attempt = 0; attempt < 20; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 6 + random.nextDouble() * 6;
                double x = around.x + Math.cos(angle) * distance;
                double z = around.z + Math.sin(angle) * distance;
                for (int dy = 4; dy >= -4; dy--) {
                    double y = Math.floor(around.y) + dy;
                    monster.moveTo(x, y, z);
                    if (level.noCollision(monster) && !level.containsAnyLiquid(monster.getBoundingBox())
                            && !level.noCollision(monster, monster.getBoundingBox().move(0, -0.1, 0)) && canSee(monster, around)) {
                        return new Vec3(x, y, z);
                    }
                }
            }
            return around;
        }

        // So monsters don't spawn in a cave under the player or behind a wall, where they can't get to them
        private boolean canSee(Monster monster, Vec3 around) {
            Vec3 eyes = monster.getEyePosition();
            Vec3 target = around.add(0, 1, 0);
            return level.clip(new ClipContext(eyes, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, monster)).getType()
                    == HitResult.Type.MISS;
        }

        private void complete(ServerPlayer player) {
            RandomSource random = level.getRandom();
            List<ItemStack> rewards = new ArrayList<>();
            // A set piece, one normal item more for every third tier (at least rare from tier V), more essence per tier,
            // and a stone for the next tier
            rewards.add(Loot.createSet(random, monsterLevel));
            // Each affix gives an item more and 25% more essence, XP and chance of a mythic
            double affixBonus = 1 + RiftAffix.REWARD_PER_AFFIX * affixes.size();
            // Higher tiers have a better chance of a mythic item
            if (random.nextDouble() < Config.MYTHIC_CHANCE_RIFT.get() * tier * affixBonus) {
                ItemStack mythic = Loot.createMythic(random, monsterLevel, Progression.playerClass(player));
                rewards.add(mythic);
                Loot.announceMythic(player, mythic);
            }
            Rarity minimum = tier >= 5 ? Rarity.RARE : Rarity.MAGIC;
            for (int i = 0; i < 1 + (tier - 1) / 3 + affixes.size(); i++) {
                rewards.add(Loot.create(random, Rarity.roll(random, minimum), monsterLevel, Progression.playerClass(player)));
            }
            // 50% more essence for a fast rift
            boolean fast = ticks < FAST_TIME;
            int essence = (int) Math.round((4 + tier * 2 + random.nextInt(5)) * affixBonus);
            rewards.add(new ItemStack(Smithy.ESSENCE.get(), fast ? essence * 3 / 2 : essence));
            // One gem, and one more for every third tier. Every fourth tier makes them a tier better
            for (int i = 0; i < 1 + tier / 3; i++) {
                rewards.add(Gems.random(random, monsterLevel, tier / 4));
            }
            int nextTier = Math.min(MAX_TIER, tier + 1);
            rewards.add(stone(nextTier, random));
            // A town portal scroll, so you can get home with your loot
            rewards.add(TownPortal.scroll());
            for (ItemStack reward : rewards) {
                level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), reward));
            }
            Progression.addXp(player, (int) ((100 + 20 * monsterLevel) * (1 + 0.25 * (tier - 1)) * affixBonus));
            Bounties.riftClosed(player);
            Journey.riftClosed(player, tier);

            player.sendSystemMessage(Component.literal("Rift " + roman(tier) + " er lukket! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Du fik en sæt-genstand, ekstra loot, ædelsten, essens, XP, en portalrulle og en riftsten "
                            + roman(nextTier) + ".")
                            .withStyle(ChatFormatting.YELLOW)));
            announceTime(player, fast);
            level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 80, 0.5, 1, 0.5, 0.4);
        }

        // The time it took, and whether it beat the player's record for the tier
        private void announceTime(ServerPlayer player, boolean fast) {
            int record = record(player, tier);
            Component line;
            if (record == 0 || ticks < record) {
                setRecord(player, tier, ticks);
                String before = record > 0 ? " (før " + time(record) + ")" : "";
                line = Component.literal("Tid: " + time(ticks) + " - ny rekord!" + before).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.6f);
            } else {
                line = Component.literal("Tid: " + time(ticks) + " (rekord " + time(record) + ")").withStyle(ChatFormatting.GRAY);
            }
            if (fast) {
                line = line.copy().append(Component.literal("  Hurtig-bonus: +50% essens").withStyle(ChatFormatting.AQUA));
            }
            player.sendSystemMessage(line);
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
