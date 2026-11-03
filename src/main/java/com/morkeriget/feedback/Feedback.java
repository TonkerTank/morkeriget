package com.morkeriget.feedback;

import com.morkeriget.Morkeriget;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// The first time a player joins a world with the mod, a window asks for feedback, with a link to GitHub. It is shown
// once per player and world
public final class Feedback {
    public static final String ISSUES_URL = "https://github.com/TonkerTank/morkeriget/issues";
    private static final String SHOWN_TAG = Morkeriget.MODID + "_feedback_shown";

    private Feedback() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Feedback::registerPayloads);
        NeoForge.EVENT_BUS.addListener(Feedback::onLogin);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The lambda keeps ClientFeedback (client only) from being loaded on a dedicated server
        event.registrar("1").playToClient(FeedbackPayload.TYPE, FeedbackPayload.STREAM_CODEC, (payload, context) -> ClientFeedback.show());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && firstTime(player)) {
            PacketDistributor.sendToPlayer(player, new FeedbackPayload());
        }
    }

    // True the first time only. The mark is kept with the player, so it lasts through deaths and restarts
    public static boolean firstTime(ServerPlayer player) {
        if (player.getPersistentData().getBoolean(SHOWN_TAG)) {
            return false;
        }
        player.getPersistentData().putBoolean(SHOWN_TAG, true);
        return true;
    }
}
