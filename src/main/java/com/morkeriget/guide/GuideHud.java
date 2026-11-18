package com.morkeriget.guide;

import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

// The guide's box in the top left: which step you are on and what to do. Hidden when the guide is over. Client only
public final class GuideHud {
    private static final int X = 6;
    private static final int Y = 6;
    private static final int WIDTH = 150;
    private static final int PADDING = 5;

    private static GuidePayload state = new GuidePayload(Guide.DONE, 0);

    private GuideHud() {
    }

    public static void set(GuidePayload payload) {
        state = payload;
    }

    public static void reset() {
        state = new GuidePayload(Guide.DONE, 0);
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "guide"), GuideHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        GuideStep[] steps = GuideStep.values();
        if (state.step() < 0 || state.step() >= steps.length || minecraft.options.hideGui || minecraft.player == null
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        GuideStep step = steps[state.step()];
        Font font = minecraft.font;
        MutableComponent text = step.text();
        if (step.goal > 1) {
            text.append(" ").append(Text.tr("guide.count", state.count(), step.goal));
        }
        List<FormattedCharSequence> lines = font.split(text, WIDTH - 2 * PADDING);
        int height = PADDING + font.lineHeight + 3 + lines.size() * (font.lineHeight + 1) + PADDING - 1;
        UiTheme.glassPanel(graphics, X, Y, WIDTH, height);
        graphics.drawString(font, Text.tr("guide.title", state.step() + 1, steps.length), X + PADDING, Y + PADDING, UiTheme.TITLE);
        int y = Y + PADDING + font.lineHeight + 3;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, X + PADDING, y, UiTheme.TEXT);
            y += font.lineHeight + 1;
        }
    }
}
