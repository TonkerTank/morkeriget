package com.morkeriget.progress;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks + on a paragon stat. amount is how many points
// to spend, 1 or 10 with shift
public record ParagonPayload(Paragon stat, int amount) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ParagonPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "paragon"));

    public static final StreamCodec<ByteBuf, ParagonPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, payload -> payload.stat.ordinal(),
            ByteBufCodecs.VAR_INT, ParagonPayload::amount,
            (id, amount) -> new ParagonPayload(Paragon.byId(id), amount));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
