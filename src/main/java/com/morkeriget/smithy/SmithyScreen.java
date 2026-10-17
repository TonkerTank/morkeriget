package com.morkeriget.smithy;

import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// The smithy screen. Like the jewelry screen there is no texture, so it is drawn with plain rectangles. Client only
public class SmithyScreen extends AbstractContainerScreen<SmithyMenu> {
    private static final int BUTTON_Y = 48;
    private Button salvage;
    private Button reroll;

    public SmithyScreen(SmithyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = SmithyMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Skills"), pressed -> backToSkills())
                .bounds(leftPos + imageWidth - 50, topPos + 4, 46, 16)
                .build());
        salvage = addRenderableWidget(Button.builder(Component.literal("Smelt om"), pressed -> click(SmithyMenu.SALVAGE))
                .bounds(leftPos + 8, topPos + BUTTON_Y, 78, 18)
                .build());
        reroll = addRenderableWidget(Button.builder(Component.literal("Re-roll"), pressed -> click(SmithyMenu.REROLL))
                .bounds(leftPos + 90, topPos + BUTTON_Y, 78, 18)
                .build());
        updateButtons();
    }

    private void click(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void backToSkills() {
        // Tell the server the menu is closed before opening the skill screen
        minecraft.player.closeContainer();
        minecraft.setScreen(new SkillScreen());
    }

    private int essence() {
        return minecraft.player.getInventory().countItem(Smithy.ESSENCE.get());
    }

    // The buttons show what they give or cost for the item in the slot
    private void updateButtons() {
        Rarity rarity = Loot.getRarity(menu.item());
        salvage.active = rarity != null;
        salvage.setMessage(Component.literal(rarity != null ? "Smelt om (+" + Smithy.salvageValue(rarity) + ")" : "Smelt om"));
        int cost = rarity != null ? Smithy.rerollCost(rarity) : 0;
        reroll.active = cost > 0 && essence() >= cost;
        reroll.setMessage(Component.literal(cost > 0 ? "Re-roll (" + cost + ")" : "Re-roll"));
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
        graphics.drawString(font, "Essens: " + essence(), 8, SmithyMenu.ITEM_Y + 4, 0xC080FF, false);
        if (menu.item().isEmpty()) {
            graphics.drawString(font, "Læg loot her", SmithyMenu.ITEM_X + 22, SmithyMenu.ITEM_Y + 4, 0x888888, false);
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
