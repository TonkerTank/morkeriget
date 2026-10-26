package com.morkeriget.glyph;

import java.util.List;

import com.morkeriget.Morkeriget;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the client with the socketed glyphs, one "KIND:level" per slot, empty for a free one
public record GlyphsPayload(List<String> glyphs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GlyphsPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "glyphs"));

    public static final StreamCodec<ByteBuf, GlyphsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GlyphsPayload::glyphs,
            GlyphsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
