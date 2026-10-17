package com.morkeriget.skill;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Packet sent from the client to the server when the player presses the Whirlwind key
public record WhirlwindPayload() implements CustomPacketPayload {
    public static final WhirlwindPayload INSTANCE = new WhirlwindPayload();

    public static final CustomPacketPayload.Type<WhirlwindPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "whirlwind"));

    public static final StreamCodec<ByteBuf, WhirlwindPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, WhirlwindPayload::handle);
    }

    private static void handle(WhirlwindPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Skills.whirlwind(player);
        }
    }
}
