package com.morkeriget.progress;

import com.morkeriget.Morkeriget;
import com.morkeriget.skill.Skill;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player puts a skill on one of the keys 1-4 in the skill screen.
// slot is 0 to 3
public record AssignSlotPayload(int slot, Skill skill) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AssignSlotPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "assign_slot"));

    public static final StreamCodec<ByteBuf, AssignSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssignSlotPayload::slot,
            ByteBufCodecs.VAR_INT.map(Skill::byId, Skill::ordinal), AssignSlotPayload::skill,
            AssignSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
