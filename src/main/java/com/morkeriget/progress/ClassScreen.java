package com.morkeriget.progress;

import com.morkeriget.Text;
import com.morkeriget.skill.Skill;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.ui.ThemedButton;
import com.morkeriget.ui.UiTheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// Picking a class: one panel for each with its skills and bonus, and a button to pick it. Opened with K before a class
// is picked, or with "Skift klasse" in the skill screen. Client only
public class ClassScreen extends Screen {
    // The widest a panel gets. On a small screen they get narrower, so all five fit on the smallest normal one (427 wide)
    private static final int MAX_PANEL_WIDTH = 124;
    private static final int GAP = 2;
    private static final int SKILL_ROW = 15;
    // The name at the top of a panel, and the pick button at the bottom
    private static final int NAME_HEIGHT = 18;
    private static final int BUTTON_HEIGHT = 24;

    public ClassScreen() {
        super(Text.tr("class.title"));
    }

    private int panelWidth() {
        int count = PlayerClass.values().length;
        return Math.min(MAX_PANEL_WIDTH, (width - 8 - (count - 1) * GAP) / count);
    }

    private int panelLeft(PlayerClass playerClass) {
        int total = PlayerClass.values().length * panelWidth() + (PlayerClass.values().length - 1) * GAP;
        return (width - total) / 2 + playerClass.ordinal() * (panelWidth() + GAP);
    }

    // The panel goes from 30 above this, for the title, to 32 below the classes, for "Tilbage". Kept on the screen
    private int top() {
        return Math.max(32, height / 2 - panelHeight() / 2);
    }

    // The most lines a class's description or bonus takes in a panel this wide, so every panel has room for the longest
    private int descriptionLines() {
        int lines = 0;
        for (PlayerClass playerClass : PlayerClass.values()) {
            lines = Math.max(lines, font.split(playerClass.description(), panelWidth() - 10).size());
        }
        return lines;
    }

    private int bonusLines() {
        int lines = 0;
        for (PlayerClass playerClass : PlayerClass.values()) {
            lines = Math.max(lines, font.split(playerClass.bonus(), panelWidth() - 10).size());
        }
        return lines;
    }

    private int descriptionHeight() {
        return descriptionLines() * font.lineHeight + 4;
    }

    private int fullHeight() {
        int skills = 0;
        for (PlayerClass playerClass : PlayerClass.values()) {
            skills = Math.max(skills, playerClass.skills.size());
        }
        return NAME_HEIGHT + descriptionHeight() + skills * SKILL_ROW + bonusLines() * font.lineHeight + 6 + BUTTON_HEIGHT;
    }

    // On a low screen the line about each class goes, so the boxes are shorter
    private boolean compact() {
        return height < 30 + fullHeight() + 34;
    }

    private int panelHeight() {
        return compact() ? fullHeight() - descriptionHeight() : fullHeight();
    }

    @Override
    protected void init() {
        PlayerClass current = ClientProgress.get().chosenClass();
        for (PlayerClass playerClass : PlayerClass.values()) {
            Component text = Text.tr(playerClass == current ? "class.reset" : "class.pick");
            addRenderableWidget(Button.builder(text, pressed -> PacketDistributor.sendToServer(new ChooseClassPayload(playerClass)))
                    .bounds(panelLeft(playerClass) + 6, top() + panelHeight() - 24, panelWidth() - 12, 18)
                    .build(ThemedButton::new));
        }
        if (current != null) {
            addRenderableWidget(Button.builder(Text.tr("button.back"), pressed -> minecraft.setScreen(new SkillScreen()))
                    .bounds(width / 2 - 40, top() + panelHeight() + 8, 80, 18)
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
        int total = count * panelWidth() + (count - 1) * GAP;
        // The classes nearly fill a small screen, so the padding shrinks to keep the frame on it
        int pad = Math.max(0, Math.min(8, (width - total) / 2 - 3));
        // Room for "Tilbage" under the classes once you have one
        int bottom = top + panelHeight() + (current != null ? 32 : 6);
        UiTheme.panel(graphics, (width - total) / 2 - pad, top - 30, total + 2 * pad, bottom - (top - 30));
        for (PlayerClass playerClass : PlayerClass.values()) {
            int x = panelLeft(playerClass);
            boolean hovered = mouseX >= x && mouseX < x + panelWidth() && mouseY >= top && mouseY < top + panelHeight();
            UiTheme.box(graphics, x, top, panelWidth(), panelHeight(), playerClass.color, playerClass == current || hovered);
            // A thin bronze line under the name
            UiTheme.divider(graphics, x + 6, top + 15, panelWidth() - 12);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        UiTheme.title(graphics, font, Text.tr("class.heading"), width / 2, top - 24);
        Component note = Text.tr(ClientProgress.get().chosenClass() == null ? "class.note_first" : "class.note_change");
        graphics.drawCenteredString(font, note, width / 2, top - 12, UiTheme.MUTED);

        for (PlayerClass playerClass : PlayerClass.values()) {
            int x = panelLeft(playerClass);
            graphics.drawCenteredString(font, playerClass.displayName(), x + panelWidth() / 2, top + 5, playerClass.color);
            int y = top + NAME_HEIGHT + 2;
            if (!compact()) {
                graphics.drawWordWrap(font, playerClass.description(), x + 5, y, panelWidth() - 10, UiTheme.MUTED);
                y += descriptionHeight();
            }
            for (Skill skill : playerClass.skills) {
                graphics.renderItem(SkillBar.icon(skill), x + 3, y);
                // A name too long for a narrow panel slides slowly back and forth
                AbstractWidget.renderScrollingString(graphics, font, skill.displayName(), x + 21, x + 21, y + 2, x + panelWidth() - 3, y + 15, UiTheme.TEXT);
                y += SKILL_ROW;
            }
            graphics.drawWordWrap(font, playerClass.bonus(), x + 5, y + 2, panelWidth() - 10, playerClass.color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
