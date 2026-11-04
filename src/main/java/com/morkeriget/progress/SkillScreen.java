package com.morkeriget.progress;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Text;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// The screen opened with K: class, level and XP at the top, the class's skill tree on the left, the upgrades every
// class has on the right, and the keys 1-4 at the bottom. Click a skill and press 1-4 (or click a key) to put it on
// that key. In the reworked trees, lines show which box needs which, a skill with variants has a small button in its
// corner that opens a window to pick one, and the keystones share one box that opens the same kind of window.
// Client only
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
    // The button in the corner of a skill with variants
    private static final int VARIANT_BUTTON = 12;
    // The window where a variant or keystone is picked
    private static final int PICK_WIDTH = 240;
    private static final int PICK_ROW = 46;

    // A box in the tree. For the keystones, upgrade is the one picked, or the first one
    private record Box(Upgrade upgrade, boolean keystones, int x, int y) {
    }

    // The skill clicked last, waiting for a key
    private Skill selected;
    // What the pick window shows, or null while it is closed. owner is the skill for variants, null for keystones
    private List<Upgrade> choices;
    private Upgrade owner;

    public SkillScreen() {
        super(Text.tr("skills.title"));
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

    // Every box in the class's tree: the skills and passives, and one for the keystones if the class has them
    private List<Box> boxes(ProgressPayload progress, PlayerClass playerClass) {
        List<Box> boxes = new ArrayList<>();
        for (Upgrade upgrade : Upgrade.values()) {
            if (upgrade.playerClass == playerClass && (upgrade.kind == Upgrade.Kind.SKILL || upgrade.kind == Upgrade.Kind.PASSIVE)) {
                boxes.add(new Box(upgrade, false, nodeX(SkillTree.column(upgrade)), nodeY(upgrade.row)));
            }
        }
        List<Upgrade> keystones = Upgrade.keystones(playerClass);
        if (!keystones.isEmpty()) {
            Upgrade first = keystones.get(0);
            Upgrade shown = keystones.stream().filter(keystone -> progress.rank(keystone) > 0).findFirst().orElse(first);
            boxes.add(new Box(shown, true, nodeX(first.column), nodeY(first.row)));
        }
        return boxes;
    }

    private boolean inside(Box box, double mouseX, double mouseY) {
        return mouseX >= box.x && mouseX < box.x + NODE_WIDTH && mouseY >= box.y && mouseY < box.y + nodeHeight();
    }

    // The keystone box counts as open when the first keystone is, they all have the same rules
    private static boolean isOpen(ProgressPayload progress, PlayerClass playerClass, Box box) {
        Upgrade upgrade = box.keystones ? Upgrade.keystones(playerClass).get(0) : box.upgrade;
        return SkillTree.isOpen(playerClass, upgrade, progress::rank);
    }

    @Override
    protected void init() {
        // Menus are opened by the server, so ask it to open the equipment screen
        addRenderableWidget(Button.builder(Text.tr("button.equipment"), pressed -> PacketDistributor.sendToServer(OpenJewelryPayload.INSTANCE))
                .bounds(left() + screenWidth() - 60, top() - 4, 60, 16)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("smithy.title"), pressed -> PacketDistributor.sendToServer(OpenSmithyPayload.INSTANCE))
                .bounds(left() + screenWidth() - 124, top() - 4, 60, 16)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("merchant.title"), pressed -> PacketDistributor.sendToServer(OpenMerchantPayload.INSTANCE))
                // Next to "Skift klasse", since the middle is taken by the class and level
                .bounds(left() + 74, top() - 4, 60, 16)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("button.change_class"), pressed -> minecraft.setScreen(new ClassScreen()))
                .bounds(left(), top() - 4, 70, 16)
                .build(ThemedButton::new));
        // The top row is full, so the stash and bounties are in the right column above Paragon and "Nulstil point"
        int half = (SHARED_WIDTH - 4) / 2;
        addRenderableWidget(Button.builder(Text.tr("stash.title"), pressed -> PacketDistributor.sendToServer(OpenStashPayload.INSTANCE))
                .bounds(sharedX(), keysY() - 44, half, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("bounties.title"), pressed -> PacketDistributor.sendToServer(new BountyActionPayload(BountyActionPayload.OPEN)))
                .bounds(sharedX() + SHARED_WIDTH - half, keysY() - 44, half, 18)
                .build(ThemedButton::new));

        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        if (playerClass == null) {
            return;
        }
        for (Box box : boxes(progress, playerClass)) {
            // The keystone box has no +, it is clicked to open the window
            if (box.keystones) {
                continue;
            }
            plusButton(progress, playerClass, box.upgrade, box.x + NODE_WIDTH - 16, box.y + nodeHeight() - 16);
            List<Upgrade> variants = box.upgrade.variants();
            if (!variants.isEmpty()) {
                // The icon of the picked variant, or a ?, is drawn over it in render
                addRenderableWidget(Button.builder(Component.empty(), pressed -> openPicker(box.upgrade, variants))
                        .bounds(variantButtonX(box), variantButtonY(box), VARIANT_BUTTON, VARIANT_BUTTON)
                        .build(ThemedButton::new));
            }
        }
        for (int row = 0; row < SHARED.size(); row++) {
            plusButton(progress, playerClass, SHARED.get(row), sharedX() + SHARED_WIDTH - 16, nodeY(0) + row * sharedRowHeight() + 4);
        }
        // Paragon points come after level 100
        if (progress.level() >= Progression.MAX_LEVEL) {
            Component text = progress.paragonPoints() > 0 ? Text.tr("skills.paragon_points", progress.paragonPoints()) : Text.tr("paragon.title");
            addRenderableWidget(Button.builder(text, pressed -> minecraft.setScreen(new ParagonScreen()))
                    .bounds(sharedX(), keysY() - 22, SHARED_WIDTH, 18)
                    .build(ThemedButton::new));
        }
        // Choosing the same class again gives every point back
        addRenderableWidget(Button.builder(Text.tr("skills.reset"), pressed -> PacketDistributor.sendToServer(new ChooseClassPayload(playerClass)))
                .bounds(sharedX(), keysY(), SHARED_WIDTH, 18)
                .build(ThemedButton::new));
    }

    private static int variantButtonX(Box box) {
        return box.x + NODE_WIDTH - VARIANT_BUTTON - 2;
    }

    private static int variantButtonY(Box box) {
        return box.y + 2;
    }

    private void plusButton(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade, int x, int y) {
        Button button = Button.builder(Component.literal("+"), pressed -> PacketDistributor.sendToServer(new UpgradePayload(upgrade)))
                .bounds(x, y, 14, 14)
                .build(ThemedButton::new);
        button.active = SkillTree.canRaise(playerClass, upgrade, progress.points(), progress::rank);
        addRenderableWidget(button);
    }

    private void openPicker(Upgrade owner, List<Upgrade> choices) {
        this.owner = owner;
        this.choices = choices;
        selected = null;
    }

    // Called when the server sends new values, e.g. right after clicking +
    public void refresh() {
        rebuildWidgets();
    }

    // The panel, the lines, the boxes and the slots go behind the buttons. The icons and text are drawn over them in render
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
        List<Box> boxes = boxes(progress, playerClass);
        for (Box box : boxes) {
            if (box.upgrade.parent != null && !box.keystones) {
                drawLine(graphics, box.upgrade.parent, box, progress.rank(box.upgrade.parent) > 0 ? 0xFF000000 | playerClass.color : 0xFF3A3A3A);
            }
        }
        for (Box box : boxes) {
            int rank = box.keystones ? (progress.rank(box.upgrade) > 0 ? 1 : 0) : progress.rank(box.upgrade);
            boolean isSelected = box.upgrade.skill != null && box.upgrade.skill == selected;
            int color = isSelected ? 0xFFD700 : rank > 0 ? playerClass.color : 0;
            UiTheme.box(graphics, box.x, box.y, NODE_WIDTH, nodeHeight(), color, isOpen(progress, playerClass, box) || isSelected);
            UiTheme.slot(graphics, box.x + 3, box.y + 3);
        }

        // The shared upgrades and the class bonus in one box, stopping above the stash and bounty buttons
        int bonusY = nodeY(0) + SHARED.size() * sharedRowHeight() + 4;
        int bonusLines = font.split(Text.tr("skills.class_bonus", playerClass.bonus()), SHARED_WIDTH).size();
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

    // From the bottom of the parent's box to the top of the child's, with a bend if they are in different columns
    private void drawLine(GuiGraphics graphics, Upgrade parent, Box child, int color) {
        int fromX = nodeX(SkillTree.column(parent)) + NODE_WIDTH / 2;
        int fromY = nodeY(parent.row) + nodeHeight();
        int toX = child.x + NODE_WIDTH / 2;
        int toY = child.y;
        if (fromX == toX) {
            graphics.fill(fromX - 1, fromY, fromX + 1, toY, color);
            return;
        }
        int middle = (fromY + toY) / 2;
        graphics.fill(fromX - 1, fromY, fromX + 1, middle + 1, color);
        graphics.fill(Math.min(fromX, toX) - 1, middle - 1, Math.max(fromX, toX) + 1, middle + 1, color);
        graphics.fill(toX - 1, middle - 1, toX + 1, toY, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The buttons under the pick window don't light up while it is open
        boolean picking = choices != null;
        super.render(graphics, picking ? -1 : mouseX, picking ? -1 : mouseY, partialTick);
        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        int left = left();
        int top = top();
        int center = width / 2;

        // Once you have a paragon level it is shown instead of level 100, like in Diablo 4
        boolean paragon = progress.paragonLevel() > 0;
        MutableComponent level = paragon ? Text.tr("paragon.level", progress.paragonLevel()) : Text.tr("skills.level", progress.level());
        Component title = playerClass != null ? playerClass.displayName().append(" - ").append(level) : level;
        // The lines stop before the buttons on both sides of the title. "Købmand" ends 134 from the left, the closer side
        int room = screenWidth() / 2 - 134 - font.width(title) / 2 - 10;
        UiTheme.title(graphics, font, title, center, top, paragon ? 0x55FFFF : playerClass != null ? playerClass.color : 0xFFD700,
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
            graphics.drawCenteredString(font, Text.tr("skills.pick_class_first"), center, nodeY(0) + 8, UiTheme.TITLE);
            return;
        }
        Box hovered = null;
        for (Box box : boxes(progress, playerClass)) {
            drawNode(graphics, progress, playerClass, box);
            if (inside(box, mouseX, mouseY)) {
                hovered = box;
            }
        }

        for (int row = 0; row < SHARED.size(); row++) {
            Upgrade upgrade = SHARED.get(row);
            int y = nodeY(0) + row * sharedRowHeight();
            int rank = progress.rank(upgrade);
            graphics.drawString(font, upgrade.displayName().append("  " + rank + "/" + upgrade.maxRank()), sharedX(), y + 3,
                    rank >= upgrade.maxRank() ? 0xFFD700 : UiTheme.TEXT);
            graphics.drawString(font, upgrade.description(), sharedX(), y + 14, UiTheme.MUTED);
        }
        graphics.drawWordWrap(font, Text.tr("skills.class_bonus", playerClass.bonus()), sharedX(), nodeY(0) + SHARED.size() * sharedRowHeight() + 4,
                SHARED_WIDTH, playerClass.color);

        drawKeys(graphics, progress);

        if (picking) {
            drawPicker(graphics, progress, playerClass, mouseX, mouseY);
        } else if (hovered != null) {
            graphics.renderComponentTooltip(font, hovered.keystones ? keystoneTooltip(progress, playerClass, hovered)
                    : tooltip(progress, playerClass, hovered.upgrade), mouseX, mouseY);
        }
    }

    // The icon, name, rank and the key it is on, over the box from renderBackground. Grey while it is locked, dim
    // before the first point
    private void drawNode(GuiGraphics graphics, ProgressPayload progress, PlayerClass playerClass, Box box) {
        Upgrade upgrade = box.upgrade;
        int x = box.x;
        int y = box.y;
        boolean open = isOpen(progress, playerClass, box);
        int rank = progress.rank(upgrade);
        // The keystone box goes to 1 when one of them is picked
        int maxRank = box.keystones ? 1 : upgrade.maxRank();
        graphics.renderItem(box.keystones && rank == 0 ? new ItemStack(Items.NETHER_STAR) : icon(upgrade), x + 3, y + 3);
        if (!open || rank == 0) {
            // A dark layer over the icon, in front of the item
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x + 3, y + 3, x + 19, y + 19, open ? 0x80000000 : 0xC0000000);
            graphics.pose().popPose();
        }
        List<Upgrade> variants = upgrade.variants();
        int nameRoom = NODE_WIDTH - 24 - (variants.isEmpty() ? 0 : VARIANT_BUTTON + 2);
        String name = box.keystones && rank == 0 ? Text.tr("skills.keystone").getString() : upgrade.displayName().getString();
        int nameColor = !open ? UiTheme.DIM : rank >= maxRank ? 0xFFD700 : UiTheme.TEXT;
        graphics.drawString(font, font.plainSubstrByWidth(name, nameRoom), x + 22, y + 4, nameColor);
        if (!compact()) {
            String kind = box.keystones ? Text.tr("skills.keystone").getString() : upgrade.isPassive() ? "Passiv" : "Skill";
            int kindColor = box.keystones ? 0xCC9944 : upgrade.isPassive() ? 0x8888CC : UiTheme.MUTED;
            graphics.drawString(font, kind, x + 22, y + 14, kindColor);
        }
        Upgrade rules = box.keystones ? Upgrade.keystones(playerClass).get(0) : upgrade;
        int spent = SkillTree.spent(playerClass, progress::rank);
        if (!open && spent < SkillTree.pointsNeeded(rules)) {
            // How far you are from opening it
            graphics.drawString(font, Text.tr("skills.locked_progress", spent, SkillTree.pointsNeeded(rules)), x + 4, y + nodeHeight() - 10, 0xAA5555);
        } else {
            graphics.drawString(font, rank + "/" + maxRank, x + 4, y + nodeHeight() - 10, rank > 0 ? UiTheme.TEXT : UiTheme.DIM);
        }
        int slot = slotOf(progress, upgrade.skill);
        if (slot >= 0) {
            String key = "[" + keyName(slot) + "]";
            graphics.drawString(font, key, x + NODE_WIDTH - 20 - font.width(key), y + nodeHeight() - 10, 0xFFD700);
        }
        if (!variants.isEmpty()) {
            drawVariantButton(graphics, progress, box, variants);
        }
    }

    // Over the button in the corner: the picked variant's icon, a gold ? when one can be picked, or a grey one before that
    private void drawVariantButton(GuiGraphics graphics, ProgressPayload progress, Box box, List<Upgrade> variants) {
        int x = variantButtonX(box);
        int y = variantButtonY(box);
        Upgrade picked = variants.stream().filter(variant -> progress.rank(variant) > 0).findFirst().orElse(null);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        if (picked != null) {
            graphics.pose().translate(x + 1, y + 1, 0);
            graphics.pose().scale(0.625f, 0.625f, 1);
            graphics.renderItem(icon(picked), 0, 0);
        } else {
            boolean ready = progress.rank(box.upgrade) >= Upgrade.VARIANT_RANK;
            graphics.drawCenteredString(font, "?", x + VARIANT_BUTTON / 2 + 1, y + 2, ready ? 0xFFD700 : UiTheme.DIM);
        }
        graphics.pose().popPose();
    }

    // "Taster:" and the four keys with the skill on each. The slots are drawn in renderBackground
    private void drawKeys(GuiGraphics graphics, ProgressPayload progress) {
        int y = keysY();
        graphics.drawString(font, Text.tr("skills.keys"), left(), y + 6, UiTheme.MUTED);
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
        Component hint = selected != null ? Text.tr("skills.key_hint_selected", selected.displayName()) : Text.tr("skills.key_hint");
        graphics.drawString(font, hint, left(), y + KEY_SIZE + 5, selected != null ? 0xFFD700 : UiTheme.DIM);
    }

    private int pickX() {
        return (width - PICK_WIDTH) / 2;
    }

    private int pickY() {
        return (height - pickHeight()) / 2;
    }

    private int pickHeight() {
        return 24 + choices.size() * PICK_ROW + 4;
    }

    private int optionY(int index) {
        return pickY() + 22 + index * PICK_ROW;
    }

    private boolean onOption(int index, double mouseX, double mouseY) {
        int y = optionY(index);
        return mouseX >= pickX() + 6 && mouseX < pickX() + PICK_WIDTH - 6 && mouseY >= y && mouseY < y + PICK_ROW - 4;
    }

    // The window over the screen with each variant or keystone: icon, name, what it does, and whether it can be picked
    private void drawPicker(GuiGraphics graphics, ProgressPayload progress, PlayerClass playerClass, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(0, 0, width, height, 0xA0000000);
        int x = pickX();
        int y = pickY();
        UiTheme.panel(graphics, x, y, PICK_WIDTH, pickHeight());
        Component title = owner != null ? Text.tr("skills.pick_variant", owner.displayName()) : Text.tr("skills.pick_keystone");
        graphics.drawCenteredString(font, title, x + PICK_WIDTH / 2, y + 8, 0xFFD700);
        for (int i = 0; i < choices.size(); i++) {
            Upgrade choice = choices.get(i);
            int optionY = optionY(i);
            boolean picked = progress.rank(choice) > 0;
            boolean canPick = SkillTree.canRaise(playerClass, choice, progress.points(), progress::rank);
            int color = picked ? 0xFFD700 : canPick && onOption(i, mouseX, mouseY) ? playerClass.color : 0;
            UiTheme.box(graphics, x + 6, optionY, PICK_WIDTH - 12, PICK_ROW - 4, color, picked || canPick);
            graphics.renderItem(icon(choice), x + 10, optionY + 4);
            graphics.drawString(font, choice.displayName(), x + 30, optionY + 4, picked ? 0xFFD700 : UiTheme.TEXT);
            List<FormattedCharSequence> lines = font.split(choice.description(), PICK_WIDTH - 40);
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                graphics.drawString(font, lines.get(line), x + 30, optionY + 14 + line * 9, UiTheme.MUTED);
            }
            graphics.drawString(font, status(progress, playerClass, choice), x + 30, optionY + 31, UiTheme.TEXT);
        }
        graphics.pose().popPose();
    }

    // Whether the variant or keystone is picked, can be picked, or what it needs first
    private static Component status(ProgressPayload progress, PlayerClass playerClass, Upgrade choice) {
        if (progress.rank(choice) > 0) {
            return Text.tr("skills.picked").withStyle(ChatFormatting.GREEN);
        }
        Component requirement = requirement(progress, playerClass, choice);
        if (requirement != null) {
            return requirement;
        }
        if (SkillTree.picked(choice, progress::rank) != null) {
            return Text.tr("skills.switch_free").withStyle(ChatFormatting.YELLOW);
        }
        if (progress.points() <= 0) {
            return Text.tr("skills.no_points").withStyle(ChatFormatting.RED);
        }
        return Text.tr("skills.pick_one_point").withStyle(ChatFormatting.YELLOW);
    }

    // What it takes to open the upgrade, in words, or null if it is open
    private static Component requirement(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade) {
        if (SkillTree.isOpen(playerClass, upgrade, progress::rank)) {
            return null;
        }
        if (SkillTree.spent(playerClass, progress::rank) < SkillTree.pointsNeeded(upgrade)) {
            return Text.tr("skills.needs_points", SkillTree.pointsNeeded(upgrade)).withStyle(ChatFormatting.RED);
        }
        if (upgrade.kind == Upgrade.Kind.VARIANT) {
            return Text.tr("skills.needs_rank", Upgrade.VARIANT_RANK, upgrade.parent.displayName()).withStyle(ChatFormatting.RED);
        }
        return Text.tr("skills.needs_parent", upgrade.parent.displayName()).withStyle(ChatFormatting.RED);
    }

    private List<Component> tooltip(ProgressPayload progress, PlayerClass playerClass, Upgrade upgrade) {
        List<Component> lines = new ArrayList<>();
        lines.add(upgrade.displayName().withStyle(ChatFormatting.GOLD));
        lines.add(Text.tr(upgrade.isPassive() ? "skills.passive" : "skills.skill").withStyle(ChatFormatting.DARK_GRAY));
        lines.add(upgrade.description().withStyle(ChatFormatting.GRAY));
        if (upgrade.skill != null) {
            lines.add(Text.tr("skills.high_ranks", Upgrade.UTILITY_MAX_RANK + 1, upgrade.maxRank(), Upgrade.UTILITY_MAX_RANK,
                    Math.round(Upgrade.COOLDOWN_PER_RANK * 100)).withStyle(ChatFormatting.DARK_AQUA));
        }
        List<Upgrade> variants = upgrade.variants();
        if (!variants.isEmpty()) {
            Upgrade picked = variants.stream().filter(variant -> progress.rank(variant) > 0).findFirst().orElse(null);
            lines.add(picked != null ? Text.tr("skills.variant", picked.displayName()).withStyle(ChatFormatting.AQUA)
                    : Text.tr("skills.variant_at", Upgrade.VARIANT_RANK).withStyle(ChatFormatting.DARK_AQUA));
        }
        Component requirement = requirement(progress, playerClass, upgrade);
        if (requirement != null) {
            lines.add(requirement);
        } else if (upgrade.skill != null && progress.rank(upgrade) == 0) {
            lines.add(Text.tr("skills.first_point").withStyle(ChatFormatting.YELLOW));
        } else if (upgrade.skill != null) {
            lines.add(Text.tr("skills.click_to_bind").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private List<Component> keystoneTooltip(ProgressPayload progress, PlayerClass playerClass, Box box) {
        List<Component> lines = new ArrayList<>();
        lines.add(Text.tr("skills.keystone").withStyle(ChatFormatting.GOLD));
        lines.add(Text.tr("skills.keystone_info").withStyle(ChatFormatting.GRAY));
        for (Upgrade keystone : Upgrade.keystones(playerClass)) {
            lines.add(Component.literal("- ").append(keystone.displayName())
                    .withStyle(progress.rank(keystone) > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_AQUA));
        }
        Component requirement = requirement(progress, playerClass, Upgrade.keystones(playerClass).get(0));
        lines.add(requirement != null ? requirement : Text.tr("skills.click_to_choose").withStyle(ChatFormatting.YELLOW));
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

    // Skills use their skill bar icon, everything else gets one of its own
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
            case FIREBALL_BURN -> Items.BLAZE_POWDER;
            case FIREBALL_SPLINTER -> Items.FIREWORK_STAR;
            case FROST_NOVA_DEEP_FREEZE -> Items.SNOWBALL;
            case FROST_NOVA_ICE_MANTLE -> Items.PRISMARINE_CRYSTALS;
            case CHAIN_LIGHTNING_OVERCHARGE -> Items.GLOWSTONE_DUST;
            case CHAIN_LIGHTNING_FORK -> Items.AMETHYST_SHARD;
            case BLINK_ARCANE_BLAST -> Items.DRAGON_BREATH;
            case BLINK_PHASE_SHIFT -> Items.PHANTOM_MEMBRANE;
            case METEOR_IMPACT -> Items.ANVIL;
            case METEOR_MOLTEN_GROUND -> Items.LAVA_BUCKET;
            case ICE_ARMOR_ICICLES -> Items.POINTED_DRIPSTONE;
            case ICE_ARMOR_THICK -> Items.ICE;
            case THUNDERSTORM_ROAMING -> Items.FEATHER;
            case THUNDERSTORM_CHAIN -> Items.CHAIN;
            case UNSTABLE_MAGIC -> Items.TNT;
            case ARCANE_ABUNDANCE -> Items.EXPERIENCE_BOTTLE;
            case ARCANE_RHYTHM -> Items.NOTE_BLOCK;
            case MARKSMAN -> Items.SPECTRAL_ARROW;
            case AGILE -> Items.SUGAR;
            case PACK_LEADER -> Items.LEAD;
            case GRAVE_CHILL -> Items.SOUL_LANTERN;
            case DEATH_LORD -> Items.SKELETON_SKULL;
            case SOUL_HARVEST -> Items.SCULK;
            case BONE_SPEAR_PIERCE -> Items.ARROW;
            case BONE_SPEAR_MARROW -> Items.BONE_MEAL;
            case RAISE_SKELETONS_GIANTS -> Items.WITHER_SKELETON_SKULL;
            case RAISE_SKELETONS_HORDE -> Items.BONE;
            case CURSE_WEAKEN -> Items.FERMENTED_SPIDER_EYE;
            case CURSE_DECAY -> Items.ROTTEN_FLESH;
            case BONE_ARMOR_STORM -> Items.BONE_BLOCK;
            case BONE_ARMOR_FORTIFIED -> Items.IRON_CHESTPLATE;
            case BONE_PRISON_CRUSH -> Items.PISTON;
            case BONE_PRISON_LARGE -> Items.IRON_BARS;
            case ARMY_BLOOD_HARVEST -> Items.REDSTONE;
            case ARMY_RISEN -> Items.ZOMBIE_HEAD;
            case BLOOD_NOVA_THIRST -> Items.BEETROOT;
            case BLOOD_NOVA_CURSE -> Items.WITHER_ROSE;
            case DEATH_PACT -> Items.TOTEM_OF_UNDYING;
            case SACRIFICE -> Items.NETHERITE_SWORD;
            case BLOOD_PACT -> Items.NETHER_WART;
            default -> Items.BOOK;
        });
    }

    // Clicking an unlocked skill picks it, clicking a key puts the picked skill on it. While the pick window is open,
    // clicks only go to it, and a click outside closes it
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ProgressPayload progress = ClientProgress.get();
        PlayerClass playerClass = progress.chosenClass();
        if (choices != null) {
            boolean onWindow = mouseX >= pickX() && mouseX < pickX() + PICK_WIDTH && mouseY >= pickY() && mouseY < pickY() + pickHeight();
            if (!onWindow || playerClass == null) {
                choices = null;
                return true;
            }
            for (int i = 0; i < choices.size(); i++) {
                Upgrade choice = choices.get(i);
                if (onOption(i, mouseX, mouseY) && SkillTree.canRaise(playerClass, choice, progress.points(), progress::rank)) {
                    PacketDistributor.sendToServer(new UpgradePayload(choice));
                }
            }
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
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
        for (Box box : boxes(progress, playerClass)) {
            if (!inside(box, mouseX, mouseY)) {
                continue;
            }
            if (box.keystones) {
                openPicker(null, Upgrade.keystones(playerClass));
            } else {
                Upgrade upgrade = box.upgrade;
                selected = upgrade.skill != null && progress.rank(upgrade) > 0 && upgrade.skill != selected ? upgrade.skill : null;
            }
            return true;
        }
        selected = null;
        return false;
    }

    private void assign(int slot) {
        PacketDistributor.sendToServer(new AssignSlotPayload(slot, selected));
        selected = null;
    }

    // K closes the screen again, and 1-4 put the picked skill on that key. Escape closes the pick window first
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (choices != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            choices = null;
            return true;
        }
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
