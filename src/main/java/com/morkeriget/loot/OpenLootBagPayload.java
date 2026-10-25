package com.morkeriget.loot;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player presses I, because menus are opened by the server
public record OpenLootBagPayload() implements CustomPacketPayload {
    public static final OpenLootBagPayload INSTANCE = new OpenLootBagPayload();

    public static final CustomPacketPayload.Type<OpenLootBagPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "open_loot_bag"));

    public static final StreamCodec<ByteBuf, OpenLootBagPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
