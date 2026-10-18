package com.morkeriget.progress;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.jewelry.OpenJewelryPayload;
import com.morkeriget.merchant.OpenMerchantPayload;
import com.morkeriget.skill.Skill;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.smithy.OpenSmithyPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

// The screen opened with K: class, level and XP at the top, the class's skill tree on the left, the upgrades every
// class has on the right, and the keys 1-4 at the bottom. Click a skill and press 1-4 (or click a key) to put it on
// that key. Client only
public class SkillScreen extends Screen {
    private static final int WIDTH = 440;
    // One box in the tree
    private static final int NODE_WIDTH = 86;
    private static final int NODE_HEIGHT = 40;
    private static final int NODE_GAP = 4;
    private static final int TREE_ROW_HEIGHT = NODE_HEIGHT + 4;
    // The column with the shared upgrades
    private static final int SHARED_WIDTH = 140;
    private static final int SHARED_ROW_HEIGHT = 28;
    private static final List<Upgrade> SHARED = List.of(Upgrade.VITALITY, Upgrade.SPIRIT, Upgrade.STRENGTH);
    private static final int KEY_SIZE = 20;

    // The skill clicked last, waiting for a key
    private Skill selected;

    public SkillScreen() {
        super(Component.literal("Skills og level"));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return height / 2 - 100;
    }

    private int nodeX(int column) {
        return left() + column * (NODE_WIDTH + NODE_GAP);
    }

    private int nodeY(int row) {
        return top() + 52 + row * TREE_ROW_HEIGHT;
    }

    private int sharedX() {
        return left() + WIDTH - SHARED_WIDTH;
    }

    private int keysY() {
        return nodeY(SkillTree.ROWS) + 4;
    }

    private int keyX(int slot) {
        return left() + 44 + slot * (KEY_SIZE + 6);
    }

    @Override
    protected void init() {
        // Menus are opened by the server, so ask it to open the jewelry slots
        addRenderableWidget(Button.builder(Component.literal("Smykker"), pressed -> PacketDistributor.sendToServer(OpenJewelryPayload.INSTANCE))
                .bounds(left() + WIDTH - 60, top() - 4, 60, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Smedje"), pressed -> PacketDistributor.sendToServer(OpenSmithyPayload.INSTANCE))
                .bounds(left() + WIDTH - 124, top() - 4, 60, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Købmand"), pressed -> PacketDistributor.sendToServer(OpenMerchantPayload.INSTANCE))
                // Next to "Skift klasse", since the middle is taken by the class and level
                .bounds(left() + 74, top() - 4, 60, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Skift klasse"), pressed -> minecraft.setScreen(new ClassScreen()))
                .bounds(left(), top() - 4, 70, 16)
                .build());

        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        if (playerClass == null) {
            return;
        }
        for (int row = 0; row < SkillTree.ROWS; row++) {
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                plusButton(progress, playerClass, upgrades.get(column), nodeX(column) + NODE_WIDTH - 16, nodeY(row) + NODE_HEIGHT - 16);
            }
        }
        for (int row = 0; row < SHARED.size(); row++) {
            plusButton(progress, playerClass, SHARED.get(row), sharedX() + SHARED_WIDTH - 16, nodeY(0) + row * SHARED_ROW_HEIGHT + 4);
        }
        // Choosing the same class again gives every point back
        addRenderableWidget(Button.builder(Component.literal("Nulstil point"), pressed -> PacketDistributor.sendToServer(new ChooseClassPayload(playerClass)))
                .bounds(sharedX(), keysY(), SHARED_WIDTH, 18)
                .build());
    }

    private void plusButton(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade, int x, int y) {
        Button button = Button.builder(Component.literal("+"), pressed -> PacketDistributor.sendToServer(new UpgradePayload(upgrade)))
                .bounds(x, y, 14, 14)
                .build();
        button.active = progress.points() > 0 && SkillTree.canRaise(playerClass, upgrade, progress::rank);
        addRenderableWidget(button);
    }

    // Called when the server sends new values, e.g. right after clicking +
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        int left = left();
        int top = top();
        int center = width / 2;

        String title = (playerClass != null ? playerClass.displayName + " - " : "") + "Level " + progress.level();
        graphics.drawCenteredString(font, title, center, top, playerClass != null ? playerClass.color : 0xFFD700);

        int barLeft = left + 20;
        int barRight = left + WIDTH - 20;
        boolean maxLevel = progress.level() >= Progression.MAX_LEVEL;
        float fraction = maxLevel ? 1 : (float) progress.xp() / progress.xpNeeded();
        graphics.fill(barLeft - 1, top + 15, barRight + 1, top + 21, 0xFF000000);
        graphics.fill(barLeft, top + 16, barRight, top + 20, 0xFF2A1A3A);
        graphics.fill(barLeft, top + 16, barLeft + Math.round((barRight - barLeft) * fraction), top + 20, 0xFF9B4DFF);
        String xpText = maxLevel ? "Max level" : progress.xp() + " / " + progress.xpNeeded() + " XP";
        graphics.drawCenteredString(font, xpText, center, top + 24, 0xAAAAAA);

        int pointsColor = progress.points() > 0 ? 0x55FF55 : 0xAAAAAA;
        graphics.drawCenteredString(font, "Skill-points: " + progress.points(), center, top + 38, pointsColor);

        if (playerClass == null) {
            graphics.drawCenteredString(font, "Vælg en klasse først", center, nodeY(0) + 8, 0xFFD700);
            return;
        }
        Upgrade hovered = null;
        for (int row = 0; row < SkillTree.ROWS; row++) {
            boolean open = SkillTree.isRowOpen(playerClass, row, progress::rank);
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                Upgrade upgrade = upgrades.get(column);
                int x = nodeX(column);
                int y = nodeY(row);
                drawNode(graphics, progress, playerClass, upgrade, x, y, open);
                if (mouseX >= x && mouseX < x + NODE_WIDTH && mouseY >= y && mouseY < y + NODE_HEIGHT) {
                    hovered = upgrade;
                }
            }
            if (!open) {
                // Next to the row, how many points it takes to open it. The tooltip says it in words
                int x = nodeX(upgrades.size());
                int spent = SkillTree.spent(playerClass, progress::rank);
                graphics.drawString(font, "Låst", x, nodeY(row) + 10, 0xAA5555);
                graphics.drawString(font, spent + "/" + SkillTree.pointsForRow(row), x, nodeY(row) + 21, 0x888888);
            }
        }

        for (int row = 0; row < SHARED.size(); row++) {
            Upgrade upgrade = SHARED.get(row);
            int y = nodeY(0) + row * SHARED_ROW_HEIGHT;
            int rank = progress.rank(upgrade);
            graphics.drawString(font, upgrade.displayName + "  " + rank + "/" + upgrade.maxRank(), sharedX(), y + 3,
                    rank >= upgrade.maxRank() ? 0xFFD700 : 0xFFFFFF);
            graphics.drawString(font, upgrade.description, sharedX(), y + 14, 0x888888);
        }
        graphics.drawWordWrap(font, Component.literal("Klassebonus: " + playerClass.bonus), sharedX(), nodeY(0) + SHARED.size() * SHARED_ROW_HEIGHT + 4,
                SHARED_WIDTH, playerClass.color);

        drawKeys(graphics, progress);

        if (hovered != null) {
            graphics.renderComponentTooltip(font, tooltip(progress, playerClass, hovered), mouseX, mouseY);
        }
    }

