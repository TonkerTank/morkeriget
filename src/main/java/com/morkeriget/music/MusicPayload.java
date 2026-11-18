package com.morkeriget.music;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to a player when the fight music they should hear changes, see CombatMusic
public record MusicPayload(CombatMusic.Mode mode) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MusicPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "music"));

    public static final StreamCodec<ByteBuf, MusicPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            index -> new MusicPayload(CombatMusic.Mode.values()[Math.floorMod(index, CombatMusic.Mode.values().length)]),
            payload -> payload.mode().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
