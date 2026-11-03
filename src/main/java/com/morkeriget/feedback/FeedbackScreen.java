package com.morkeriget.feedback;

import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

// The window that asks for feedback the first time you join a world, with a button to the GitHub issues. Client only
public class FeedbackScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int TEXT_WIDTH = WIDTH - 24;

    private List<FormattedCharSequence> lines = List.of();

    public FeedbackScreen() {
        super(Text.tr("feedback.title"));
    }

    private int panelHeight() {
        return 30 + lines.size() * (font.lineHeight + 1) + 40;
    }

    private int top() {
        return Math.max(8, (height - panelHeight()) / 2);
    }

    @Override
    protected void init() {
        lines = font.split(Text.tr("feedback.text"), TEXT_WIDTH);
        int buttonY = top() + panelHeight() - 28;
        addRenderableWidget(Button.builder(Text.tr("feedback.give"), pressed -> ConfirmLinkScreen.confirmLinkNow(this, Feedback.ISSUES_URL))
                .bounds(width / 2 - 104, buttonY, 100, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("feedback.close"), pressed -> onClose())
                .bounds(width / 2 + 4, buttonY, 100, 18)
                .build(ThemedButton::new));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2;
        int top = top();
        UiTheme.panel(graphics, left, top, WIDTH, panelHeight());
        UiTheme.title(graphics, font, title, width / 2, top + 10, UiTheme.TITLE, 30);
        int y = top + 28;
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, width / 2, y, UiTheme.TEXT);
            y += font.lineHeight + 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
