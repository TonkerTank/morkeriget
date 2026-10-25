package com.morkeriget.progress;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.bounty.BountyActionPayload;
import com.morkeriget.jewelry.OpenJewelryPayload;
import com.morkeriget.merchant.OpenMerchantPayload;
import com.morkeriget.skill.Skill;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.smithy.OpenSmithyPayload;
import com.morkeriget.stash.OpenStashPayload;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

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
    private static final int COMPACT_WIDTH = 412;
    // One box in the tree
    private static final int NODE_WIDTH = 86;
    private static final int NODE_HEIGHT = 40;
    private static final int NODE_GAP = 4;
    private static final int TREE_ROW_HEIGHT = NODE_HEIGHT + 4;
    // On a low screen the boxes are lower, the "Skill"/"Passiv" line goes (the tooltip says it too) and the header
    // takes one line less, so the whole tree still fits
    private static final int COMPACT_NODE_HEIGHT = 30;
    private static final int HEADER = 52;
    private static final int COMPACT_HEADER = 40;
    // What is under the tree: the keys, the hint below them and the bottom of the panel
    private static final int FOOTER = 44;
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
        return (width - screenWidth()) / 2;
    }

    // In the middle of the screen. The panel starts 12 above this, for the buttons at the top
    private int top() {
        return Math.max(12, (height - contentHeight(compact())) / 2 + 12);
    }

    // From the top of the panel to the bottom, with or without the compact layout
    private static int contentHeight(boolean compact) {
        int rowHeight = (compact ? COMPACT_NODE_HEIGHT : NODE_HEIGHT) + (compact ? 3 : 4);
        return 12 + (compact ? COMPACT_HEADER : HEADER) + treeRows() * rowHeight + FOOTER;
    }

    // A little narrower when the normal width wouldn't fit, with the shared column moved closer to the tree
    private int screenWidth() {
        return width < WIDTH + 14 ? COMPACT_WIDTH : WIDTH;
    }

    private boolean compact() {
        return height < contentHeight(false);
    }

    private int nodeHeight() {
        return compact() ? COMPACT_NODE_HEIGHT : NODE_HEIGHT;
    }

    // Closer together when compact, so the class bonus stays above the stash and bounty buttons
    private int sharedRowHeight() {
        return compact() ? 22 : SHARED_ROW_HEIGHT;
    }

    private int rowHeight() {
        return compact() ? COMPACT_NODE_HEIGHT + 3 : TREE_ROW_HEIGHT;
    }

    // The rows of the player's tree. Three before a class is picked, so the screen stays where it was
    private static int treeRows() {
        PlayerClass playerClass = ClientProgress.get().chosenClass();
        return playerClass == null ? 3 : Math.max(3, SkillTree.rows(playerClass));
    }

    private int nodeX(int column) {
        return left() + column * (NODE_WIDTH + NODE_GAP);
    }

    private int nodeY(int row) {
        return top() + (compact() ? COMPACT_HEADER : HEADER) + row * rowHeight();
    }

    private int sharedX() {
        return left() + screenWidth() - SHARED_WIDTH;
    }

    private int keysY() {
        return nodeY(treeRows()) + 4;
    }

    private int keyX(int slot) {
        return left() + 44 + slot * (KEY_SIZE + 6);
    }

    @Override
    protected void init() {
        // Menus are opened by the server, so ask it to open the equipment screen
        addRenderableWidget(Button.builder(Component.literal("Udstyr"), pressed -> PacketDistributor.sendToServer(OpenJewelryPayload.INSTANCE))
                .bounds(left() + screenWidth() - 60, top() - 4, 60, 16)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("Smedje"), pressed -> PacketDistributor.sendToServer(OpenSmithyPayload.INSTANCE))
                .bounds(left() + screenWidth() - 124, top() - 4, 60, 16)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("Købmand"), pressed -> PacketDistributor.sendToServer(OpenMerchantPayload.INSTANCE))
                // Next to "Skift klasse", since the middle is taken by the class and level
                .bounds(left() + 74, top() - 4, 60, 16)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("Skift klasse"), pressed -> minecraft.setScreen(new ClassScreen()))
                .bounds(left(), top() - 4, 70, 16)
                .build(ThemedButton::new));
        // The top row is full, so the stash and bounties are in the right column above Paragon and "Nulstil point"
        int half = (SHARED_WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Kiste"), pressed -> PacketDistributor.sendToServer(OpenStashPayload.INSTANCE))
                .bounds(sharedX(), keysY() - 44, half, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Component.literal("Opgaver"), pressed -> PacketDistributor.sendToServer(new BountyActionPayload(BountyActionPayload.OPEN)))
                .bounds(sharedX() + SHARED_WIDTH - half, keysY() - 44, half, 18)
                .build(ThemedButton::new));

        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        if (playerClass == null) {
            return;
        }
        for (int row = 0; row < treeRows(); row++) {
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                plusButton(progress, playerClass, upgrades.get(column), nodeX(column) + NODE_WIDTH - 16, nodeY(row) + nodeHeight() - 16);
            }
        }
        for (int row = 0; row < SHARED.size(); row++) {
            plusButton(progress, playerClass, SHARED.get(row), sharedX() + SHARED_WIDTH - 16, nodeY(0) + row * sharedRowHeight() + 4);
        }
        // Paragon points come after level 100
        if (progress.level() >= Progression.MAX_LEVEL) {
            String text = "Paragon" + (progress.paragonPoints() > 0 ? " (" + progress.paragonPoints() + ")" : "");
            addRenderableWidget(Button.builder(Component.literal(text), pressed -> minecraft.setScreen(new ParagonScreen()))
                    .bounds(sharedX(), keysY() - 22, SHARED_WIDTH, 18)
                    .build(ThemedButton::new));
        }
        // Choosing the same class again gives every point back
        addRenderableWidget(Button.builder(Component.literal("Nulstil point"), pressed -> PacketDistributor.sendToServer(new ChooseClassPayload(playerClass)))
                .bounds(sharedX(), keysY(), SHARED_WIDTH, 18)
                .build(ThemedButton::new));
    }

    private void plusButton(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade, int x, int y) {
        Button button = Button.builder(Component.literal("+"), pressed -> PacketDistributor.sendToServer(new UpgradePayload(upgrade)))
                .bounds(x, y, 14, 14)
                .build(ThemedButton::new);
        button.active = progress.points() > 0 && SkillTree.canRaise(playerClass, upgrade, progress::rank);
        addRenderableWidget(button);
    }

    // Called when the server sends new values, e.g. right after clicking +
    public void refresh() {
        rebuildWidgets();
    }

    // The panel, the boxes and the slots go behind the buttons. The icons and text are drawn over them in render
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Little room at the sides, since the screen is already as wide as small screens
        UiTheme.panel(graphics, left() - 6, top() - 12, screenWidth() + 12, keysY() + 40 - (top() - 12));
        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        if (playerClass == null) {
            return;
        }
        for (int row = 0; row < treeRows(); row++) {
            boolean open = SkillTree.isRowOpen(playerClass, row, progress::rank);
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                Upgrade upgrade = upgrades.get(column);
                int x = nodeX(column);
                int y = nodeY(row);
                int rank = progress.rank(upgrade);
                boolean isSelected = upgrade.skill != null && upgrade.skill == selected;
                UiTheme.box(graphics, x, y, NODE_WIDTH, nodeHeight(), isSelected ? 0xFFD700 : rank > 0 ? playerClass.color : 0, open || isSelected);
                UiTheme.slot(graphics, x + 3, y + 3);
            }
        }

        // The shared upgrades and the class bonus in one box, stopping above the stash and bounty buttons
        int bonusY = nodeY(0) + SHARED.size() * sharedRowHeight() + 4;
        int bonusLines = font.split(Component.literal("Klassebonus: " + playerClass.bonus), SHARED_WIDTH).size();
        int boxBottom = Math.min(bonusY + bonusLines * font.lineHeight + 2, keysY() - 47);
        UiTheme.box(graphics, sharedX() - 3, nodeY(0) - 2, SHARED_WIDTH + 5, boxBottom - nodeY(0) + 2, 0, true);
        UiTheme.divider(graphics, sharedX(), bonusY - 4, SHARED_WIDTH - 2);

        int y = keysY();
        for (int slot = 0; slot < SkillTree.SLOTS; slot++) {
            int x = keyX(slot);
            // A gold ring around the keys while a skill waits for one
            if (selected != null) {
                graphics.fill(x, y, x + KEY_SIZE, y + KEY_SIZE, 0xFFFFD700);
            }
            UiTheme.slot(graphics, x + 2, y + 2);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        int left = left();
        int top = top();
        int center = width / 2;

        // Once you have a paragon level it is shown instead of level 100, like in Diablo 4
        boolean paragon = progress.paragonLevel() > 0;
        String title = (playerClass != null ? playerClass.displayName + " - " : "")
                + (paragon ? "Paragon " + progress.paragonLevel() : "Level " + progress.level());
        // The lines stop before the buttons on both sides of the title. "Købmand" ends 134 from the left, the closer side
        int room = screenWidth() / 2 - 134 - font.width(title) / 2 - 10;
        UiTheme.title(graphics, font, Component.literal(title), center, top, paragon ? 0x55FFFF : playerClass != null ? playerClass.color : 0xFFD700,
                Math.max(0, Math.min(40, room)));

        int barLeft = left + 20;
        int barRight = left + screenWidth() - 20;
        boolean maxLevel = progress.level() >= Progression.MAX_LEVEL;
        // At level 100 the bar fills up towards the next paragon level, in another colour
        float fraction = (float) progress.xp() / Math.max(1, progress.xpNeeded());
        UiTheme.bar(graphics, barLeft, top + 16, barRight - barLeft, 4, fraction, maxLevel ? 0x33CCCC : 0x9B4DFF);
        String xpText = progress.xp() + " / " + progress.xpNeeded() + " XP";
        int pointsColor = progress.points() > 0 ? UiTheme.GOOD : UiTheme.MUTED;
        String pointsText = "Skill-points: " + progress.points();
        if (compact()) {
            // Both on one line, to save room
            int gap = 16;
            int start = center - (font.width(xpText) + gap + font.width(pointsText)) / 2;
            graphics.drawString(font, xpText, start, top + 25, UiTheme.MUTED);
            graphics.drawString(font, pointsText, start + font.width(xpText) + gap, top + 25, pointsColor);
        } else {
            graphics.drawCenteredString(font, xpText, center, top + 24, UiTheme.MUTED);
            graphics.drawCenteredString(font, pointsText, center, top + 38, pointsColor);
        }

        if (playerClass == null) {
            graphics.drawCenteredString(font, "Vælg en klasse først", center, nodeY(0) + 8, UiTheme.TITLE);
            return;
        }
        Upgrade hovered = null;
        for (int row = 0; row < treeRows(); row++) {
            boolean open = SkillTree.isRowOpen(playerClass, row, progress::rank);
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                Upgrade upgrade = upgrades.get(column);
                int x = nodeX(column);
                int y = nodeY(row);
                drawNode(graphics, progress, upgrade, x, y, open);
                if (mouseX >= x && mouseX < x + NODE_WIDTH && mouseY >= y && mouseY < y + nodeHeight()) {
                    hovered = upgrade;
                }
            }
            // Next to the row, how many points it takes to open it, if there is room before the shared column. Otherwise
            // the boxes say it, see drawNode. The tooltip says it in words
            if (!open && nodeX(upgrades.size()) + 30 < sharedX()) {
                int x = nodeX(upgrades.size());
                int spent = SkillTree.spent(playerClass, progress::rank);
                graphics.drawString(font, "Låst", x, nodeY(row) + 10, 0xAA5555);
                graphics.drawString(font, spent + "/" + SkillTree.pointsForRow(row), x, nodeY(row) + 21, UiTheme.MUTED);
            }
        }

        for (int row = 0; row < SHARED.size(); row++) {
            Upgrade upgrade = SHARED.get(row);
            int y = nodeY(0) + row * sharedRowHeight();
            int rank = progress.rank(upgrade);
            graphics.drawString(font, upgrade.displayName + "  " + rank + "/" + upgrade.maxRank(), sharedX(), y + 3,
                    rank >= upgrade.maxRank() ? 0xFFD700 : UiTheme.TEXT);
            graphics.drawString(font, upgrade.description, sharedX(), y + 14, UiTheme.MUTED);
        }
        graphics.drawWordWrap(font, Component.literal("Klassebonus: " + playerClass.bonus), sharedX(), nodeY(0) + SHARED.size() * sharedRowHeight() + 4,
                SHARED_WIDTH, playerClass.color);

        drawKeys(graphics, progress);

        if (hovered != null) {
            graphics.renderComponentTooltip(font, tooltip(progress, playerClass, hovered), mouseX, mouseY);
        }
    }

    // The icon, name, rank and the key it is on, over the box from renderBackground. Grey while the row is locked, dim
    // before the first point
    private void drawNode(GuiGraphics graphics, ProgressPayload progress, Upgrade upgrade, int x, int y, boolean open) {
        int rank = progress.rank(upgrade);
        graphics.renderItem(icon(upgrade), x + 3, y + 3);
        if (!open || rank == 0) {
            // A dark layer over the icon, in front of the item
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x + 3, y + 3, x + 19, y + 19, open ? 0x80000000 : 0xC0000000);
            graphics.pose().popPose();
        }
        int nameColor = !open ? UiTheme.DIM : rank >= upgrade.maxRank() ? 0xFFD700 : UiTheme.TEXT;
        graphics.drawString(font, font.plainSubstrByWidth(upgrade.displayName, NODE_WIDTH - 24), x + 22, y + 4, nameColor);
        if (!compact()) {
            String kind = upgrade.isPassive() ? "Passiv" : "Skill";
            graphics.drawString(font, kind, x + 22, y + 14, upgrade.isPassive() ? 0x8888CC : UiTheme.MUTED);
        }
        if (!open && progress.chosenClass() != null && nodeX(SkillTree.row(progress.chosenClass(), upgrade.row).size()) + 30 >= sharedX()) {
            // No room for "Låst" next to the row, so each box says how far you are from opening it
            int spent = SkillTree.spent(progress.chosenClass(), progress::rank);
            graphics.drawString(font, "Låst " + spent + "/" + SkillTree.pointsForRow(upgrade.row), x + 4, y + nodeHeight() - 10, 0xAA5555);
        } else {
            graphics.drawString(font, rank + "/" + upgrade.maxRank(), x + 4, y + nodeHeight() - 10, rank > 0 ? UiTheme.TEXT : UiTheme.DIM);
        }
        int slot = slotOf(progress, upgrade.skill);
        if (slot >= 0) {
            String key = "[" + keyName(slot) + "]";
            graphics.drawString(font, key, x + NODE_WIDTH - 20 - font.width(key), y + nodeHeight() - 10, 0xFFD700);
        }
    }

    // "Taster:" and the four keys with the skill on each. The slots are drawn in renderBackground
    private void drawKeys(GuiGraphics graphics, ProgressPayload progress) {
        int y = keysY();
        graphics.drawString(font, "Taster:", left(), y + 6, UiTheme.MUTED);
        for (int slot = 0; slot < SkillTree.SLOTS; slot++) {
            int x = keyX(slot);
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
        graphics.drawString(font, hint, left(), y + KEY_SIZE + 5, selected != null ? 0xFFD700 : UiTheme.DIM);
    }

    private List<Component> tooltip(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(upgrade.displayName).withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal(upgrade.isPassive() ? "Passiv" : "Skill").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.literal(upgrade.description).withStyle(ChatFormatting.GRAY));
        if (upgrade.skill != null) {
            lines.add(Component.literal("Rank " + (Upgrade.UTILITY_MAX_RANK + 1) + "-" + upgrade.maxRank() + ": skade bliver ved med at stige, resten stopper ved "
                    + Upgrade.UTILITY_MAX_RANK + ", og hver rank giver -" + Math.round(Upgrade.COOLDOWN_PER_RANK * 100) + "% cooldown")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
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
        for (int row = 0; row < treeRows(); row++) {
            List<Upgrade> upgrades = SkillTree.row(playerClass, row);
            for (int column = 0; column < upgrades.size(); column++) {
                Upgrade upgrade = upgrades.get(column);
                int x = nodeX(column);
                int y = nodeY(row);
                if (mouseX >= x && mouseX < x + NODE_WIDTH && mouseY >= y && mouseY < y + nodeHeight()) {
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
