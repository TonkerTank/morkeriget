package com.morkeriget.bounty;

import java.util.List;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player with their bounties. open is true when the player clicked "Opgaver",
// so the screen should open, and false when it is only an update
public record BountiesPayload(List<Bounty> bounties, boolean bonusClaimed, boolean open) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BountiesPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "bounties"));

    public static final StreamCodec<ByteBuf, BountiesPayload> STREAM_CODEC = StreamCodec.composite(
            Bounty.STREAM_CODEC.apply(ByteBufCodecs.list()), BountiesPayload::bounties,
            ByteBufCodecs.BOOL, BountiesPayload::bonusClaimed,
            ByteBufCodecs.BOOL, BountiesPayload::open,
            BountiesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public boolean allClaimed() {
        return !bounties.isEmpty() && bounties.stream().allMatch(Bounty::claimed);
    }
}
