package com.morkeriget.dungeon;

import java.util.List;
import java.util.Optional;

import com.morkeriget.Morkeriget;
import com.morkeriget.elite.Bosses;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.Rarity;
import com.morkeriget.rift.Rifts;
import com.morkeriget.smithy.Smithy;
import com.morkeriget.uber.Uber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Dungeons, lives and the fight with Mørkets Herre. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class DungeonTests {
    // Where the rooms are built, see Dungeon
    private static final int FLOOR_Y = 64;
    private static final int CELL = 16;
    private static final int SLOT_SPACING = 512;
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;

    private DungeonTests() {
    }

    @GameTest(template = "empty")
    public static void darkSummonRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack shard = Uber.soulShard();
        ItemStack core = Uber.riftCore();
        CraftingInput all = CraftingInput.of(3, 2, List.of(shard, shard, core, core, Uber.worldHeart(), ItemStack.EMPTY));
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, all, level);
        helper.assertTrue(recipe.isPresent(), "Ingen opskrift på Mørkets Kald");
        helper.assertTrue(recipe.get().value().assemble(all, level.registryAccess()).is(Uber.SUMMON.get()), "Opskriften laver ikke Mørkets Kald");
        CraftingInput missing = CraftingInput.of(3, 2, List.of(shard, shard, core, core, ItemStack.EMPTY, ItemStack.EMPTY));
        helper.assertFalse(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, missing, level).isPresent(),
                "Mørkets Kald kan laves uden Verdens Hjerte");
        helper.succeed();
    }

    // Mørkets Kald opens the arena, the boss is there, /kill works on it and it drops the uber rewards
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void uberFight(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        // So the boss and its helpers don't kill the player before the test is done
        player.getAbilities().invulnerable = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Uber.SUMMON.get(), 2));
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(Dungeons.isInDungeon(player), "Mørkets Kald åbnede ikke arenaen");
        TestPlayers.arrived(player);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Mørkets Kald blev ikke brugt op");
        Dungeon dungeon = Dungeons.dungeonOf(player);
        helper.assertTrue(dungeon != null && dungeon.uber, "Spilleren er ikke i en uber-kamp");
        ServerLevel level = player.serverLevel();
        // The arena is the cell after the entrance, two cells each way and ten blocks high
        int x0 = dungeon.slot * SLOT_SPACING + CELL;
        int middle = CELL;
        helper.assertTrue(level.getBlockState(new BlockPos(x0 + middle, FLOOR_Y, middle)).is(Blocks.CRYING_OBSIDIAN), "Arenaen har ingen midte");
        helper.assertTrue(level.getBlockState(new BlockPos(x0 + middle, FLOOR_Y + 7, middle)).isAir(), "Arenaen er ikke høj nok");
        helper.assertFalse(level.getBlockState(new BlockPos(x0 + middle, FLOOR_Y + 10, middle)).isAir(), "Arenaen har intet loft");
        helper.assertFalse(level.getBlockState(new BlockPos(x0 + 2 * CELL - 1, FLOOR_Y + 1, middle)).isAir(), "Arenaen er ikke 32 blokke bred");
        AABB arena = new AABB(x0, FLOOR_Y, 0, x0 + 2 * CELL, FLOOR_Y + 10, 2 * CELL);
        TestPlayers.moveTo(player, level, new Vec3(x0 + 4.5, FLOOR_Y + 1, 4.5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(boss(level, arena) != null, "Mørkets Herre kom ikke, da spilleren gik ind i arenaen"))
                .thenExecute(() -> {
                    Monster boss = boss(level, arena);
                    helper.assertTrue(boss.getType() == EntityType.WITHER_SKELETON, "Mørkets Herre er ikke et wither-skelet");
                    helper.assertTrue(boss.getName().getString().equals("Mørkets Herre"), "Bossen hedder " + boss.getName().getString());
                    int helpers = level.getEntitiesOfClass(Monster.class, arena, monster -> !Bosses.isBoss(monster)).size();
                    helper.assertTrue(helpers >= 4, "Mørkets Herre har kun " + helpers + " hjælpere");
                    boss.kill();
                    helper.assertTrue(boss.isDeadOrDying(), "/kill dræbte ikke Mørkets Herre (liv: " + boss.getHealth() + "/" + boss.getMaxHealth() + ")");
                })
                .thenWaitUntil(() -> {
                    List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, arena);
                    long legendaries = drops.stream().filter(drop -> Loot.getRarity(drop.getItem()) == Rarity.LEGENDARY).count();
                    helper.assertTrue(legendaries >= 3, "Mørkets Herre gav kun " + legendaries + " legendariske genstande");
                    helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Smithy.ESSENCE.get()) && drop.getItem().getCount() >= 80),
                            "Mørkets Herre gav ikke nok essens");
                })
                .thenExecute(() -> TestPlayers.remove(player))
                .thenSucceed();
    }

    private static Monster boss(ServerLevel level, AABB arena) {
        List<Monster> bosses = level.getEntitiesOfClass(Monster.class, arena, monster -> Bosses.isBoss(monster) && monster.isAlive());
        return bosses.isEmpty() ? null : bosses.get(0);
    }

    // Dying in a dungeon costs exactly one of the shared lives
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void realDeathCostsOneLife(GameTestHelper helper) {
        ServerPlayer player = enterDungeon(helper);
        Dungeon dungeon = Dungeons.dungeonOf(player);
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    player.hurt(player.damageSources().generic(), 1000);
                    helper.assertTrue(player.isDeadOrDying(), "Spilleren døde ikke");
                    helper.assertTrue(dungeon.lives() == Dungeon.LIVES - 1, "Et dødsfald kostede " + (Dungeon.LIVES - dungeon.lives()) + " liv");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // A Totem of Undying saves the player, so no life is lost
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void totemCostsNoLife(GameTestHelper helper) {
        ServerPlayer player = enterDungeon(helper);
        Dungeon dungeon = Dungeons.dungeonOf(player);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    player.hurt(player.damageSources().generic(), 1000);
                    helper.assertTrue(player.isAlive(), "Totemet reddede ikke spilleren");
                    helper.assertTrue(player.getOffhandItem().isEmpty(), "Totemet blev ikke brugt");
                    helper.assertTrue(dungeon.lives() == Dungeon.LIVES, "Spilleren mistede et liv, selvom totemet reddede dem");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // Snyd Døden saves the player, so no life is lost
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cheatDeathCostsNoLife(GameTestHelper helper) {
        ServerPlayer player = enterDungeon(helper);
        Dungeon dungeon = Dungeons.dungeonOf(player);
        ItemStack helmet = Loot.create(helper.getLevel().getRandom(), Rarity.LEGENDARY, 20, Items.DIAMOND_HELMET);
        LegendaryPower.CHEAT_DEATH.markOn(helmet);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    player.hurt(player.damageSources().generic(), 1000);
                    helper.assertTrue(player.isAlive(), "Snyd Døden reddede ikke spilleren");
                    helper.assertTrue(player.getHealth() <= 1, "Spilleren tog ingen skade");
                    helper.assertTrue(dungeon.lives() == Dungeon.LIVES, "Spilleren mistede et liv, selvom Snyd Døden reddede dem");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // A tier I dungeon with only this player in it
    private static ServerPlayer enterDungeon(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        helper.assertTrue(Dungeons.start(player, 1, List.of()), "Dungeonet åbnede ikke");
        helper.assertTrue(Dungeons.dungeonOf(player) != null, "Spilleren er ikke med i dungeonet");
        TestPlayers.arrived(player);
        return player;
    }

    // A treasure chest can't be opened before its guards are dead
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void treasureChestLockedUntilGuardsDie(GameTestHelper helper) {
        ServerPlayer player = enterDungeon(helper);
        player.getAbilities().invulnerable = true;
        Dungeon dungeon = Dungeons.dungeonOf(player);
        helper.assertFalse(dungeon.treasureChests().isEmpty(), "Dungeonet har intet skatkammer");
        BlockPos chest = dungeon.treasureChests().get(0);
        ServerLevel level = player.serverLevel();
        helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "Der står ingen kiste i skatkammeret");
        helper.assertTrue(clickStopped(player, chest), "Kisten kunne åbnes, før nogen var gået ind i skatkammeret");
        TestPlayers.moveTo(player, level, new Vec3(chest.getX() - 3.5, chest.getY(), chest.getZ() + 0.5));
        AABB room = new AABB(chest).inflate(8);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(guards(level, room).isEmpty(), "Ingen vogtere kom, da spilleren gik ind i skatkammeret"))
                .thenExecute(() -> {
                    helper.assertTrue(clickStopped(player, chest), "Kisten kunne åbnes, mens vogterne levede");
                    guards(level, room).forEach(Monster::kill);
                })
                .thenWaitUntil(() -> helper.assertFalse(clickStopped(player, chest), "Kisten var stadig låst, efter vogterne døde"))
                .thenExecute(() -> TestPlayers.remove(player))
                .thenSucceed();
    }

    private static List<Monster> guards(ServerLevel level, AABB room) {
        return level.getEntitiesOfClass(Monster.class, room, Monster::isAlive);
    }

    // A right-click on the block, like a real one. True if something stopped it
    private static boolean clickStopped(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit).isCanceled();
    }

    // Monsters that come into a dungeon from something else than the dungeon, like the boss's helpers, go away with it.
    // Outside every dungeon none can come in
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void monstersThatComeInGoAwayWithTheDungeon(GameTestHelper helper) {
        ServerPlayer player = enterDungeon(helper);
        Dungeon dungeon = Dungeons.dungeonOf(player);
        ServerLevel level = player.serverLevel();
        Husk husk = EntityType.HUSK.create(level);
        husk.moveTo(player.getX() + 2, player.getY(), player.getZ() + 2, 0, 0);
        helper.assertTrue(level.addFreshEntity(husk), "Monstret kunne ikke komme ind i dungeonet");
        helper.assertTrue(dungeon.owns(husk.getUUID()), "Dungeonet holder ikke styr på monstret");
        Husk stray = EntityType.HUSK.create(level);
        stray.moveTo(-SLOT_SPACING * 10, FLOOR_Y + 1, 0, 0, 0);
        helper.assertFalse(level.addFreshEntity(stray), "Et monster kunne komme ind i dungeon-dimensionen uden for et dungeon");
        dungeon.leave(player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(husk.isRemoved(), "Monstret blev, da dungeonet lukkede"))
                .thenExecute(() -> TestPlayers.remove(player))
                .thenSucceed();
    }

    // A rift stone does nothing in a dungeon and isn't used up
    @GameTest(template = "empty")
    public static void noRiftInDungeon(GameTestHelper helper) {
        ServerPlayer player = enterDungeon(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, Rifts.stone(1));
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(Rifts.isInRift(player), "En rift åbnede inde i et dungeon");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "Riftstenen blev brugt op");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Monsters can't change the dungeon, but they can outside it
    @GameTest(template = "empty")
    public static void monstersCannotGriefDungeons(GameTestHelper helper) {
        ServerLevel dungeonLevel = helper.getLevel().getServer().getLevel(Dungeons.LEVEL);
        helper.assertTrue(dungeonLevel != null, "Dungeon-dimensionen mangler");
        Zombie inside = EntityType.ZOMBIE.create(dungeonLevel);
        Zombie outside = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertFalse(EventHooks.canEntityGrief(dungeonLevel, inside), "Monstre kan ændre dungeons");
        helper.assertTrue(EventHooks.canEntityGrief(helper.getLevel(), outside), "Monstre kan ikke ændre noget uden for dungeons");
        helper.succeed();
    }
}
