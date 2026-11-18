package com.morkeriget.combat;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to a player to shake their camera, see CameraShake. strength is from 0 to 1
public record ShakePayload(float strength) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShakePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "shake"));

    public static final StreamCodec<ByteBuf, ShakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ShakePayload::strength,
            ShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
