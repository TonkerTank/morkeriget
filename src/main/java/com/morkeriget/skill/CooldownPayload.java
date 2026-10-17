package com.morkeriget.skill;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player when a skill was used, with its cooldown in ticks, so the skill bar can show it
public record CooldownPayload(Skill skill, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CooldownPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "cooldown"));

    public static final StreamCodec<ByteBuf, CooldownPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(Skill::byId, Skill::ordinal), CooldownPayload::skill,
            ByteBufCodecs.VAR_INT, CooldownPayload::ticks,
            CooldownPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
