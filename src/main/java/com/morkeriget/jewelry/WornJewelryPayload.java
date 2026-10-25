package com.morkeriget.jewelry;

import java.util.List;

import com.morkeriget.Morkeriget;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// The jewelry the player wears, sent to them whenever it changes, so loot tooltips can compare new rings and amulets
// with it. One entry per slot, rings first, empty for a free slot
public record WornJewelryPayload(List<ItemStack> items) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WornJewelryPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "worn_jewelry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WornJewelryPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), WornJewelryPayload::items, WornJewelryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
