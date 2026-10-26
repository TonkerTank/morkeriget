package com.morkeriget.dummy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.loot.Staffs;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;
import com.morkeriget.skill.Mana;
import com.morkeriget.skill.Minions;
import com.morkeriget.skill.Skill;
import com.morkeriget.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Measures how hard each class hits, so the classes and skills can be compared with numbers. Every skill is used on its
// own against a group of five training dummies for SECONDS, with mana and cooldowns like in the game, and each class
// once more with a typical set of four skills (there are only four keys). Everyone is level LEVEL with every skill at
// rank RANK and a plain weapon for their class of that level. Swords and staffs get an average damage bonus, while bows
// grow with their item level by themselves, like loot does. The single skills are measured without basic attacks, and
// each class also with only basic attacks (swinging the weapon, or fully drawn bow shots), and with its four skills and
// basic attacks in between.
// The table comes in the log after the "balance" batch, with lines starting "BALANCE". Run with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class BalanceTests {
    private static final String BATCH = "balance";
    private static final int LEVEL = 50;
    private static final int RANK = 5;
    private static final int SECONDS = 30;
    // In the arena: where the player stands, and the middle of the group of dummies in front of them
    private static final Vec3 SPOT = new Vec3(10.5, 2, 12.5);
    private static final Vec3 CENTER = new Vec3(13, 2, 12.5);
    // Side by side across the way you look, so none of them shields the others from arrows
    private static final double[][] GROUP = {{0, 0}, {0, 1.2}, {0, -1.2}, {0, 2.4}, {0, -2.4}};
    // 20 ticks is one second. How soon a skill has to be ready for mana to be kept for it, see useSkills
    private static final int RESERVE_TIME = 2 * 20;
    private static final Map<PlayerClass, List<Skill>> DAMAGE_SKILLS = Map.of(
            PlayerClass.BARBARIAN, List.of(Skill.WHIRLWIND, Skill.LEAP, Skill.EARTHQUAKE, Skill.RUSH, Skill.DOOM_SLAM),
            PlayerClass.SORCERER, List.of(Skill.FROST_NOVA, Skill.CHAIN_LIGHTNING, Skill.FIREBALL, Skill.METEOR, Skill.THUNDERSTORM),
            PlayerClass.RANGER, List.of(Skill.MULTISHOT, Skill.ARROW_RAIN, Skill.WOLVES, Skill.EXPLOSIVE_ARROW, Skill.BEAR_TRAP, Skill.ARROW_HURRICANE),
            PlayerClass.NECROMANCER, List.of(Skill.BONE_SPEAR, Skill.RAISE_SKELETONS, Skill.BLOOD_NOVA, Skill.ARMY_OF_THE_DEAD));
    // The best set of four for each class from the single skills, the big ones first
    private static final Map<PlayerClass, List<Skill>> BUILDS = Map.of(
            PlayerClass.BARBARIAN, List.of(Skill.DOOM_SLAM, Skill.EARTHQUAKE, Skill.LEAP, Skill.WHIRLWIND),
            PlayerClass.SORCERER, List.of(Skill.METEOR, Skill.THUNDERSTORM, Skill.FIREBALL, Skill.CHAIN_LIGHTNING),
            PlayerClass.RANGER, List.of(Skill.ARROW_HURRICANE, Skill.EXPLOSIVE_ARROW, Skill.ARROW_RAIN, Skill.MULTISHOT),
            PlayerClass.NECROMANCER, List.of(Skill.CURSE, Skill.ARMY_OF_THE_DEAD, Skill.RAISE_SKELETONS, Skill.BONE_SPEAR));
    private static final String BUILD = "4 skills + slag";
    // The damage per second every class's four skills must stay within, against one dummy and against all five
    private static final int[] BUILD_SINGLE = {30, 90};
    private static final int[] BUILD_GROUP = {100, 260};
    private static final String BASIC = "Kun slag/skud";
    // A bow is fully drawn after this many ticks
    private static final int FULL_DRAW = 20;

    // basic is whether the player also swings their weapon or shoots their bow when nothing else is used
    private record Scenario(PlayerClass playerClass, String name, String id, List<Skill> skills, boolean basic) {
    }

    private record Result(PlayerClass playerClass, String name, double single, double group) {
    }

    private static final List<Result> RESULTS = Collections.synchronizedList(new ArrayList<>());
    // Damage done to each dummy by each player. Counted here rather than with the dummy's own numbers, which start
    // over after a pause, and a slow skill can be quiet for longer than that
    private static final Map<UUID, Map<UUID, Double>> DAMAGE = new HashMap<>();

    static {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, BalanceTests::onDamage);
    }

    private BalanceTests() {
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        if (!TrainingDummy.isDummy(event.getEntity()) || !(event.getEntity().level() instanceof ServerLevel level) || event.getNewDamage() <= 0) {
            return;
        }
        Entity attacker = event.getSource().getEntity() != null ? event.getSource().getEntity() : event.getEntity().getKillCredit();
        ServerPlayer player = TrainingDummy.credit(level, attacker);
        if (player != null) {
            DAMAGE.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).merge(event.getEntity().getUUID(), (double) event.getNewDamage(), Double::sum);
        }
    }

    @GameTestGenerator
    public static List<TestFunction> balance() {
        List<Scenario> scenarios = new ArrayList<>();
        for (PlayerClass playerClass : PlayerClass.values()) {
            for (Skill skill : DAMAGE_SKILLS.get(playerClass)) {
                scenarios.add(new Scenario(playerClass, skill.displayName().getString(), skill.name(), List.of(skill), false));
            }
            scenarios.add(new Scenario(playerClass, BASIC, "basic", List.of(), true));
            scenarios.add(new Scenario(playerClass, BUILD, "build", BUILDS.get(playerClass), true));
        }
        // The ranger also with the wolves, since a pet and Multishot together are the strongest against one
        scenarios.add(new Scenario(PlayerClass.RANGER, "Ulve-sæt + skud", "build_wolves",
                List.of(Skill.WOLVES, Skill.ARROW_HURRICANE, Skill.EXPLOSIVE_ARROW, Skill.MULTISHOT), true));
        List<TestFunction> tests = new ArrayList<>();
        for (Scenario scenario : scenarios) {
            String name = "balance_" + scenario.playerClass.name().toLowerCase(Locale.ROOT) + "_" + scenario.id.toLowerCase(Locale.ROOT);
            tests.add(new TestFunction(BATCH, name, Morkeriget.MODID + ":arena", SECONDS * 20 + 100, 0, true, helper -> run(helper, scenario)));
        }
        return tests;
    }

    private static void run(GameTestHelper helper, Scenario scenario) {
        ServerLevel level = helper.getLevel();
        List<Husk> dummies = new ArrayList<>();
        for (double[] offset : GROUP) {
            dummies.add(TrainingDummy.spawn(level, helper.absoluteVec(CENTER.add(offset[0], 0, offset[1])), 90));
        }
        ServerPlayer player = TestPlayers.survivor(helper, BlockPos.containing(SPOT));
        player.getAbilities().invulnerable = true;
        Map<Upgrade, Integer> ranks = new EnumMap<>(Upgrade.class);
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade.playerClass == scenario.playerClass && upgrade.skill != null) {
                ranks.put(upgrade, RANK);
            }
        }
        Progression.setForTests(player, LEVEL, scenario.playerClass, ranks);
        player.setItemInHand(InteractionHand.MAIN_HAND, weapon(scenario.playerClass, level.getRandom()));
        player.getInventory().add(new ItemStack(Items.ARROW, 64));
        Vec3 spot = helper.absoluteVec(SPOT);
        Vec3 target = dummies.get(0).position().add(0, dummies.get(0).getBbHeight() / 2, 0);
        long[] drawn = {0};
        helper.startSequence()
                .thenExecuteFor(SECONDS * 20, () -> {
                    // Back on the spot every tick, since Leap, Rush and Doom Slam move you, and looking at the middle dummy
                    TestPlayers.moveTo(player, level, spot);
                    look(player, target);
                    TestPlayers.tick(player);
                    boolean used = useSkills(player, scenario.skills);
                    if (scenario.basic && !used) {
                        basicAttack(player, dummies.get(0), drawn);
                    }
                })
                .thenExecute(() -> {
                    Map<UUID, Double> done = DAMAGE.getOrDefault(player.getUUID(), Map.of());
                    // Against one monster: the dummy that got the most, since minions and arrows don't spread their damage evenly
                    double single = dummies.stream().mapToDouble(dummy -> done.getOrDefault(dummy.getUUID(), 0.0)).max().orElse(0) / SECONDS;
                    double group = dummies.stream().mapToDouble(dummy -> done.getOrDefault(dummy.getUUID(), 0.0)).sum() / SECONDS;
                    Result result = new Result(scenario.playerClass, scenario.name, single, group);
                    RESULTS.add(result);
                    Morkeriget.LOGGER.info("BALANCE {}", line(result));
                    DAMAGE.remove(player.getUUID());
                    cleanUp(level, player, dummies, spot);
                    helper.assertTrue(group > 0, scenario.name + " gjorde ingen skade");
                    // Every class's four skills land in the same band, so a change that makes one far stronger or weaker
                    // than the rest is caught. Balanced on 2026-10-26 to about 45-65 against one and 140-195 against five,
                    // and the ranger with wolves 70-80 against one but less against five
                    if (scenario.id.startsWith("build")) {
                        helper.assertTrue(single >= BUILD_SINGLE[0] && single <= BUILD_SINGLE[1], String.format("%s mod én fjende: %.1f, uden for %d-%d",
                                scenario.playerClass.name(), single, BUILD_SINGLE[0], BUILD_SINGLE[1]));
                        helper.assertTrue(group >= BUILD_GROUP[0] && group <= BUILD_GROUP[1], String.format("%s mod en flok: %.1f, uden for %d-%d",
                                scenario.playerClass.name(), group, BUILD_GROUP[0], BUILD_GROUP[1]));
                    }
                })
                .thenSucceed();
    }

    // Like a player who knows the class: the skills earlier in the list come first, and a later one is only used if
    // there is still mana enough left for an earlier one that is ready within RESERVE_TIME, so the cheap ones don't keep
    // the big ones from ever coming
    // Returns whether a skill was used this tick
    private static boolean useSkills(ServerPlayer player, List<Skill> skills) {
        long now = player.serverLevel().getGameTime();
        float reserve = 0;
        boolean used = false;
        for (Skill skill : skills) {
            String readyTag = "morkeriget_" + skill.name().toLowerCase(Locale.ROOT) + "_ready";
            if (Mana.get(player) - skill.manaCost() >= reserve) {
                Skills.use(player, skill);
                used |= player.getPersistentData().getLong(readyTag) > now;
            }
            if (player.getPersistentData().getLong(readyTag) - now <= RESERVE_TIME) {
                reserve = Math.max(reserve, skill.manaCost());
            }
        }
        return used;
    }

    // A swing as soon as the weapon is ready again, or with a bow a fully drawn shot every FULL_DRAW ticks. drawn is
    // when the bow was last shot
    private static void basicAttack(ServerPlayer player, Husk target, long[] drawn) {
        ItemStack held = player.getMainHandItem();
        long now = player.serverLevel().getGameTime();
        if (held.getItem() instanceof BowItem bow) {
            if (now - drawn[0] >= FULL_DRAW) {
                bow.releaseUsing(held, player.serverLevel(), player, bow.getUseDuration(held, player) - FULL_DRAW);
                drawn[0] = now;
            }
        } else if (player.getAttackStrengthScale(0.5f) >= 1) {
            player.attack(target);
        }
    }

    // The class's weapon at LEVEL. A sword or staff gets an average damage bonus for the level, a bow none, since bows
    // grow with their item level instead, see PlayerHits
    private static ItemStack weapon(PlayerClass playerClass, RandomSource random) {
        Item item = switch (playerClass) {
            case BARBARIAN -> Items.NETHERITE_SWORD;
            case SORCERER, NECROMANCER -> Staffs.STAR.get();
            case RANGER -> Items.BOW;
        };
        ItemStack weapon = Loot.create(random, Rarity.COMMON, LEVEL, item);
        if (playerClass != PlayerClass.RANGER) {
            double bonus = 2.5 * (1 + Config.AFFIX_PER_LEVEL.get() * (LEVEL - 1));
            weapon.set(DataComponents.ATTRIBUTE_MODIFIERS, Loot.baseModifiers(weapon).withModifierAdded(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "affix_0"), bonus, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND));
        }
        return weapon;
    }

    private static void look(ServerPlayer player, Vec3 target) {
        Vec3 way = target.subtract(player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(way.z, way.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(way.y, way.horizontalDistance()));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
    }

    // The dummies, and the player's wolves, skeletons and arrows, so nothing goes on into the next test
    private static void cleanUp(ServerLevel level, ServerPlayer player, List<Husk> dummies, Vec3 spot) {
        dummies.forEach(Entity::discard);
        for (Entity entity : level.getEntities((Entity) null, new AABB(spot, spot).inflate(48), entity -> belongsTo(level, entity, player))) {
            entity.discard();
        }
        TestPlayers.remove(player);
    }

    private static boolean belongsTo(ServerLevel level, Entity entity, ServerPlayer player) {
        if (entity instanceof OwnableEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
            return true;
        }
        if (entity instanceof AbstractArrow arrow && arrow.getOwner() == player) {
            return true;
        }
        return Minions.isAlly(entity) && Minions.owner(level, entity) == player;
    }

    private static String line(Result result) {
        return String.format("%-12s %-16s %8.1f %8.1f", result.playerClass.name(), result.name, result.single, result.group);
    }

    // The whole table, by class and then the most damage against the group first
    @AfterBatch(batch = BATCH)
    public static void table(ServerLevel level) {
        List<Result> results = new ArrayList<>(RESULTS);
        results.sort(Comparator.comparing((Result result) -> result.playerClass).thenComparing(result -> -result.group));
        Morkeriget.LOGGER.info("BALANCE {}", String.format("%-12s %-16s %8s %8s", "Klasse", "Færdighed", "1 fjende", "5 fjender"));
        for (Result result : results) {
            Morkeriget.LOGGER.info("BALANCE {}", line(result));
        }
        RESULTS.clear();
    }
}
