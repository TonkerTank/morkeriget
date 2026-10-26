package com.morkeriget.bounty;

import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.journey.JourneyPayload;
import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Today's bounties, opened from the skill screen. One row for each bounty with a progress bar and a button for the
// reward, and the bonus for finishing them all at the bottom. Client only
public class BountyScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int ROW_HEIGHT = 34;
    private static final int BUTTON_WIDTH = 60;
    // What the server last sent
    private static BountiesPayload bounties = new BountiesPayload(List.of(), false, false);

    public BountyScreen() {
        super(Text.tr("bounties.title"));
    }

    // Opens the screen when the player asked for it, otherwise only updates it if it is open
    static void receive(BountiesPayload payload) {
        bounties = payload;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof BountyScreen screen) {
            screen.rebuildWidgets();
        } else if (payload.open()) {
            minecraft.setScreen(new BountyScreen());
        }
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return height / 2 - 80;
    }

    private int rowTop(int row) {
        return top() + 24 + row * ROW_HEIGHT;
    }

    @Override
    protected void init() {
        List<Bounty> list = bounties.bounties();
        for (int i = 0; i < list.size(); i++) {
            Bounty bounty = list.get(i);
            int index = i;
            Button button = Button.builder(Text.tr(bounty.claimed() ? "bounties.claimed" : "bounties.claim"),
                            pressed -> PacketDistributor.sendToServer(new BountyActionPayload(index)))
                    .bounds(left() + WIDTH - BUTTON_WIDTH, rowTop(i) + 4, BUTTON_WIDTH, 20)
                    .build(ThemedButton::new);
            button.active = bounty.done() && !bounty.claimed();
            addRenderableWidget(button);
        }
        int bottom = rowTop(BountyBoard.COUNT);
        Button bonus = Button.builder(Text.tr(bounties.bonusClaimed() ? "bounties.claimed" : "bounties.claim_bonus"),
                        pressed -> PacketDistributor.sendToServer(new BountyActionPayload(BountyActionPayload.BONUS)))
                .bounds(left() + WIDTH - BUTTON_WIDTH, bottom + 4, BUTTON_WIDTH, 20)
                .build(ThemedButton::new);
        bonus.active = bounties.allClaimed() && !bounties.bonusClaimed();
        addRenderableWidget(bonus);
        addRenderableWidget(Button.builder(Text.tr("button.back"), pressed -> minecraft.setScreen(new SkillScreen()))
                .bounds(width / 2 - 84, bottom + 40, 80, 18)
                .build(ThemedButton::new));
        addRenderableWidget(Button.builder(Text.tr("journey.title"), pressed -> PacketDistributor.sendToServer(new JourneyPayload.Open()))
                .bounds(width / 2 + 4, bottom + 40, 80, 18)
                .build(ThemedButton::new));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Down to just below "Back" and "Season journey"
        int bottom = rowTop(BountyBoard.COUNT) + 58 + 8;
        UiTheme.panel(graphics, left() - 10, top() - 10, WIDTH + 20, bottom - (top() - 10));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        UiTheme.title(graphics, font, Text.tr("bounties.heading").getString(), width / 2, top());
        graphics.drawCenteredString(font, Text.tr("bounties.new_at_midnight"), width / 2, top() + 11, UiTheme.MUTED);
        List<Bounty> list = bounties.bounties();
        int barWidth = WIDTH - BUTTON_WIDTH - 8;
        for (int i = 0; i < list.size(); i++) {
            Bounty bounty = list.get(i);
            int x = left();
            int y = rowTop(i);
            int color = bounty.claimed() ? UiTheme.MUTED : bounty.done() ? UiTheme.GOOD : UiTheme.TEXT;
            graphics.drawString(font, bounty.type().text(bounty.goal()), x, y + 2, color);
            // Progress bar with the numbers on the right
            float fraction = (float) bounty.progress() / Math.max(1, bounty.goal());
            UiTheme.bar(graphics, x, y + 14, barWidth, 4, fraction, bounty.done() ? 0x55CC55 : 0xCC9933);
            String count = bounty.progress() + "/" + bounty.goal();
            graphics.drawString(font, count, x + barWidth - font.width(count), y + 21, UiTheme.MUTED);
            graphics.drawString(font, Text.tr("bounties.reward"), x, y + 21, UiTheme.DIM);
        }
        int bottom = rowTop(BountyBoard.COUNT);
        UiTheme.divider(graphics, left(), bottom - 2, WIDTH);
        graphics.drawString(font, Text.tr("bounties.all_three"), left(), bottom + 4, bounties.allClaimed() ? UiTheme.GOOD : 0xFFAA00);
        graphics.drawString(font, Text.tr("bounties.bonus_reward"), left(), bottom + 15, UiTheme.DIM);
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
