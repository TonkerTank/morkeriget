package com.morkeriget.journey;

import java.util.Arrays;
import java.util.List;

import com.morkeriget.bounty.BountyScreen;
import com.morkeriget.skill.SkillKeys;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

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

    private static int tabWidth() {
        return (WIDTH - (Achievement.CHAPTERS - 1) * 4) / Achievement.CHAPTERS;
    }

    private int tabX(int number) {
        return left() + (number - 1) * (tabWidth() + 4);
    }

    @Override
    protected void init() {
        String[] numbers = {"I", "II", "III", "IV"};
        for (int i = 1; i <= Achievement.CHAPTERS; i++) {
            int number = i;
            Button tab = Button.builder(Component.literal("Kapitel " + numbers[i - 1]), pressed -> {
                        chapter = number;
                        rebuildWidgets();
                    })
                    .bounds(tabX(i), top() + 12, tabWidth(), 18)
                    .build(ThemedButton::new);
            tab.active = i != chapter;
            addRenderableWidget(tab);
        }
        addRenderableWidget(Button.builder(Component.literal("Tilbage"), pressed -> minecraft.setScreen(new BountyScreen()))
                .bounds(width / 2 - 40, rowTop(7) + 30, 80, 18)
                .build(ThemedButton::new));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Down to just below "Tilbage"
        int bottom = rowTop(7) + 48 + 6;
        UiTheme.panel(graphics, left() - 10, top() - 8, WIDTH + 20, bottom - (top() - 8));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        long total = Arrays.stream(Achievement.values()).filter(JourneyScreen::done).count();
        UiTheme.title(graphics, font, "Sæsonrejse - " + total + "/" + Achievement.values().length + " klaret", width / 2, top());
        // A gold line under the chapter shown, whose tab can't be clicked
        int tabX = tabX(chapter);
        graphics.fill(tabX + 2, top() + 31, tabX + tabWidth() - 2, top() + 32, UiTheme.TITLE);
        List<Achievement> list = shown();
        int barWidth = WIDTH - 60;
        for (int i = 0; i < list.size(); i++) {
            Achievement achievement = list.get(i);
            int x = left();
            int y = rowTop(i);
            boolean done = done(achievement);
            graphics.drawString(font, (done ? "✔ " : "") + achievement.text, x, y, done ? UiTheme.GOOD : UiTheme.TEXT);
            int value = Math.min(achievement.goal, journey.value(achievement.stat));
            float fraction = (float) value / achievement.goal;
            UiTheme.bar(graphics, x, y + 11, barWidth, 4, fraction, done ? 0x55CC55 : 0xCC9933);
            String count = value + "/" + achievement.goal;
            graphics.drawString(font, count, x + WIDTH - font.width(count), y + 9, UiTheme.MUTED);
        }
        int bottom = rowTop(7);
        UiTheme.divider(graphics, left(), bottom - 4, WIDTH);
        graphics.drawString(font, "Kapitel " + chapter + ": " + Achievement.CHAPTER_NAMES[chapter - 1], left(), bottom, 0xFFAA00);
        graphics.drawString(font, "Hvert mål giver " + Achievement.CHAPTER_REWARDS[chapter - 1], left(), bottom + 10, UiTheme.DIM);
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
