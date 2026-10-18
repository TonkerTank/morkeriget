package com.morkeriget.progress;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks + on a paragon stat
public record ParagonPayload(Paragon stat) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ParagonPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "paragon"));

    public static final StreamCodec<ByteBuf, ParagonPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> new ParagonPayload(Paragon.byId(id)), payload -> payload.stat.ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
