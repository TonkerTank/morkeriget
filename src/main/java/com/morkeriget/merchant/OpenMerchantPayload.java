package com.morkeriget.merchant;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks "Købmand", because menus are opened by the server
public record OpenMerchantPayload() implements CustomPacketPayload {
    public static final OpenMerchantPayload INSTANCE = new OpenMerchantPayload();

    public static final CustomPacketPayload.Type<OpenMerchantPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "open_merchant"));

    public static final StreamCodec<ByteBuf, OpenMerchantPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
