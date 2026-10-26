package com.morkeriget.glyph;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the client to the server when the player takes the glyph in a slot out in the paragon screen
public record GlyphActionPayload(int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GlyphActionPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "glyph_action"));

    public static final StreamCodec<ByteBuf, GlyphActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GlyphActionPayload::slot,
            GlyphActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
