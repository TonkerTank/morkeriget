package com.morkeriget.progress;

import com.morkeriget.skill.Skill;
import com.morkeriget.skill.SkillBar;

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
    private static final int PANEL_HEIGHT = 160;
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
                    .build());
        }
        if (current != null) {
            addRenderableWidget(Button.builder(Component.literal("Tilbage"), pressed -> minecraft.setScreen(new SkillScreen()))
                    .bounds(width / 2 - 40, top() + PANEL_HEIGHT + 8, 80, 18)
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = top();
        graphics.drawCenteredString(font, "Vælg din klasse", width / 2, top - 24, 0xFFD700);
        String note = ClientProgress.get().chosenClass() == null
                ? "Du kan altid skifte senere i K"
                : "Skifter du klasse, får du alle dine skill-points tilbage";
        graphics.drawCenteredString(font, note, width / 2, top - 12, 0xAAAAAA);

        for (PlayerClass playerClass : PlayerClass.values()) {
            int x = panelLeft(playerClass);
            graphics.fill(x, top, x + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF000000 | (playerClass.color & 0x3F3F3F));
            graphics.fill(x + 1, top + 1, x + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, 0xE0181820);
            graphics.drawCenteredString(font, playerClass.displayName, x + PANEL_WIDTH / 2, top + 6, playerClass.color);
            graphics.drawWordWrap(font, Component.literal(playerClass.description), x + 5, top + 18, PANEL_WIDTH - 10, 0xBBBBBB);

            int y = top + 46;
            for (Skill skill : playerClass.skills) {
                graphics.renderItem(SkillBar.icon(skill), x + 3, y);
                graphics.drawString(font, skill.displayName, x + 21, y + 4, 0xFFFFFF);
                y += 17;
            }
            graphics.drawWordWrap(font, Component.literal(playerClass.bonus), x + 5, y + 2, PANEL_WIDTH - 10, playerClass.color);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
