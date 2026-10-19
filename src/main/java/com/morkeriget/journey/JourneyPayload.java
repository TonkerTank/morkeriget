package com.morkeriget.journey;

import java.util.List;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player with their journey stats, one number for each Achievement.Stat in order.
// open is true when the player asked to see the journey, so the screen should open, and false when it is only an update
public record JourneyPayload(List<Integer> stats, boolean open) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<JourneyPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "journey"));

    public static final StreamCodec<ByteBuf, JourneyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), JourneyPayload::stats,
            ByteBufCodecs.BOOL, JourneyPayload::open,
            JourneyPayload::new);

    // Sent from the client when the player opens the journey. The server has the stats, so it has no content
    public record Open() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Open> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "open_journey"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int value(Achievement.Stat stat) {
        return stat.ordinal() < stats.size() ? stats.get(stat.ordinal()) : 0;
    }
}
