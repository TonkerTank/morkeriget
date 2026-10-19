package com.morkeriget.jewelry;

import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// The jewelry screen. There is no texture, so the panel and the slots are drawn with plain rectangles. Client only
public class JewelryScreen extends AbstractContainerScreen<JewelryMenu> {
    public JewelryScreen(JewelryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = JewelryMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Skills"), pressed -> backToSkills())
                .bounds(leftPos + imageWidth - 50, topPos + 4, 46, 16)
                .build(ThemedButton::new));
    }

    private void backToSkills() {
        // Tell the server the menu is closed before opening the skill screen
        minecraft.player.closeContainer();
        minecraft.setScreen(new SkillScreen());
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
        int labelY = JewelryMenu.JEWELRY_Y - 10;
        graphics.drawString(font, "Ringe", 53, labelY, 0xFFD27A, false);
        graphics.drawString(font, "Amulet", 98, labelY, 0xFF8A8A, false);
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
