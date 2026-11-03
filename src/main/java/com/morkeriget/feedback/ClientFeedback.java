package com.morkeriget.feedback;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// Opens the feedback window once the world has loaded. The packet comes while the loading screen is still up, which
// would close the window again, so it waits until no other screen is open. Client only
public final class ClientFeedback {
    private static boolean pending;

    private ClientFeedback() {
    }

    public static void show() {
        pending = true;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (pending && minecraft.player != null && minecraft.level != null && minecraft.screen == null) {
            pending = false;
            minecraft.setScreen(new FeedbackScreen());
        }
    }

    public static void reset() {
        pending = false;
    }
}
