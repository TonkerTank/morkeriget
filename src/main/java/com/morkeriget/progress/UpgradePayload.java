package com.morkeriget.progress;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player clicks + on an upgrade in the skill screen
public record UpgradePayload(Upgrade upgrade) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpgradePayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "upgrade"));

    public static final StreamCodec<ByteBuf, UpgradePayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> new UpgradePayload(Upgrade.byId(id)), payload -> payload.upgrade.ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
