package com.morkeriget.loot;

import com.morkeriget.ui.ThemedButton;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

// The normal chest screen with the loot bag's buttons to the right of it. Client only
public class LootBagScreen extends ContainerScreen {
    private static final int BUTTON_WIDTH = 82;
    // The best rarity "Smelt" takes. Remembered while the game runs, so it is the same next time
    private static Rarity salvageUpTo = Rarity.MAGIC;

    public LootBagScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + imageWidth + 4;
        addRenderableWidget(Button.builder(Component.literal("Sortér"), pressed -> click(LootBagMenu.SORT))
                .tooltip(Tooltip.create(Component.literal("Favoritter først, så efter sjældenhed og genstandsstyrke")))
                .bounds(x, topPos + 4, BUTTON_WIDTH, 18)
                .build(ThemedButton::new));
        // Clicking it picks how good the items "Smelt" takes can be, up to set items (never mythic)
        addRenderableWidget(Button.builder(limitText(), pressed -> {
                    salvageUpTo = Rarity.values()[(salvageUpTo.ordinal() + 1) % (Rarity.SET.ordinal() + 1)];
                    pressed.setMessage(limitText());
                })
                .bounds(x, topPos + 30, BUTTON_WIDTH, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("Smelt"), pressed -> click(LootBagMenu.SALVAGE + salvageUpTo.ordinal()))
                .tooltip(Tooltip.create(Component.literal("Smelter alt i tasken op til den valgte sjældenhed om til essens. "
                        + "Favoritter (Alt+klik), mytiske og genstande med ædelsten bliver aldrig rørt")))
                .bounds(x, topPos + 50, BUTTON_WIDTH, 18)
                .build(ThemedButton::new));
    }

    // E.g. "Til: Sjælden" in the rarity's colour
    private static Component limitText() {
        return Component.literal("Til: ").append(Component.literal(salvageUpTo.displayName).withStyle(salvageUpTo.color));
    }

    private void click(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
}
