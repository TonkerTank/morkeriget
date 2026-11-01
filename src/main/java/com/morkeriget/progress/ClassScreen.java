package com.morkeriget.progress;

import com.morkeriget.Text;
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
    // The widest a panel gets. On a small screen they get narrower, so all five fit on the smallest normal one (427 wide)
    private static final int MAX_PANEL_WIDTH = 104;
    // Room for seven skills and the bonus, which can take three lines in a narrow panel
    private static final int PANEL_HEIGHT = 210;
    // The room the description takes under the name
    private static final int DESCRIPTION_HEIGHT = 28;
    private static final int GAP = 2;

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

    // On a low screen the line about each class goes, so the boxes are shorter
    private boolean compact() {
        return height < 30 + PANEL_HEIGHT + 34;
    }

    private int panelHeight() {
        return compact() ? PANEL_HEIGHT - DESCRIPTION_HEIGHT : PANEL_HEIGHT;
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
            graphics.drawCenteredString(font, playerClass.displayName(), x + panelWidth() / 2, top + 6, playerClass.color);
            int y = top + 18;
            if (!compact()) {
                graphics.drawWordWrap(font, playerClass.description(), x + 5, y, panelWidth() - 10, UiTheme.MUTED);
                y += DESCRIPTION_HEIGHT;
            }
            for (Skill skill : playerClass.skills) {
                graphics.renderItem(SkillBar.icon(skill), x + 3, y);
                // Cut short if the panel is narrow
                graphics.drawString(font, font.plainSubstrByWidth(skill.displayName().getString(), panelWidth() - 23), x + 21, y + 4, UiTheme.TEXT);
                y += 15;
            }
            graphics.drawWordWrap(font, playerClass.bonus(), x + 5, y + 2, panelWidth() - 10, playerClass.color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
