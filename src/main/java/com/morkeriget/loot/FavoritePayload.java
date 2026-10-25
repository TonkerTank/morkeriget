package com.morkeriget.loot;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player Alt+clicks loot, with the menu and the slot it is in
public record FavoritePayload(int containerId, int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FavoritePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "favorite"));

    public static final StreamCodec<ByteBuf, FavoritePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FavoritePayload::containerId,
            ByteBufCodecs.VAR_INT, FavoritePayload::slot,
            FavoritePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
