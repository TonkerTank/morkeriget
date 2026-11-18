package com.morkeriget.ui;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.Text;
import com.morkeriget.feedback.FeedbackScreen;
import com.morkeriget.progress.ClassScreen;
import com.morkeriget.progress.ClientProgress;
import com.morkeriget.progress.SkillScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ScreenEvent;

// The mod's look on the pause menu (Esc), like the title screen: the dark red backdrop with embers, the gold logo in
// place of the "Game Menu" title and a see-through panel behind the buttons. Minecraft's "Give Feedback" and "Report
// Bugs" buttons go to Mojang, so they are swapped for the mod's character screen and its feedback window. Client only
public final class PauseMenu {
    private static final int LOGO_HEIGHT = 40;

    private PauseMenu() {
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen) || !screen.showsPauseMenu()) {
            return;
        }
        List<GuiEventListener> remove = new ArrayList<>();
        List<Button> add = new ArrayList<>();
        for (GuiEventListener child : event.getListenersList()) {
            if (child instanceof StringWidget) {
                // The "Game Menu" title, where the logo goes
                remove.add(child);
            } else if (child instanceof Button button && isKey(button, "menu.sendFeedback")) {
                remove.add(child);
                add.add(swap(button, Text.tr("pause.character"), () -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(ClientProgress.get().chosenClass() == null ? new ClassScreen() : new SkillScreen());
                }));
            } else if (child instanceof Button button && isKey(button, "menu.reportBugs")) {
                remove.add(child);
                add.add(swap(button, Text.tr("pause.feedback"), () -> Minecraft.getInstance().setScreen(new FeedbackScreen())));
            }
        }
        remove.forEach(event::removeListener);
        add.forEach(event::addListener);
    }

    private static boolean isKey(Button button, String key) {
        return button.getMessage().getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key);
    }

    // A new button with the same place and size as the old one
    private static Button swap(Button old, Component message, Runnable action) {
        return Button.builder(message, pressed -> action.run())
                .bounds(old.getX(), old.getY(), old.getWidth(), old.getHeight())
                .build(ThemedButton::new);
    }

    // After Minecraft's blurred background and before the buttons, from PauseScreenMixin
    public static void background(PauseScreen screen, GuiGraphics graphics) {
        if (!screen.showsPauseMenu()) {
            return;
        }
        int width = screen.width;
        int height = screen.height;
        int buttonsTop = buttonsTop(screen);
        // The logo sits in the space above the buttons, but never above the top of the screen
        int logoTop = Math.max(4, buttonsTop - LOGO_HEIGHT - 12);
        int logoHeight = Math.min(LOGO_HEIGHT, buttonsTop - 12 - logoTop);
        TitleMenu.backdrop(graphics, width, height, logoTop + logoHeight / 2, 340, 120);
        TitleMenu.buttonPanel(graphics, screen.children());
        if (logoHeight >= 16) {
            TitleMenu.logoAt(graphics, width, logoTop, logoHeight, 1);
        }
    }

    private static int buttonsTop(Screen screen) {
        int top = screen.height / 4;
        boolean found = false;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                top = found ? Math.min(top, widget.getY()) : widget.getY();
                found = true;
            }
        }
        return top;
    }
}
