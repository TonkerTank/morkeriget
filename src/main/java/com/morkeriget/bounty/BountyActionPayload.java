package com.morkeriget.bounty;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server from the bounty board: OPEN to see the bounties, 0 to 2 to take the
// reward for that bounty, or BONUS for the reward for finishing all of them
public record BountyActionPayload(int action) implements CustomPacketPayload {
    public static final int OPEN = -1;
    public static final int BONUS = BountyBoard.COUNT;

    public static final CustomPacketPayload.Type<BountyActionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "bounty_action"));

    // VAR_INT can't be negative in a nice way, so the action is sent one higher
    public static final StreamCodec<ByteBuf, BountyActionPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            value -> new BountyActionPayload(value - 1), payload -> payload.action + 1);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
