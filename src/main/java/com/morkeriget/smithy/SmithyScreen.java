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
    private static final int BUTTON_Y = 42;
    private static final int ROW = 20;
    private Button salvage;
    private Button reroll;
    private Button socket;
    private Button unsocket;
    private Button combine;
    // The best rarity "Smelt alle" takes. Remembered while the game runs, so it is the same next time
    private static Rarity salvageUpTo = Rarity.MAGIC;

    public SmithyScreen(SmithyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = SmithyMenu.INVENTORY_Y + 82;
        inventoryLabelY = SmithyMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Skills"), pressed -> backToSkills())
                .bounds(leftPos + imageWidth - 50, topPos + 4, 46, 16)
                .build());
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
                .build());
        addRenderableWidget(Button.builder(Component.literal("Smelt alle"), pressed -> click(SmithyMenu.SALVAGE_ALL + salvageUpTo.ordinal()))
                .bounds(leftPos + 90, topPos + BUTTON_Y + 2 * ROW, 78, 18)
                .build());
        combine = addRenderableWidget(Button.builder(Component.literal("Kombinér 3 sten"), pressed -> click(SmithyMenu.COMBINE))
                .bounds(leftPos + 8, topPos + BUTTON_Y + 3 * ROW, 160, 18)
                .build());
        updateButtons();
    }

    // Two columns of buttons under the slots
    private Button button(int id, String text, int column, int row) {
        return addRenderableWidget(Button.builder(Component.literal(text), pressed -> click(id))
                .bounds(leftPos + 8 + column * 82, topPos + BUTTON_Y + row * ROW, 78, 18)
                .build());
    }

    // E.g. "Til: Sjælden" in the rarity's colour
    private static Component limitText() {
        return Component.literal("Til: ").append(Component.literal(salvageUpTo.displayName).withStyle(salvageUpTo.color));
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
        graphics.drawString(font, "Loot", SmithyMenu.ITEM_X - 4 - font.width("Loot"), SmithyMenu.SLOT_Y + 4, 0x888888, false);
        graphics.drawString(font, "Sten", SmithyMenu.GEM_X - 4 - font.width("Sten"), SmithyMenu.SLOT_Y + 4, 0x888888, false);
        if (!menu.item().isEmpty()) {
            int sockets = Gems.sockets(menu.item());
            String text = sockets == 0 ? "Ingen" : Gems.gems(menu.item()).size() + "/" + sockets + " sokler";
            graphics.drawString(font, text, SmithyMenu.GEM_X + 20, SmithyMenu.SLOT_Y + 4, 0xAAAAAA, false);
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
