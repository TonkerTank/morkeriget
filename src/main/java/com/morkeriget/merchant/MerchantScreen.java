package com.morkeriget.merchant;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.smithy.Smithy;

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
        addRenderableWidget(Button.builder(Component.literal("Skills"), pressed -> backToSkills())
                .bounds(leftPos + imageWidth - 50, topPos + 2, 46, 16)
                .build());
        Tooltip odds = Tooltip.create(Component.literal("En tilfældig genstand på dit level: "
                + percent(1 - Merchant.LEGENDARY_CHANCE - Merchant.RARE_CHANCE) + " magisk, "
                + percent(Merchant.RARE_CHANCE) + " sjælden og " + percent(Merchant.LEGENDARY_CHANCE) + " legendarisk"));
        for (Ware ware : Ware.values()) {
            int column = ware.ordinal() % 2;
            int row = ware.ordinal() / 2;
            Button button = addRenderableWidget(Button.builder(Component.literal(ware.displayName + " " + ware.price),
                            pressed -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ware.ordinal()))
                    .bounds(leftPos + 8 + column * 82, topPos + BUTTON_Y + row * ROW, 78, 18)
                    .tooltip(odds)
                    .build());
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
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFF000000);
        graphics.fill(left + 1, top + 1, left + imageWidth - 1, top + imageHeight - 1, 0xFF2B2B33);
        for (Slot slot : menu.slots) {
            int x = left + slot.x;
            int y = top + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF111116);
            graphics.fill(x, y, x + 16, y + 16, 0xFF45454F);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
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
