package com.morkeriget.glyph;

import java.util.Map;

import com.morkeriget.Morkeriget;
import com.morkeriget.dummy.TrainingDummy;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Glyphs on the paragon board. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class GlyphTests {
    // Players need this long after they join before they can be hurt
    private static final int SPAWN_PROTECTION = 61;

    private GlyphTests() {
    }

    private static ServerPlayer player(GameTestHelper helper, int paragonLevel) {
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        Progression.setForTests(player, 100, paragonLevel, PlayerClass.BARBARIAN, Map.of());
        return player;
    }

    // Right-clicks the glyph like a player would. True if it was used up
    private static boolean use(ServerPlayer player, ItemStack glyph) {
        player.setItemInHand(InteractionHand.MAIN_HAND, glyph);
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        return player.getMainHandItem().isEmpty();
    }

    // No slots before paragon, one at paragon 1 and four at 50
    @GameTest(template = "empty")
    public static void glyphSlotsComeWithParagon(GameTestHelper helper) {
        ServerPlayer player = player(helper, 0);
        helper.assertFalse(use(player, Glyphs.stack(Glyph.WRATH, 1)), "En glyf kunne sættes i uden paragon");
        helper.assertTrue(Glyph.slots(1) == 1 && Glyph.slots(10) == 2 && Glyph.slots(25) == 3 && Glyph.slots(50) == 4 && Glyph.slots(500) == 4,
                "Glyf-pladserne åbner ikke ved paragon 1, 10, 25 og 50");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // One of each kind: a better one replaces the old, which comes back, a worse one is turned down, and without a free
    // slot nothing goes in. Taking one out gives it back
    @GameTest(template = "empty")
    public static void socketReplaceAndRemove(GameTestHelper helper) {
        ServerPlayer player = player(helper, 10);
        helper.assertTrue(use(player, Glyphs.stack(Glyph.WRATH, 5)), "Glyffen blev ikke sat i");
        helper.assertTrue(Glyphs.level(player, Glyph.WRATH) == 5, "Vrede sidder ikke på level 5");
        helper.assertFalse(use(player, Glyphs.stack(Glyph.WRATH, 3)), "En dårligere Vrede tog pladsen");
        helper.assertTrue(use(player, Glyphs.stack(Glyph.WRATH, 8)), "En bedre Vrede blev ikke sat i");
        helper.assertTrue(Glyphs.level(player, Glyph.WRATH) == 8, "Den bedre Vrede sidder ikke");
        helper.assertTrue(hasGlyph(player, Glyph.WRATH, 5), "Den gamle Vrede kom ikke tilbage");
        helper.assertTrue(use(player, Glyphs.stack(Glyph.FLAME, 1)), "Flamme kom ikke i den anden plads");
        helper.assertFalse(use(player, Glyphs.stack(Glyph.DOMINION, 1)), "En tredje glyf kom i, selvom der kun er to pladser ved paragon 10");
        Glyphs.remove(player, 0);
        helper.assertTrue(Glyphs.level(player, Glyph.WRATH) == 0, "Vrede blev ikke taget ud");
        helper.assertTrue(hasGlyph(player, Glyph.WRATH, 8), "Vrede kom ikke tilbage i inventaret");
        TestPlayers.remove(player);
        helper.succeed();
    }

    private static boolean hasGlyph(ServerPlayer player, Glyph glyph, int level) {
        for (ItemStack stack : player.getInventory().items) {
            Glyphs.Socketed socketed = Glyphs.of(stack);
            if (socketed != null && socketed.glyph() == glyph && socketed.level() == level) {
                return true;
            }
        }
        return false;
    }

    // A cleared dungeon raises every socketed glyph a level, but never above twice its tier
    @GameTest(template = "empty")
    public static void glyphsLevelWithDungeons(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1);
        use(player, Glyphs.stack(Glyph.WRATH, 1));
        Glyphs.dungeonCleared(player, 1);
        helper.assertTrue(Glyphs.level(player, Glyph.WRATH) == 2, "Glyffen steg ikke efter et dungeon");
        Glyphs.dungeonCleared(player, 1);
        helper.assertTrue(Glyphs.level(player, Glyph.WRATH) == 2, "Glyffen steg over 2 i et tier I-dungeon");
        Glyphs.dungeonCleared(player, 5);
        helper.assertTrue(Glyphs.level(player, Glyph.WRATH) == 3, "Glyffen steg ikke i et højere tier");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // Wrath, Frost and Legion on the damage done to a training dummy, measured against the same hit without them
    @GameTest(template = "empty")
    public static void glyphsAddDamage(GameTestHelper helper) {
        ServerPlayer player = player(helper, 50);
        double plain = hit(helper, player, false);
        use(player, Glyphs.stack(Glyph.WRATH, Glyph.MAX_LEVEL));
        double wrath = hit(helper, player, false);
        helper.assertTrue(Math.abs(wrath / plain - 1.15) < 0.01, "Vrede 20 gav " + wrath / plain + " gange skaden, ikke 1,15");
        use(player, Glyphs.stack(Glyph.FROST, Glyph.MAX_LEVEL));
        double frost = hit(helper, player, true);
        helper.assertTrue(Math.abs(frost / plain - 1.55) < 0.01, "Vrede og Kulde 20 på en langsom fjende gav " + frost / plain + " gange skaden, ikke 1,55");
        use(player, Glyphs.stack(Glyph.LEGION, Glyph.MAX_LEVEL));
        Husk dummy = TrainingDummy.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(5.5, 2, 5.5)), 0);
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        wolf.tame(player);
        dummy.hurt(dummy.damageSources().mobAttack(wolf), 10);
        double legion = TrainingDummy.total(dummy, player);
        helper.assertTrue(Math.abs(legion / plain - 1.55) < 0.01, "Vrede og Legion 20 på en ulvs bid gav " + legion / plain + " gange skaden, ikke 1,55");
        TestPlayers.remove(player);
        helper.succeed();
    }

    // What one hit of 10 from the player does to a fresh dummy, slowed or not
    private static double hit(GameTestHelper helper, ServerPlayer player, boolean slowed) {
        Husk dummy = TrainingDummy.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(5.5, 2, 5.5)), 0);
        if (slowed) {
            dummy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
        }
        dummy.hurt(player.damageSources().playerAttack(player), 10);
        double done = TrainingDummy.total(dummy, player);
        dummy.discard();
        return done;
    }

    // Bulwark 20 takes 15% off the damage the player takes
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bulwarkTakesLessDamage(GameTestHelper helper) {
        ServerPlayer player = player(helper, 1);
        helper.startSequence()
                .thenIdle(SPAWN_PROTECTION)
                .thenExecute(() -> {
                    float before = player.getHealth();
                    player.hurt(player.damageSources().generic(), 10);
                    float plain = before - player.getHealth();
                    player.setHealth(player.getMaxHealth());
                    player.invulnerableTime = 0;
                    use(player, Glyphs.stack(Glyph.BULWARK, Glyph.MAX_LEVEL));
                    player.hurt(player.damageSources().generic(), 10);
                    float shielded = player.getMaxHealth() - player.getHealth();
                    helper.assertTrue(plain > 0 && Math.abs(shielded / plain - 0.85) < 0.01, "Bolværk 20 gav " + shielded / plain + " af skaden, ikke 0,85");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }
}
