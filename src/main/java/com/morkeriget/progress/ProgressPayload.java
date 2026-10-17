package com.morkeriget.progress;

import java.util.List;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player with their level, XP and upgrades, for the skill screen and the HUD.
// ranks is in the same order as Upgrade.values()
public record ProgressPayload(int level, int xp, int xpNeeded, int points, List<Integer> ranks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ProgressPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "progress"));

    public static final StreamCodec<ByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ProgressPayload::level,
            ByteBufCodecs.VAR_INT, ProgressPayload::xp,
            ByteBufCodecs.VAR_INT, ProgressPayload::xpNeeded,
            ByteBufCodecs.VAR_INT, ProgressPayload::points,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ProgressPayload::ranks,
            ProgressPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int rank(Upgrade upgrade) {
        return upgrade.ordinal() < ranks.size() ? ranks.get(upgrade.ordinal()) : 0;
    }
}
