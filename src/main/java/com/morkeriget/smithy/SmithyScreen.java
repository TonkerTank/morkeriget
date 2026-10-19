package com.morkeriget.smithy;

import java.util.Arrays;
import java.util.List;

import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

// The smithy screen. Like the jewelry screen there is no texture, so it is drawn with plain rectangles. Client only
public class SmithyScreen extends AbstractContainerScreen<SmithyMenu> {
    private static final int BUTTON_Y = 42;
    private static final int ROW = 20;
    private Button salvage;
    private Button reroll;
    private Button socket;
    private Button unsocket;
    private Button combine;
    // The best rarity "Smelt alle" takes. Remembered while the game runs, so it is the same next time
    private static Rarity salvageUpTo = Rarity.MAGIC;
    // The codex panel to the right of the normal smithy
    private static final int MAIN_WIDTH = 176;
    private static final int CODEX_X = MAIN_WIDTH + 6;
    private static final int CODEX_WIDTH = 136;
    private static final int CODEX_DESCRIPTION_Y = 62;
    private Button extract;
    private Button imprint;
    private Button previousPower;
    private Button nextPower;
    // The power picked in the codex, or null if the codex is empty. Remembered while the game runs
    private static LegendaryPower selected;

    public SmithyScreen(SmithyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = CODEX_X + CODEX_WIDTH;
        imageHeight = SmithyMenu.INVENTORY_Y + 82;
        inventoryLabelY = SmithyMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Skills"), pressed -> backToSkills())
                .bounds(leftPos + MAIN_WIDTH - 50, topPos + 4, 46, 16)
                .build(ThemedButton::new));
        int codexLeft = leftPos + CODEX_X + 6;
        int codexInner = CODEX_WIDTH - 12;
        extract = addRenderableWidget(Button.builder(Component.literal("Udtræk kraft"), pressed -> click(SmithyMenu.EXTRACT))
                .bounds(codexLeft, topPos + 18, codexInner, 18)
                .tooltip(Tooltip.create(Component.literal("Kraften gemmes i kraftbogen, men genstanden bliver brugt. Ædelsten får du tilbage")))
                .build(ThemedButton::new));
        previousPower = addRenderableWidget(Button.builder(Component.literal("<"), pressed -> step(-1))
                .bounds(codexLeft, topPos + 40, 16, 18)
                .build(ThemedButton::new));
        nextPower = addRenderableWidget(Button.builder(Component.literal(">"), pressed -> step(1))
                .bounds(codexLeft + codexInner - 16, topPos + 40, 16, 18)
                .build(ThemedButton::new));
        imprint = addRenderableWidget(Button.builder(Component.literal("Påfør (" + Codex.IMPRINT_COST + ")"),
                        pressed -> click(SmithyMenu.IMPRINT + selected.ordinal()))
                .bounds(codexLeft, topPos + 118, codexInner, 18)
                .build(ThemedButton::new));
        salvage = button(SmithyMenu.SALVAGE, "Smelt om", 0, 0);
        reroll = button(SmithyMenu.REROLL, "Re-roll", 1, 0);
        socket = button(SmithyMenu.SOCKET, "Sæt i", 0, 1);
        unsocket = button(SmithyMenu.UNSOCKET, "Fjern sten (" + SmithyMenu.UNSOCKET_COST + ")", 1, 1);
        // Clicking the first one picks how good the items "Smelt alle" takes can be
        addRenderableWidget(Button.builder(limitText(), pressed -> {
                    salvageUpTo = Rarity.values()[(salvageUpTo.ordinal() + 1) % (Rarity.LEGENDARY.ordinal() + 1)];
                    pressed.setMessage(limitText());
                })
                .bounds(leftPos + 8, topPos + BUTTON_Y + 2 * ROW, 78, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("Smelt alle"), pressed -> click(SmithyMenu.SALVAGE_ALL + salvageUpTo.ordinal()))
                .bounds(leftPos + 90, topPos + BUTTON_Y + 2 * ROW, 78, 18)
                .build(ThemedButton::new));
        combine = addRenderableWidget(Button.builder(Component.literal("Kombinér 3 sten"), pressed -> click(SmithyMenu.COMBINE))
                .bounds(leftPos + 8, topPos + BUTTON_Y + 3 * ROW, 160, 18)
                .build(ThemedButton::new));
        updateButtons();
    }

    // Two columns of buttons under the slots
    private Button button(int id, String text, int column, int row) {
        return addRenderableWidget(Button.builder(Component.literal(text), pressed -> click(id))
                .bounds(leftPos + 8 + column * 82, topPos + BUTTON_Y + row * ROW, 78, 18)
                .build(ThemedButton::new));
    }

    // E.g. "Til: Sjælden" in the rarity's colour
    private static Component limitText() {
        return Component.literal("Til: ").append(Component.literal(salvageUpTo.displayName).withStyle(salvageUpTo.color));
    }

    // The powers in the codex, in the same order as LegendaryPower.values()
    private List<LegendaryPower> known() {
        return Arrays.stream(LegendaryPower.values()).filter(menu::knows).toList();
    }

    // Goes to the previous or next power in the codex
    private void step(int direction) {
        List<LegendaryPower> known = known();
        if (!known.isEmpty()) {
            int index = Math.max(0, known.indexOf(selected));
            selected = known.get(Math.floorMod(index + direction, known.size()));
        }
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
        socket.active = rarity != null && Gems.hasFreeSocket(menu.item()) && Gem.of(menu.gem()) != null;
        unsocket.active = rarity != null && Gems.hasGems(menu.item()) && essence() >= SmithyMenu.UNSOCKET_COST;
        int tier = Gems.tier(menu.gem());
        boolean canCombine = Gem.of(menu.gem()) != null && menu.gem().getCount() >= 3 && tier < Gem.MAX_TIER;
        combine.active = canCombine && essence() >= Smithy.combineCost(tier);
        combine.setMessage(Component.literal(canCombine
                ? "Kombinér 3 → " + Gem.of(menu.gem()).name(tier + 1) + " (" + Smithy.combineCost(tier) + ")"
                : "Kombinér 3 ens sten"));

        // The codex comes from the server a moment after the screen opens, so the picked power is checked every tick
        List<LegendaryPower> known = known();
        if (selected == null || !known.contains(selected)) {
            selected = known.isEmpty() ? null : known.get(0);
        }
        LegendaryPower onItem = LegendaryPower.of(menu.item());
        extract.active = onItem != null && !menu.knows(onItem);
        previousPower.active = known.size() > 1;
        nextPower.active = known.size() > 1;
        imprint.active = selected != null && rarity == Rarity.LEGENDARY && onItem != selected && essence() >= Codex.IMPRINT_COST;
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
        // Both panels are 1 pixel in from the edges, so their frames don't run into each other in the gap
        UiTheme.panel(graphics, leftPos + 1, topPos + 1, MAIN_WIDTH - 2, imageHeight - 2);
        // The codex panel stops a little below the Apply button
        UiTheme.panel(graphics, leftPos + CODEX_X + 1, topPos + 1, CODEX_WIDTH - 2, 140);
        for (Slot slot : menu.slots) {
            UiTheme.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }
    }

    // The vanilla labels are dark grey, which can't be read on the dark panel
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, UiTheme.TITLE, false);
        UiTheme.divider(graphics, 7, inventoryLabelY - 4, MAIN_WIDTH - 14);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, UiTheme.MUTED, false);
        // On the same line as the "Inventory" label, but at the right edge
        String essence = "Essens: " + essence();
        graphics.drawString(font, essence, MAIN_WIDTH - 8 - font.width(essence), inventoryLabelY, 0xC080FF, false);
        renderCodex(graphics);
        graphics.drawString(font, "Loot", SmithyMenu.ITEM_X - 4 - font.width("Loot"), SmithyMenu.SLOT_Y + 4, UiTheme.MUTED, false);
        graphics.drawString(font, "Sten", SmithyMenu.GEM_X - 4 - font.width("Sten"), SmithyMenu.SLOT_Y + 4, UiTheme.MUTED, false);
        if (!menu.item().isEmpty()) {
            int sockets = Gems.sockets(menu.item());
            String text = sockets == 0 ? "Ingen" : Gems.gems(menu.item()).size() + "/" + sockets + " sokler";
            graphics.drawString(font, text, SmithyMenu.GEM_X + 20, SmithyMenu.SLOT_Y + 4, UiTheme.MUTED, false);
        }
    }

    // The title with how many powers you have, the picked power between < and >, and what it does
    private void renderCodex(GuiGraphics graphics) {
        int center = CODEX_X + CODEX_WIDTH / 2;
        String title = "Kraftbog " + known().size() + "/" + LegendaryPower.values().length;
        graphics.drawString(font, title, center - font.width(title) / 2, 6, UiTheme.TITLE, false);
        if (selected == null) {
            graphics.drawString(font, "Tom", center - font.width("Tom") / 2, 45, UiTheme.MUTED, false);
            int y = CODEX_DESCRIPTION_Y;
            for (FormattedCharSequence line : font.split(Component.literal("Udtræk kræfter fra legendariske genstande for at gemme dem her"), CODEX_WIDTH - 12)) {
                graphics.drawString(font, line, CODEX_X + 6, y, UiTheme.MUTED, false);
                y += 10;
            }
            return;
        }
        String name = font.plainSubstrByWidth(selected.displayName, CODEX_WIDTH - 12 - 2 * 18);
        graphics.drawString(font, name, center - font.width(name) / 2, 45, 0xFFAA00, false);
        int y = CODEX_DESCRIPTION_Y;
        if (selected.playerClass != null) {
            graphics.drawString(font, selected.playerClass.displayName, CODEX_X + 6, y, selected.playerClass.color, false);
            y += 10;
        }
        for (FormattedCharSequence line : font.split(Component.literal(selected.description), CODEX_WIDTH - 12)) {
            graphics.drawString(font, line, CODEX_X + 6, y, 0xFFFF55, false);
            y += 10;
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
