package com.morkeriget.guide;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player with the guide step they are on (Guide.DONE when it is over) and how far
// they are with it
public record GuidePayload(int step, int count) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GuidePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "guide"));

    public static final StreamCodec<ByteBuf, GuidePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GuidePayload::step,
            ByteBufCodecs.VAR_INT, GuidePayload::count,
            GuidePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
