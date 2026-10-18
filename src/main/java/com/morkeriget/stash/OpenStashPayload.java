package com.morkeriget.stash;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks "Kiste", because menus are opened by the server
public record OpenStashPayload() implements CustomPacketPayload {
    public static final OpenStashPayload INSTANCE = new OpenStashPayload();

    public static final CustomPacketPayload.Type<OpenStashPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "open_stash"));

    public static final StreamCodec<ByteBuf, OpenStashPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
