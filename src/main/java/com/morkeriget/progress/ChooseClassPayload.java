package com.morkeriget.progress;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player picks a class in the class screen
public record ChooseClassPayload(PlayerClass playerClass) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChooseClassPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "choose_class"));

    public static final StreamCodec<ByteBuf, ChooseClassPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> new ChooseClassPayload(PlayerClass.values()[Math.max(0, Math.min(PlayerClass.values().length - 1, id))]),
            payload -> payload.playerClass.ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
