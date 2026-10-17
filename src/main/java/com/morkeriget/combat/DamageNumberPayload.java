package com.morkeriget.combat;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// Packet sent from the server to a player when they hit something, so the client can show the damage number
public record DamageNumberPayload(int entityId, float amount, boolean crushing) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DamageNumberPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "damage_number"));

    public static final StreamCodec<ByteBuf, DamageNumberPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DamageNumberPayload::entityId,
            ByteBufCodecs.FLOAT, DamageNumberPayload::amount,
            ByteBufCodecs.BOOL, DamageNumberPayload::crushing,
            DamageNumberPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // The lambda keeps DamageNumbers (client only) from being loaded on a dedicated server
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> DamageNumbers.add(payload));
    }
}
