package com.morkeriget.progress;

import com.morkeriget.skill.SkillKeys;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// The screen opened with K: level, XP and a + button for each upgrade. Client only
public class SkillScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int ROW_HEIGHT = 28;

    public SkillScreen() {
        super(Component.literal("Skills og level"));
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return height / 2 - 100;
    }

    @Override
    protected void init() {
        ProgressPayload progress = ClientProgress.get();
        for (Upgrade upgrade : Upgrade.values()) {
            int y = top() + 56 + upgrade.ordinal() * ROW_HEIGHT;
            Button button = Button.builder(Component.literal("+"), pressed -> PacketDistributor.sendToServer(new UpgradePayload(upgrade)))
                    .bounds(left() + WIDTH - 20, y + 2, 20, 20)
                    .build();
            button.active = progress.points() > 0 && progress.rank(upgrade) < Upgrade.MAX_RANK;
            addRenderableWidget(button);
        }
    }

    // Called when the server sends new values, e.g. right after clicking +
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ProgressPayload progress = ClientProgress.get();
        int left = left();
        int top = top();
        int center = width / 2;

        graphics.drawCenteredString(font, "Level " + progress.level(), center, top, 0xFFD700);

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

        for (Upgrade upgrade : Upgrade.values()) {
            int y = top + 56 + upgrade.ordinal() * ROW_HEIGHT;
            int rank = progress.rank(upgrade);
            int nameColor = rank >= Upgrade.MAX_RANK ? 0xFFD700 : 0xFFFFFF;
            graphics.drawString(font, upgrade.displayName + "  " + rank + "/" + Upgrade.MAX_RANK, left, y + 3, nameColor);
            graphics.drawString(font, upgrade.description, left, y + 14, 0x888888);
        }
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
