package com.morkeriget.progress;

import java.util.List;

import com.morkeriget.jewelry.OpenJewelryPayload;
import com.morkeriget.merchant.OpenMerchantPayload;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.smithy.OpenSmithyPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// The screen opened with K: class, level, XP and a + button for each upgrade. The class's four skills are in the left
// column with their key, the upgrades every class has are in the right column. Client only
public class SkillScreen extends Screen {
    private static final int WIDTH = 440;
    private static final int ROW_HEIGHT = 28;
    private static final int COLUMN_WIDTH = 210;
    // The skill icon in front of the skill upgrades
    private static final int ICON_WIDTH = 20;
    private static final List<Upgrade> SHARED = List.of(Upgrade.VITALITY, Upgrade.SPIRIT, Upgrade.STRENGTH);

    public SkillScreen() {
        super(Component.literal("Skills og level"));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return height / 2 - 100;
    }

    private int rowTop(int row) {
        return top() + 56 + row * ROW_HEIGHT;
    }

    private static List<Upgrade> skillUpgrades(PlayerClass playerClass) {
        return playerClass.skills.stream()
                .map(skill -> List.of(Upgrade.values()).stream().filter(upgrade -> upgrade.skill == skill).findFirst().orElseThrow())
                .toList();
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
        List<Upgrade> skills = skillUpgrades(playerClass);
        for (int row = 0; row < skills.size(); row++) {
            plusButton(progress, skills.get(row), left(), rowTop(row));
        }
        for (int row = 0; row < SHARED.size(); row++) {
            plusButton(progress, SHARED.get(row), left() + WIDTH - COLUMN_WIDTH, rowTop(row));
        }
    }

    private void plusButton(ProgressPayload progress, Upgrade upgrade, int x, int y) {
        Button button = Button.builder(Component.literal("+"), pressed -> PacketDistributor.sendToServer(new UpgradePayload(upgrade)))
                .bounds(x + COLUMN_WIDTH - 20, y + 2, 20, 20)
                .build();
        button.active = progress.points() > 0 && progress.rank(upgrade) < Upgrade.MAX_RANK;
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
            graphics.drawCenteredString(font, "Vælg en klasse først", center, rowTop(0) + 8, 0xFFD700);
            return;
        }
        List<Upgrade> skills = skillUpgrades(playerClass);
        for (int row = 0; row < skills.size(); row++) {
            Upgrade upgrade = skills.get(row);
            int y = rowTop(row);
            graphics.renderItem(SkillBar.icon(upgrade.skill), left, y + 4);
            String key = SkillKeys.SKILL_SLOTS.get(row).getTranslatedKeyMessage().getString();
            drawUpgrade(graphics, progress, upgrade, left + ICON_WIDTH, y, " [" + key + "]");
        }
        for (int row = 0; row < SHARED.size(); row++) {
            drawUpgrade(graphics, progress, SHARED.get(row), left + WIDTH - COLUMN_WIDTH, rowTop(row), "");
        }
        graphics.drawString(font, "Klassebonus: " + playerClass.bonus, left + WIDTH - COLUMN_WIDTH, rowTop(SHARED.size()) + 8, playerClass.color);
    }

    private void drawUpgrade(GuiGraphics graphics, ProgressPayload progress, Upgrade upgrade, int x, int y, String suffix) {
        int rank = progress.rank(upgrade);
        int nameColor = rank >= Upgrade.MAX_RANK ? 0xFFD700 : 0xFFFFFF;
        graphics.drawString(font, upgrade.displayName + suffix + "  " + rank + "/" + Upgrade.MAX_RANK, x, y + 3, nameColor);
        graphics.drawString(font, upgrade.description, x, y + 14, 0x888888);
    }

    // K closes the screen again
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (SkillKeys.SKILLS.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
