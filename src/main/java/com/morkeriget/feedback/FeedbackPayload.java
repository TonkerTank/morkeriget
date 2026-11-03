package com.morkeriget.feedback;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the client to show the feedback window
public record FeedbackPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FeedbackPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "feedback"));

    public static final StreamCodec<ByteBuf, FeedbackPayload> STREAM_CODEC = StreamCodec.unit(new FeedbackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
