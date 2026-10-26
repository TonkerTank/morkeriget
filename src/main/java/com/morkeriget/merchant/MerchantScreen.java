package com.morkeriget.merchant;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.smithy.Smithy;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// The merchant screen: a button for each ware in two columns, and the inventory below. Drawn with plain rectangles
// like the smithy. Client only
public class MerchantScreen extends AbstractContainerScreen<MerchantMenu> {
    private static final int BUTTON_Y = 20;
    private static final int ROW = 20;
    private final List<Button> wareButtons = new ArrayList<>();

    public MerchantScreen(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = MerchantMenu.INVENTORY_Y + 82;
        inventoryLabelY = MerchantMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        wareButtons.clear();
        addRenderableWidget(Button.builder(Text.tr("button.skills"), pressed -> backToSkills())
                .bounds(leftPos + imageWidth - 50, topPos + 2, 46, 16)
                .build(ThemedButton::new));
        Tooltip odds = Tooltip.create(Text.tr("merchant.odds", percent(1 - Merchant.LEGENDARY_CHANCE - Merchant.RARE_CHANCE),
                percent(Merchant.RARE_CHANCE), percent(Merchant.LEGENDARY_CHANCE)));
        for (Ware ware : Ware.values()) {
            int column = ware.ordinal() % 2;
            int row = ware.ordinal() / 2;
            Button button = addRenderableWidget(Button.builder(ware.displayName().append(" " + ware.price),
                            pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ware.ordinal()))
                    .bounds(leftPos + 8 + column * 82, topPos + BUTTON_Y + row * ROW, 78, 18)
                    .tooltip(odds)
                    .build(ThemedButton::new));
            wareButtons.add(button);
        }
        updateButtons();
    }

    private static String percent(double chance) {
        return Math.round(chance * 100) + "%";
    }

    private void backToSkills() {
        // Tell the server the menu is closed before opening the skill screen
        minecraft.player.closeContainer();
        minecraft.setScreen(new SkillScreen());
    }

    private int essence() {
        return minecraft.player.getInventory().countItem(Smithy.ESSENCE.get());
    }

    // Only the wares you can pay for can be clicked
    private void updateButtons() {
        int essence = essence();
        for (Ware ware : Ware.values()) {
            wareButtons.get(ware.ordinal()).active = essence >= ware.price;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiTheme.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            UiTheme.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }
    }

    // The vanilla labels are dark grey, which can't be read on the dark panel
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, UiTheme.TITLE, false);
        UiTheme.divider(graphics, 7, inventoryLabelY - 4, imageWidth - 14);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.MUTED, false);
        // On the same line as the "Inventory" label, but at the right edge
        String essence = "Essens: " + essence();
        graphics.drawString(font, essence, imageWidth - 8 - font.width(essence), inventoryLabelY, 0xC080FF, false);
        // In the empty place next to the last ware, if there is one. The buttons' tooltip has the chances too
        if (Ware.values().length % 2 == 1) {
            String chance = percent(Merchant.LEGENDARY_CHANCE) + " legendarisk";
            int row = (Ware.values().length - 1) / 2;
            graphics.drawString(font, chance, 90 + (78 - font.width(chance)) / 2, BUTTON_Y + row * ROW + 5, 0xFF8800, false);
        }
    }

    // K closes the screen, just like the skill screen
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SkillKeys.SKILLS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
