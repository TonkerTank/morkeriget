package com.morkeriget.loot;

import java.util.List;

import com.morkeriget.Morkeriget;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Staffs for casters, so a sorcerer doesn't have to carry a sword: skills get stronger with the damage of the weapon
// in your hand, and a staff hits as hard as a sword of the same tier. They are slower to swing, and every staff
// makes mana come back faster (STAFF_MANA_TAG, rolled in Loot). The tiers drop at the same levels as swords
public final class Staffs {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> OAK = staff("staff_oak", Tiers.STONE);
    public static final DeferredItem<Item> BLAZE = staff("staff_blaze", Tiers.IRON);
    public static final DeferredItem<Item> STORM = staff("staff_storm", Tiers.DIAMOND);
    public static final DeferredItem<Item> STAR = staff("staff_star", Tiers.NETHERITE);
    private static final List<DeferredItem<Item>> ALL = List.of(OAK, BLAZE, STORM, STAR);

    private Staffs() {
    }

    // The same damage as a sword of the tier, but a slower swing
    private static DeferredItem<Item> staff(String name, Tier tier) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(1)
                .attributes(SwordItem.createAttributes(tier, 3, -2.8f)));
    }

    static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(Staffs::addToCreativeTab);
    }

    public static boolean isStaff(ItemStack stack) {
        return ALL.stream().anyMatch(staff -> stack.is(staff.get()));
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            ALL.forEach(event::accept);
        }
    }
}
