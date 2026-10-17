package com.morkeriget.smithy;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks "Smedje", because menus are opened by the server
public record OpenSmithyPayload() implements CustomPacketPayload {
    public static final OpenSmithyPayload INSTANCE = new OpenSmithyPayload();

    public static final CustomPacketPayload.Type<OpenSmithyPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "open_smithy"));

    public static final StreamCodec<ByteBuf, OpenSmithyPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
