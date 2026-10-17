package com.morkeriget.skill;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Packet sent from the client to the server when the player presses a skill key
public record SkillPayload(Skill skill) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "skill"));

    public static final StreamCodec<ByteBuf, SkillPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> new SkillPayload(Skill.byId(id)), payload -> payload.skill.ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SkillPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Skills.use(player, payload.skill);
        }
    }
}