    // A box with the icon, name, rank and the key it is on. Grey while the row is locked, dim before the first point
    private void drawNode(GuiGraphics graphics, ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade, int x, int y, boolean open) {
        int rank = progress.rank(upgrade);
        boolean isSelected = upgrade.skill != null && upgrade.skill == selected;
        int border = isSelected ? 0xFFFFD700 : rank > 0 ? 0xFF000000 | playerClass.color : 0xFF333340;
        graphics.fill(x, y, x + NODE_WIDTH, y + NODE_HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + NODE_WIDTH - 1, y + NODE_HEIGHT - 1, open ? 0xFF1E1E26 : 0xFF141418);
        graphics.renderItem(icon(upgrade), x + 3, y + 3);
        if (!open || rank == 0) {
            // A dark layer over the icon, in front of the item
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x + 3, y + 3, x + 19, y + 19, open ? 0x80000000 : 0xC0000000);
            graphics.pose().popPose();
        }
        int nameColor = !open ? 0x666666 : rank >= upgrade.maxRank() ? 0xFFD700 : 0xFFFFFF;
        graphics.drawString(font, font.plainSubstrByWidth(upgrade.displayName, NODE_WIDTH - 24), x + 22, y + 4, nameColor);
        String kind = upgrade.isPassive() ? "Passiv" : "Skill";
        graphics.drawString(font, kind, x + 22, y + 14, upgrade.isPassive() ? 0x8888CC : 0x888888);
        graphics.drawString(font, rank + "/" + upgrade.maxRank(), x + 4, y + NODE_HEIGHT - 12, rank > 0 ? 0xFFFFFF : 0x777777);
        int slot = slotOf(progress, upgrade.skill);
        if (slot >= 0) {
            String key = "[" + keyName(slot) + "]";
            graphics.drawString(font, key, x + NODE_WIDTH - 20 - font.width(key), y + NODE_HEIGHT - 12, 0xFFD700);
        }
    }

    // "Taster:" and the four keys with the skill on each
    private void drawKeys(GuiGraphics graphics, ProgressPayload progress) {
        int y = keysY();
        graphics.drawString(font, "Taster:", left(), y + 6, 0xAAAAAA);
        for (int slot = 0; slot < SkillTree.SLOTS; slot++) {
            int x = keyX(slot);
            graphics.fill(x, y, x + KEY_SIZE, y + KEY_SIZE, selected != null ? 0xFFFFD700 : 0xFF000000);
            graphics.fill(x + 1, y + 1, x + KEY_SIZE - 1, y + KEY_SIZE - 1, 0xFF2A2A2A);
            Skill skill = progress.slot(slot);
            if (skill != null) {
                graphics.renderItem(SkillBar.icon(skill), x + 2, y + 2);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            String key = keyName(slot);
            graphics.drawString(font, key, x + KEY_SIZE - 1 - font.width(key), y + KEY_SIZE - 9, 0xFFD700);
            graphics.pose().popPose();
        }
        String hint = selected != null
                ? "Tryk 1-4 eller klik en tast for at sætte " + selected.displayName + " på den"
                : "Klik en skill og tryk 1-4 for at sætte den på en tast";
        graphics.drawString(font, hint, left(), y + KEY_SIZE + 5, selected != null ? 0xFFD700 : 0x777777);
    }

    private List<Component> tooltip(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(upgrade.displayName).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(upgrade.isPassive() ? "Passiv" : "Skill").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal(upgrade.description).withStyle(ChatFormatting.GRAY));
        if (!SkillTree.isRowOpen(playerClass, upgrade.row, progress::rank)) {
            lines.add(Component.literal("Kræver " + SkillTree.pointsForRow(upgrade.row) + " point i træet").withStyle(ChatFormatting.RED));
        } else if (upgrade.skill != null && progress.rank(upgrade) == 0) {
            lines.add(Component.literal("Det første point låser den op").withStyle(ChatFormatting.YELLOW));
        } else if (upgrade.skill != null) {
            lines.add(Component.literal("Klik for at sætte den på en tast").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    // The key (0 to 3) the skill is on, or -1
    private static int slotOf(ProgressPayload progress, Skill skill) {
        if (skill == null) {
            return -1;
        }
        for (int slot = 0; slot < SkillTree.SLOTS; slot++) {
            if (progress.slot(slot) == skill) {
                return slot;
            }
        }
        return -1;
    }

    private static String keyName(int slot) {
        return SkillKeys.SKILL_SLOTS.get(slot).getTranslatedKeyMessage().getString();
    }

    // Skills use their skill bar icon, passives get one of their own
    private static ItemStack icon(Upgrade upgrade) {
        if (upgrade.skill != null) {
            return SkillBar.icon(upgrade.skill);
        }
        return new ItemStack(switch (upgrade) {
            case UNYIELDING -> Items.SHIELD;
            case BLOODTHIRSTY -> Items.RED_DYE;
            case RELENTLESS -> Items.IRON_AXE;
            case GLASS_CANNON -> Items.GLASS;
            case ARCANE_FLOW -> Items.LAPIS_LAZULI;
            case TIME_WARP -> Items.CLOCK;
            case MARKSMAN -> Items.SPECTRAL_ARROW;
            case AGILE -> Items.SUGAR;
            case PACK_LEADER -> Items.LEAD;
            case GRAVE_CHILL -> Items.SOUL_LANTERN;
            case DEATH_LORD -> Items.SKELETON_SKULL;
            case SOUL_HARVEST -> Items.SCULK;
            default -> Items.BOOK;
        });
    }

    // Clicking an unlocked skill picks it, clicking a key puts the picked skill on it
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        if (playerClass == null) {
            return false;
        }
        for (int slot = 0; slot < SkillTree.SLOTS; slot++) {
            int x = keyX(slot);
            if (selected != null && mouseX >= x && mouseX < x + KEY_SIZE && mouseY >= keysY() && mouseY < keysY() + KEY_SIZE) {
                assign(slot);
                return true;
            }
        }
        for (int row = 0; row < SkillTree.ROWS; row++) {
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                Upgrade upgrade = upgrades.get(column);
                int x = nodeX(column);
                int y = nodeY(row);
                if (mouseX >= x && mouseX < x + NODE_WIDTH && mouseY >= y && mouseY < y + NODE_HEIGHT) {
                    selected = upgrade.skill != null && progress.rank(upgrade) > 0 && upgrade.skill != selected ? upgrade.skill : null;
                    return true;
                }
            }
        }
        selected = null;
        return false;
    }

    private void assign(int slot) {
        PacketDistributor.sendToServer(new AssignSlotPayload(slot, selected));
        selected = null;
    }

    // K closes the screen again, and 1-4 put the picked skill on that key
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SkillKeys.SKILLS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (selected != null) {
            for (int slot = 0; slot < SkillTree.SLOTS; slot++) {
                if (SkillKeys.SKILL_SLOTS.get(slot).matches(keyCode, scanCode)) {
                    assign(slot);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
