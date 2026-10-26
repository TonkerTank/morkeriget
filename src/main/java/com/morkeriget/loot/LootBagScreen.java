package com.morkeriget.loot;

import com.morkeriget.Text;
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
        addRenderableWidget(Button.builder(Text.tr("bag.sort"), pressed -> click(LootBagMenu.SORT))
                .tooltip(Tooltip.create(Text.tr("bag.sort.tooltip")))
                .bounds(x, topPos + 4, BUTTON_WIDTH, 18)
                .build(ThemedButton::new));
        // Clicking it picks how good the items "Smelt" takes can be, up to set items (never mythic)
        addRenderableWidget(Button.builder(limitText(), pressed -> {
                    salvageUpTo = Rarity.values()[(salvageUpTo.ordinal() + 1) % (Rarity.SET.ordinal() + 1)];
                    pressed.setMessage(limitText());
                })
                .bounds(x, topPos + 30, BUTTON_WIDTH, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("bag.salvage"), pressed -> click(LootBagMenu.SALVAGE + salvageUpTo.ordinal()))
                .tooltip(Tooltip.create(Text.tr("bag.salvage.tooltip")))
                .bounds(x, topPos + 50, BUTTON_WIDTH, 18)
                .build(ThemedButton::new));
    }

    // E.g. "Up to: Rare" in the rarity's colour
    private static Component limitText() {
        return Text.tr("bag.up_to", salvageUpTo.displayName().withStyle(salvageUpTo.color));
    }

    private void click(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
}
