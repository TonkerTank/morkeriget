package com.morkeriget.bounty;

import java.util.List;

import com.morkeriget.progress.SkillScreen;
import com.morkeriget.skill.SkillKeys;

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
        super(Component.literal("Opgaver"));
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
            Button button = Button.builder(Component.literal(bounty.claimed() ? "Hentet" : "Hent"),
                            pressed -> PacketDistributor.sendToServer(new BountyActionPayload(index)))
                    .bounds(left() + WIDTH - BUTTON_WIDTH, rowTop(i) + 4, BUTTON_WIDTH, 20)
                    .build();
            button.active = bounty.done() && !bounty.claimed();
            addRenderableWidget(button);
        }
        int bottom = rowTop(BountyBoard.COUNT);
        Button bonus = Button.builder(Component.literal(bounties.bonusClaimed() ? "Hentet" : "Hent bonus"),
                        pressed -> PacketDistributor.sendToServer(new BountyActionPayload(BountyActionPayload.BONUS)))
                .bounds(left() + WIDTH - BUTTON_WIDTH, bottom + 4, BUTTON_WIDTH, 20)
                .build();
        bonus.active = bounties.allClaimed() && !bounties.bonusClaimed();
        addRenderableWidget(bonus);
        addRenderableWidget(Button.builder(Component.literal("Tilbage"), pressed -> minecraft.setScreen(new SkillScreen()))
                .bounds(width / 2 - 40, bottom + 40, 80, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Dagens opgaver", width / 2, top(), 0xFFD700);
        graphics.drawCenteredString(font, "Nye opgaver ved midnat", width / 2, top() + 11, 0x888888);
        List<Bounty> list = bounties.bounties();
        int barWidth = WIDTH - BUTTON_WIDTH - 8;
        for (int i = 0; i < list.size(); i++) {
            Bounty bounty = list.get(i);
            int x = left();
            int y = rowTop(i);
            int color = bounty.claimed() ? 0x888888 : bounty.done() ? 0x55FF55 : 0xFFFFFF;
            graphics.drawString(font, bounty.type().text(bounty.goal()), x, y + 2, color);
            // Progress bar with the numbers on the right
            float fraction = (float) bounty.progress() / Math.max(1, bounty.goal());
            graphics.fill(x - 1, y + 13, x + barWidth + 1, y + 19, 0xFF000000);
            graphics.fill(x, y + 14, x + barWidth, y + 18, 0xFF2A2A2A);
            graphics.fill(x, y + 14, x + Math.round(barWidth * fraction), y + 18, bounty.done() ? 0xFF55CC55 : 0xFFCC9933);
            String count = bounty.progress() + "/" + bounty.goal();
            graphics.drawString(font, count, x + barWidth - font.width(count), y + 21, 0xAAAAAA);
            graphics.drawString(font, "Essens, sjælden+ genstand og XP", x, y + 21, 0x777777);
        }
        int bottom = rowTop(BountyBoard.COUNT);
        graphics.drawString(font, "Klar alle 3:", left(), bottom + 4, bounties.allClaimed() ? 0x55FF55 : 0xFFAA00);
        graphics.drawString(font, "Legendarisk genstand, essens og ædelsten", left(), bottom + 15, 0x777777);
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
