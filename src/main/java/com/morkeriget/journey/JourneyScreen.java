package com.morkeriget.journey;

import java.util.Arrays;
import java.util.List;

import com.morkeriget.bounty.BountyScreen;
import com.morkeriget.skill.SkillKeys;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// The season journey, opened from the bounties. One chapter at a time, picked with the buttons at the top, with a
// progress bar for each goal. Client only
public class JourneyScreen extends Screen {
    private static final int WIDTH = 260;
    private static final int ROW_HEIGHT = 22;
    // What the server last sent
    private static JourneyPayload journey = new JourneyPayload(List.of(), false);
    // The chapter shown, 1 to Achievement.CHAPTERS. Kept while the game runs, so it opens where you left it
    private static int chapter = 1;

    public JourneyScreen() {
        super(Component.literal("Sæsonrejse"));
    }

    // Opens the screen when the player asked for it, otherwise only updates it if it is open
    static void receive(JourneyPayload payload) {
        journey = payload;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof JourneyScreen screen) {
            screen.rebuildWidgets();
        } else if (payload.open()) {
            minecraft.setScreen(new JourneyScreen());
        }
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return height / 2 - 110;
    }

    private int rowTop(int row) {
        return top() + 40 + row * ROW_HEIGHT;
    }

    private static List<Achievement> shown() {
        return Arrays.stream(Achievement.values()).filter(achievement -> achievement.chapter == chapter).toList();
    }

    private static boolean done(Achievement achievement) {
        return journey.value(achievement.stat) >= achievement.goal;
    }

    @Override
    protected void init() {
        int tabWidth = (WIDTH - (Achievement.CHAPTERS - 1) * 4) / Achievement.CHAPTERS;
        String[] numbers = {"I", "II", "III", "IV"};
        for (int i = 1; i <= Achievement.CHAPTERS; i++) {
            int number = i;
            Button tab = Button.builder(Component.literal("Kapitel " + numbers[i - 1]), pressed -> {
                        chapter = number;
                        rebuildWidgets();
                    })
                    .bounds(left() + (i - 1) * (tabWidth + 4), top() + 12, tabWidth, 18)
                    .build();
            tab.active = i != chapter;
            addRenderableWidget(tab);
        }
        addRenderableWidget(Button.builder(Component.literal("Tilbage"), pressed -> minecraft.setScreen(new BountyScreen()))
                .bounds(width / 2 - 40, rowTop(7) + 30, 80, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        long total = Arrays.stream(Achievement.values()).filter(JourneyScreen::done).count();
        graphics.drawCenteredString(font, "Sæsonrejse - " + total + "/" + Achievement.values().length + " klaret", width / 2, top(), 0xFFD700);
        List<Achievement> list = shown();
        int barWidth = WIDTH - 60;
        for (int i = 0; i < list.size(); i++) {
            Achievement achievement = list.get(i);
            int x = left();
            int y = rowTop(i);
            boolean done = done(achievement);
            graphics.drawString(font, (done ? "✔ " : "") + achievement.text, x, y, done ? 0x55FF55 : 0xFFFFFF);
            int value = Math.min(achievement.goal, journey.value(achievement.stat));
            float fraction = (float) value / achievement.goal;
            graphics.fill(x - 1, y + 10, x + barWidth + 1, y + 16, 0xFF000000);
            graphics.fill(x, y + 11, x + barWidth, y + 15, 0xFF2A2A2A);
            graphics.fill(x, y + 11, x + Math.round(barWidth * fraction), y + 15, done ? 0xFF55CC55 : 0xFFCC9933);
            String count = value + "/" + achievement.goal;
            graphics.drawString(font, count, x + WIDTH - font.width(count), y + 9, 0xAAAAAA);
        }
        int bottom = rowTop(7);
        graphics.drawString(font, "Kapitel " + chapter + ": " + Achievement.CHAPTER_NAMES[chapter - 1], left(), bottom, 0xFFAA00);
        graphics.drawString(font, "Hvert mål giver " + Achievement.CHAPTER_REWARDS[chapter - 1], left(), bottom + 10, 0x777777);
        graphics.drawString(font, "Klar dem alle for en mytisk genstand", left(), bottom + 20, 0xCC55FF);
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
