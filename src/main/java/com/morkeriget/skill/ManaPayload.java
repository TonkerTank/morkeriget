package com.morkeriget.skill;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player with how much mana they have, so the client can draw the mana bar
public record ManaPayload(float mana) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ManaPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "mana"));

    public static final StreamCodec<ByteBuf, ManaPayload> STREAM_CODEC = ByteBufCodecs.FLOAT.map(ManaPayload::new, ManaPayload::mana);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
