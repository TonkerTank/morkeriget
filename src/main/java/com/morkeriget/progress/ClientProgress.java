package com.morkeriget.progress;

import java.util.List;

import net.minecraft.client.Minecraft;

// The level and upgrades the server last told this player about. Client only
public final class ClientProgress {
    private static ProgressPayload progress = new ProgressPayload(1, 0, PlayerProgress.xpForNextLevel(1), 0, List.of());

    private ClientProgress() {
    }

    public static ProgressPayload get() {
        return progress;
    }

    public static void set(ProgressPayload payload) {
        progress = payload;
        if (Minecraft.getInstance().screen instanceof SkillScreen screen) {
            screen.refresh();
        }
    }
}
