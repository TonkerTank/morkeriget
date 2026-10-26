package com.morkeriget.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.morkeriget.Morkeriget;
import com.morkeriget.jewelry.Jewelry;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Loot rolls, masterwork and the jewelry comparison in the tooltip. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class LootTests {
    private static final int ROLLS = 300;
    // The best a percent or special bonus can roll, twice its normal best (see Loot.AFFIXES)
    private static final Map<String, Integer> SPECIAL_CAPS = Map.of(
            Loot.LIFESTEAL_TAG, 20, Loot.FIRE_TAG, 10, Loot.CRUSHING_TAG, 30, Loot.ARROW_DAMAGE_TAG, 60, Loot.THORNS_TAG, 60);
    private static final Map<Holder<Attribute>, Double> PERCENT_CAPS = Map.of(
            Attributes.ATTACK_SPEED, 0.4, Attributes.MOVEMENT_SPEED, 0.24, Attributes.LUCK, 6.0, Attributes.ATTACK_KNOCKBACK, 3.0);

    private LootTests() {
    }

    // Even the best possible roll at level 100, with every bonus greater and three times as strong, stays under the caps
    @GameTest(template = "empty")
    public static void percentBonusesAreCapped(GameTestHelper helper) {
        RandomSource random = helper.getLevel().getRandom();
        Loot.Quality best = new Loot.Quality(1.0, 1.0, 10, 4, 3.0, false);
        for (int i = 0; i < ROLLS; i++) {
            ItemStack stack = Loot.create(random, Rarity.LEGENDARY, 100, null, best);
            for (Map.Entry<String, Integer> cap : SPECIAL_CAPS.entrySet()) {
                int value = Loot.affix(stack, cap.getKey());
                helper.assertTrue(value <= cap.getValue(), cap.getKey() + " rullede " + value + ", over grænsen " + cap.getValue());
            }
            for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
                Double cap = PERCENT_CAPS.get(entry.attribute());
                if (cap != null && entry.modifier().id().getPath().startsWith("affix_")) {
                    helper.assertTrue(entry.modifier().amount() <= cap + 1e-9,
                            entry.attribute().getRegisteredName() + " rullede " + entry.modifier().amount() + ", over grænsen " + cap);
                }
            }
        }
        helper.succeed();
    }

    // Dungeon rewards and treasure chests only have the best materials
    @GameTest(template = "empty")
    public static void dungeonLootIsBestMaterials(GameTestHelper helper) {
        RandomSource random = helper.getLevel().getRandom();
        for (int i = 0; i < ROLLS; i++) {
            ItemStack stack = Loot.create(random, Rarity.RARE, 30, null, Loot.Quality.dungeon(5, 0, 0));
            helper.assertFalse(Loot.outclassed(stack.getItem(), 30), "Dungeon-loot gav " + stack.getItem() + " på level 30");
        }
        helper.succeed();
    }

    // At rank 12 a special bonus has grown half as much as a flat one
    @GameTest(template = "empty")
    public static void masterworkGrowsPercentBonusesHalfAsFast(GameTestHelper helper) {
        RandomSource random = helper.getLevel().getRandom();
        ItemStack sword = ItemStack.EMPTY;
        for (int i = 0; i < 1000 && Loot.affix(sword, Loot.LIFESTEAL_TAG) < 10; i++) {
            sword = Loot.create(random, Rarity.LEGENDARY, 60, Items.NETHERITE_SWORD);
        }
        int lifesteal = Loot.affix(sword, Loot.LIFESTEAL_TAG);
        helper.assertTrue(lifesteal >= 10, "Fandt intet sværd med livsstjæl");
        double damage = flatDamage(sword);
        for (int rank = 0; rank < Masterwork.MAX_RANK; rank++) {
            Masterwork.upgrade(sword, random);
        }
        // 5% per rank and three big steps of 25% that may all land on one bonus, half of that for percent bonuses
        double grown = Loot.affix(sword, Loot.LIFESTEAL_TAG) / (double) lifesteal;
        helper.assertTrue(grown >= 1.3 - 1.0 / lifesteal && grown <= 1.675 + 1.0 / lifesteal,
                "Livsstjæl voksede fra " + lifesteal + " til " + Loot.affix(sword, Loot.LIFESTEAL_TAG) + " med mesterværk 12");
        if (damage > 0) {
            double damageGrown = flatDamage(sword) / damage;
            helper.assertTrue(damageGrown >= 1.6 - 1e-9 && damageGrown <= 2.35 + 1e-9, "Skade voksede " + damageGrown + " gange med mesterværk 12");
        }
        helper.succeed();
    }

    private static double flatDamage(ItemStack stack) {
        double total = 0;
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.attribute() == Attributes.ATTACK_DAMAGE && entry.modifier().id().getPath().startsWith("affix_")) {
                total += entry.modifier().amount();
            }
        }
        return total;
    }

    // A new ring is compared with the worst ring you wear, and an amulet with the worst amulet
    @GameTest(template = "empty")
    public static void jewelryIsComparedWithTheWeakest(GameTestHelper helper) {
        RandomSource random = helper.getLevel().getRandom();
        List<ItemStack> rings = new ArrayList<>();
        for (int i = 0; i < Jewelry.RING_SLOTS; i++) {
            rings.add(Loot.create(random, Rarity.RARE, 10 + 10 * i, Jewelry.RING.get()));
        }
        ItemStack amulet = Loot.create(random, Rarity.RARE, 30, Jewelry.AMULET.get());
        ItemStack weakestRing = rings.stream().min((a, b) -> Integer.compare(Loot.itemPower(a), Loot.itemPower(b))).orElseThrow();
        ItemStack newRing = Loot.create(random, Rarity.LEGENDARY, 50, Jewelry.RING.get());
        ItemStack newAmulet = Loot.create(random, Rarity.LEGENDARY, 50, Jewelry.AMULET.get());
        try {
            // Every slot full
            wear(rings, amulet, Loot.create(random, Rarity.RARE, 40, Jewelry.AMULET.get()));
            helper.assertTrue(LootTooltips.weakestWorn(newRing) == weakestRing, "En ny ring blev ikke sammenlignet med den svageste ring");
            ItemStack weakestAmulet = LootTooltips.weakestWorn(newAmulet);
            helper.assertTrue(weakestAmulet != null && weakestAmulet.is(Jewelry.AMULET.get()), "En ny amulet blev ikke sammenlignet med en amulet");
            helper.assertTrue(LootTooltips.weakestWorn(rings.get(2)) == null, "En ring, man har på, blev sammenlignet med sig selv");
            // A free amulet slot doesn't mean there is room for a ring
            wear(rings, amulet, ItemStack.EMPTY);
            helper.assertTrue(LootTooltips.weakestWorn(newRing) == weakestRing, "En ny ring fik at vide, at der er en ledig plads, fordi en amulet-plads er ledig");
            helper.assertTrue(LootTooltips.weakestWorn(newAmulet).isEmpty(), "En ny amulet fik ikke at vide, at der er en ledig plads");
            // A free ring slot
            List<ItemStack> threeRings = new ArrayList<>(rings.subList(0, 3));
            threeRings.add(ItemStack.EMPTY);
            wear(threeRings, amulet, amulet.copy());
            helper.assertTrue(LootTooltips.weakestWorn(newRing).isEmpty(), "En ny ring fik ikke at vide, at der er en ledig plads");
        } finally {
            LootTooltips.setWornJewelry(List.of());
        }
        helper.succeed();
    }

    // The rings first, then the amulets, like Jewelry.applyStats sends them
    private static void wear(List<ItemStack> rings, ItemStack amulet1, ItemStack amulet2) {
        List<ItemStack> worn = new ArrayList<>(rings);
        worn.add(amulet1);
        worn.add(amulet2);
        LootTooltips.setWornJewelry(worn);
    }
}
