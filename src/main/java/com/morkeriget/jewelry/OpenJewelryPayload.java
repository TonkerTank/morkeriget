package com.morkeriget.jewelry;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks "Smykker", because menus are opened by the server
public record OpenJewelryPayload() implements CustomPacketPayload {
    public static final OpenJewelryPayload INSTANCE = new OpenJewelryPayload();

    public static final CustomPacketPayload.Type<OpenJewelryPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "open_jewelry"));

    public static final StreamCodec<ByteBuf, OpenJewelryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
