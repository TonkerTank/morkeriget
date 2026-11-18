package com.morkeriget.music;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.morkeriget.dungeon.Dungeons;
import com.morkeriget.elite.Bosses;
import com.morkeriget.rift.Rifts;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Works out once a second which of the mod's fight music each player should hear: a boss fight comes first, then a
// rift, then a dungeon. Only changes are sent, and the client picks the music, see ClientMenuMusic. Server side
public final class CombatMusic {
    public enum Mode {
        NONE, DUNGEON, RIFT, BOSS
    }

    private static final Map<UUID, Mode> SENT = new HashMap<>();

    private CombatMusic() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CombatMusic::registerPayloads);
        NeoForge.EVENT_BUS.addListener(CombatMusic::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> SENT.remove(event.getEntity().getUUID()));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // The lambda keeps ClientMenuMusic (client only) from being loaded on a dedicated server
        event.registrar("1").playToClient(MusicPayload.TYPE, MusicPayload.STREAM_CODEC, (payload, context) -> ClientMenuMusic.setMode(payload.mode()));
    }

    public static Mode modeFor(ServerPlayer player) {
        if (Bosses.inBossFight(player)) {
            return Mode.BOSS;
        }
        if (Rifts.isInRift(player)) {
            return Mode.RIFT;
        }
        return player.level().dimension() == Dungeons.LEVEL ? Mode.DUNGEON : Mode.NONE;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        Mode mode = modeFor(player);
        if (SENT.put(player.getUUID(), mode) != mode) {
            PacketDistributor.sendToPlayer(player, new MusicPayload(mode));
        }
    }
}
