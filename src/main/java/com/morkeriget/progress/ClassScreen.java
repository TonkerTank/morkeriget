package com.morkeriget.progress;

import com.morkeriget.skill.Skill;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Picking a class: one panel for each with its skills and bonus, and a button to pick it. Opened with K before a class
// is picked, or with "Skift klasse" in the skill screen. Client only
public class ClassScreen extends Screen {
    // Four panels have to fit on the smallest normal screen, which is 427 wide
    private static final int PANEL_WIDTH = 104;
    // Room for seven skills and the bonus
    private static final int PANEL_HEIGHT = 200;
    private static final int GAP = 2;

    public ClassScreen() {
        super(Component.literal("Vælg klasse"));
    }

    private int panelLeft(PlayerClass playerClass) {
        int total = PlayerClass.values().length * PANEL_WIDTH + (PlayerClass.values().length - 1) * GAP;
        return (width - total) / 2 + playerClass.ordinal() * (PANEL_WIDTH + GAP);
    }

    private int top() {
        return height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        PlayerClass current = ClientProgress.get().chosenClass();
        for (PlayerClass playerClass : PlayerClass.values()) {
            String text = playerClass == current ? "Nulstil" : "Vælg";
            addRenderableWidget(Button.builder(Component.literal(text), pressed -> PacketDistributor.sendToServer(new ChooseClassPayload(playerClass)))
                    .bounds(panelLeft(playerClass) + 6, top() + PANEL_HEIGHT - 24, PANEL_WIDTH - 12, 18)
                    .build(ThemedButton::new));
        }
        if (current != null) {
            addRenderableWidget(Button.builder(Component.literal("Tilbage"), pressed -> minecraft.setScreen(new SkillScreen()))
                    .bounds(width / 2 - 40, top() + PANEL_HEIGHT + 8, 80, 18)
                    .build(ThemedButton::new));
        }
    }

    // The panel and a box for each class go behind the buttons. The class you have, or the one under the mouse, is lit
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int top = top();
        PlayerClass current = ClientProgress.get().chosenClass();
        int count = PlayerClass.values().length;
        int total = count * PANEL_WIDTH + (count - 1) * GAP;
        // The four classes nearly fill a small screen, so the padding shrinks to keep the frame on it
        int pad = Math.max(0, Math.min(8, (width - total) / 2 - 3));
        // Room for "Tilbage" under the classes once you have one
        int bottom = top + PANEL_HEIGHT + (current != null ? 32 : 6);
        UiTheme.panel(graphics, (width - total) / 2 - pad, top - 30, total + 2 * pad, bottom - (top - 30));
        for (PlayerClass playerClass : PlayerClass.values()) {
            int x = panelLeft(playerClass);
            boolean hovered = mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= top && mouseY < top + PANEL_HEIGHT;
            UiTheme.box(graphics, x, top, PANEL_WIDTH, PANEL_HEIGHT, playerClass.color, playerClass == current || hovered);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        UiTheme.title(graphics, font, "Vælg din klasse", width / 2, top - 24);
        String note = ClientProgress.get().chosenClass() == null
                ? "Du kan altid skifte senere i K"
                : "Skifter du klasse, får du alle dine skill-points tilbage";
        graphics.drawCenteredString(font, note, width / 2, top - 12, UiTheme.MUTED);

        for (PlayerClass playerClass : PlayerClass.values()) {
            int x = panelLeft(playerClass);
            graphics.drawCenteredString(font, playerClass.displayName, x + PANEL_WIDTH / 2, top + 6, playerClass.color);
            graphics.drawWordWrap(font, Component.literal(playerClass.description), x + 5, top + 18, PANEL_WIDTH - 10, UiTheme.MUTED);

            int y = top + 46;
            for (Skill skill : playerClass.skills) {
                graphics.renderItem(SkillBar.icon(skill), x + 3, y);
                graphics.drawString(font, skill.displayName, x + 21, y + 4, UiTheme.TEXT);
                y += 15;
            }
            graphics.drawWordWrap(font, Component.literal(playerClass.bonus), x + 5, y + 2, PANEL_WIDTH - 10, playerClass.color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
