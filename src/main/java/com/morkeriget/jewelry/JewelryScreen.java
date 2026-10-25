package com.morkeriget.jewelry;

import java.util.List;
import java.util.Locale;

import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;

// The equipment screen. There is no texture, so the panel and the slots are drawn with plain rectangles. Client only
public class JewelryScreen extends AbstractContainerScreen<JewelryMenu> {
    // The stats panel to the right, like the character sheet in Diablo 4
    private static final int STATS_WIDTH = 132;
    private static final int STATS_GAP = 4;
    private static final int LINE = 9;

    // The newest stats from the server, and the first ones after the screen opened. The difference shows in green or
    // red, so you can see what a swap did
    private static List<EquipmentStats.Line> latest = List.of();
    private List<EquipmentStats.Line> baseline;

    public JewelryScreen(JewelryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 188;
        inventoryLabelY = JewelryMenu.INVENTORY_Y - 11;
        // Old stats from the last time the screen was open aren't shown
        latest = List.of();
    }

    @Override
    protected void init() {
        super.init();
        // The two panels together are centred
        leftPos -= (STATS_WIDTH + STATS_GAP) / 2;
        addRenderableWidget(Button.builder(Component.literal("Skills"), pressed -> backToSkills())
                .bounds(leftPos + imageWidth - 50, topPos + 4, 46, 16)
                .build(ThemedButton::new));
    }

    public static void receive(EquipmentStats.Payload payload) {
        latest = payload.lines();
        if (Minecraft.getInstance().screen instanceof JewelryScreen screen && screen.baseline == null) {
            screen.baseline = latest;
        }
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
        renderStats(graphics);
        for (Slot slot : menu.slots) {
            UiTheme.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }
        // The first hotbar slot is the weapon slot at the top, so its place in the hotbar shows a faded sword instead
        int x = leftPos + 8;
        int y = topPos + JewelryMenu.HOTBAR_Y;
        UiTheme.slot(graphics, x, y);
        graphics.setColor(1, 1, 1, 0.35f);
        graphics.blit(x, y, 0, 16, 16, minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(JewelryMenu.WEAPON_ICON));
        graphics.setColor(1, 1, 1, 1);
    }

    private void renderStats(GuiGraphics graphics) {
        int x = leftPos + imageWidth + STATS_GAP;
        UiTheme.panel(graphics, x, topPos, STATS_WIDTH, imageHeight);
        UiTheme.title(graphics, font, "Stats", x + STATS_WIDTH / 2, topPos + 6);
        int y = topPos + 18;
        String group = null;
        for (EquipmentStats.Line line : latest) {
            if (!line.group().equals(group)) {
                group = line.group();
                y += group.equals(latest.get(0).group()) ? 0 : 2;
                graphics.drawString(font, group, x + 8, y, UiTheme.TITLE, false);
                y += LINE + 1;
            }
            graphics.drawString(font, line.name(), x + 10, y, UiTheme.MUTED, false);
            String value = format(line.value(), line.decimals()) + line.unit();
            int valueRight = x + STATS_WIDTH - 30;
            graphics.drawString(font, value, valueRight - font.width(value), y, UiTheme.TEXT, false);
            float change = line.value() - before(line);
            if (Math.abs(change) >= Math.pow(10, -line.decimals()) / 2) {
                String text = (change > 0 ? "+" : "") + format(change, line.decimals());
                graphics.drawString(font, text, valueRight + 4, y, change > 0 ? UiTheme.GOOD : UiTheme.BAD, false);
            }
            y += LINE;
        }
        if (latest.isEmpty()) {
            graphics.drawString(font, "...", x + 10, y, UiTheme.DIM, false);
        }
    }

    // What the line was when the screen opened. A special bonus you didn't have before counts from 0
    private float before(EquipmentStats.Line line) {
        if (baseline != null) {
            for (EquipmentStats.Line old : baseline) {
                if (old.name().equals(line.name())) {
                    return old.value();
                }
            }
        }
        return baseline == null ? line.value() : 0;
    }

    private static String format(float value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value).replace('.', ',');
    }

    // The vanilla labels are dark grey, which can't be read on the dark panel
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, UiTheme.TITLE, false);
        UiTheme.divider(graphics, 7, inventoryLabelY - 4, imageWidth - 14);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.MUTED, false);
        graphics.drawString(font, "Ringe", JewelryMenu.JEWELRY_X, JewelryMenu.RINGS_Y - 10, 0xFFD27A, false);
        graphics.drawString(font, "Amuletter", JewelryMenu.JEWELRY_X, JewelryMenu.AMULETS_Y - 10, 0xFF8A8A, false);
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
