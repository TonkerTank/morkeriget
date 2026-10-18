package com.morkeriget.progress;

import com.morkeriget.skill.SkillKeys;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Spending paragon points, opened from the skill screen once you are level 100. The stats are in two columns, each
// with a + button. Shift-click spends 10 points at once. Client only
public class ParagonScreen extends Screen {
    private static final int COLUMN_WIDTH = 190;
    private static final int COLUMN_GAP = 20;
    private static final int ROW_HEIGHT = 30;
    private static final int ROWS = (Paragon.values().length + 1) / 2;

    public ParagonScreen() {
        super(Component.literal("Paragon"));
    }

    private int columnLeft(Paragon stat) {
        return (width - 2 * COLUMN_WIDTH - COLUMN_GAP) / 2 + (stat.ordinal() / ROWS) * (COLUMN_WIDTH + COLUMN_GAP);
    }

    private int top() {
        return height / 2 - 80;
    }

    private int rowTop(int row) {
        return top() + 36 + row * ROW_HEIGHT;
    }

    private int rowTop(Paragon stat) {
        return rowTop(stat.ordinal() % ROWS);
    }

    @Override
    protected void init() {
        ProgressPayload progress = ClientProgress.get();
        for (Paragon stat : Paragon.values()) {
            Button button = Button.builder(Component.literal("+"),
                            pressed -> PacketDistributor.sendToServer(new ParagonPayload(stat, hasShiftDown() ? 10 : 1)))
                    .bounds(columnLeft(stat) + COLUMN_WIDTH - 20, rowTop(stat) + 4, 20, 20)
                    .build();
            button.active = progress.paragonPoints() > 0 && progress.rank(stat) < stat.maxRank;
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Tilbage"), pressed -> minecraft.setScreen(new SkillScreen()))
                .bounds(width / 2 - 40, rowTop(ROWS) + 6, 80, 18)
                .build());
    }

    // Called when the server sends new values, e.g. right after clicking +
    public void refresh() {
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ProgressPayload progress = ClientProgress.get();
        int center = width / 2;
        graphics.drawCenteredString(font, "Paragon " + progress.paragonLevel(), center, top(), 0x55FFFF);
        int pointsColor = progress.paragonPoints() > 0 ? 0x55FF55 : 0xAAAAAA;
        graphics.drawCenteredString(font, "Paragon-points: " + progress.paragonPoints() + "  (shift-klik giver 10)", center, top() + 14, pointsColor);
        for (Paragon stat : Paragon.values()) {
            int x = columnLeft(stat);
            int y = rowTop(stat);
            int rank = progress.rank(stat);
            graphics.drawString(font, stat.displayName + "  " + rank + "/" + stat.maxRank, x, y + 4,
                    rank >= stat.maxRank ? 0xFFD700 : 0xFFFFFF);
            graphics.drawString(font, stat.description, x, y + 16, 0x888888);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
