package com.morkeriget.uber;

import com.morkeriget.Morkeriget;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The uber boss, like Lilith and Duriel in Diablo 4: the top of the endgame. Each of the other big fights drops one of
// three materials (dungeons from tier III, rifts from tier III and the world boss), and together they are crafted into
// Mørkets Kald, which opens Mørkets Herre's arena. The fight itself is a dungeon with only an arena, see Dungeon, and
// the boss has three phases and every ability, see Bosses
public final class Uber {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> SOUL_SHARD = ITEMS.registerSimpleItem("soul_shard", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> RIFT_CORE = ITEMS.registerSimpleItem("rift_core", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<Item> WORLD_HEART = ITEMS.registerSimpleItem("world_heart", new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<Item> SUMMON = ITEMS.registerItem("dark_summon", UberSummonItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

    private Uber() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.COMBAT) {
                event.accept(SOUL_SHARD);
                event.accept(RIFT_CORE);
                event.accept(WORLD_HEART);
                event.accept(SUMMON);
            }
        });
    }

    public static ItemStack soulShard() {
        return new ItemStack(SOUL_SHARD.get());
    }

    public static ItemStack riftCore() {
        return new ItemStack(RIFT_CORE.get());
    }

    public static ItemStack worldHeart() {
        return new ItemStack(WORLD_HEART.get());
    }
}
