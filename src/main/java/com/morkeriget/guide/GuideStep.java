package com.morkeriget.guide;

import java.util.Locale;

import com.morkeriget.Text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

// The steps of the guide for new players, in order. goal is how many times it has to happen, e.g. 5 kills
public enum GuideStep {
    CHOOSE_CLASS(1),
    PICK_SKILL(1),
    USE_SKILL(1),
    KILL(5),
    LOOT(1),
    SALVAGE(1);

    public final int goal;

    GuideStep(int goal) {
        this.goal = goal;
    }

    // What to do, shown in the box in the top left. The keys are shown as the player has set them
    public MutableComponent text() {
        Component skills = Component.keybind("key.morkeriget.skills");
        Component firstSkill = Component.keybind("key.morkeriget.skill_1");
        String key = "guide.step." + name().toLowerCase(Locale.ROOT);
        return switch (this) {
            case CHOOSE_CLASS, SALVAGE -> Text.tr(key, skills);
            case PICK_SKILL -> Text.tr(key, skills, firstSkill);
            case USE_SKILL -> Text.tr(key, firstSkill);
            case LOOT -> Text.tr(key, Component.keybind("key.morkeriget.loot_bag"));
            default -> Text.tr(key);
        };
    }
}
