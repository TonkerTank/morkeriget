package com.morkeriget.smithy;

import com.morkeriget.Morkeriget;
import com.morkeriget.combat.PlayerHits;
import com.morkeriget.gametest.TestPlayers;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Durability and the smithy. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class SmithyTests {
    private SmithyTests() {
    }

    // Worn all the way down, a chestplate gives no bonuses but still its armor, and repaired they come back
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void wornOutGearGivesNoBonuses(GameTestHelper helper) {
        RandomSource random = helper.getLevel().getRandom();
        ItemStack chest = ItemStack.EMPTY;
        for (int i = 0; i < 100 && bonusAttribute(chest) == null; i++) {
            chest = Loot.create(random, Rarity.LEGENDARY, 50, Items.NETHERITE_CHESTPLATE);
        }
        Holder<Attribute> attribute = bonusAttribute(chest);
        helper.assertTrue(attribute != null, "Fandt ingen brystplade med en bonus");
        ItemStack worn = chest;
        ServerPlayer player = TestPlayers.survivor(helper, new BlockPos(2, 2, 2));
        player.setItemSlot(EquipmentSlot.CHEST, worn);
        helper.startSequence()
                // The game puts on what is worn when the player ticks
                .thenExecute(() -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertTrue(hasBonus(player, attribute), "Brystpladens bonus virker ikke");
                    helper.assertTrue(PlayerHits.equippedItems(player).contains(worn), "Brystpladen tæller ikke med i kamp");
                    worn.setDamageValue(worn.getMaxDamage() - 1);
                    helper.assertTrue(Durability.isBroken(worn), "Brystpladen er ikke slidt op");
                })
                .thenExecute(() -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertFalse(hasBonus(player, attribute), "Den slidte brystplade giver stadig sin bonus");
                    helper.assertFalse(PlayerHits.equippedItems(player).contains(worn), "Den slidte brystplade tæller stadig med i kamp");
                    helper.assertTrue(player.getAttributeValue(Attributes.ARMOR) > 0, "Den slidte brystplade giver ingen rustning");
                    helper.assertTrue(Durability.repairCost(worn) > 0, "Den slidte brystplade koster intet at reparere");
                    worn.setDamageValue(0);
                })
                .thenExecute(() -> TestPlayers.tick(player))
                .thenExecute(() -> {
                    helper.assertTrue(hasBonus(player, attribute), "Bonussen kom ikke tilbage efter reparation");
                    TestPlayers.remove(player);
                })
                .thenSucceed();
    }

    // The attribute of one of the item's bonuses, or null if it has none
    private static Holder<Attribute> bonusAttribute(ItemStack stack) {
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.modifier().id().getPath().startsWith("affix_")) {
                return entry.attribute();
            }
        }
        return null;
    }

    private static boolean hasBonus(ServerPlayer player, Holder<Attribute> attribute) {
        return player.getAttribute(attribute).getModifiers().stream()
                .anyMatch(modifier -> modifier.id().getNamespace().equals(Morkeriget.MODID) && modifier.id().getPath().startsWith("affix_"));
    }
}
