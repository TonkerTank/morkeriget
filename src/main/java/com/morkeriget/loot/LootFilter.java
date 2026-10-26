package com.morkeriget.loot;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.morkeriget.Morkeriget;
import com.morkeriget.Text;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The loot filter: loot below the rarity the player picked never lands on the ground. It is salvaged right away and
// drops as essence instead, so the ground isn't full of junk at high levels. Saved on the player and kept on death
public final class LootFilter {
    // What the player sees for each setting. Setting n hides the first n rarities, so 3 hides common, magic and rare
    // The settings' names are in the language files, under filter.<number>
    private static final int SETTINGS = 4;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    private static final Supplier<AttachmentType<Integer>> SETTING = ATTACHMENTS.register("loot_filter",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    // Sent from the client when the player presses the filter key. The server knows the setting, so it has no content
    public record CyclePayload() implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CyclePayload> TYPE = new CustomPacketPayload.Type<>(
                ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "loot_filter"));
        public static final StreamCodec<ByteBuf, CyclePayload> STREAM_CODEC = StreamCodec.unit(new CyclePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private LootFilter() {
    }

    static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("1")
                .playToServer(CyclePayload.TYPE, CyclePayload.STREAM_CODEC, LootFilter::handle));
    }

    // Whether loot of this rarity is salvaged instead of dropped for the player. Legendary and better is always dropped
    public static boolean hides(ServerPlayer player, Rarity rarity) {
        return rarity.ordinal() < Rarity.LEGENDARY.ordinal() && rarity.ordinal() < player.getData(SETTING);
    }

    private static void handle(CyclePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            int setting = (player.getData(SETTING) + 1) % SETTINGS;
            player.setData(SETTING, setting);
            player.displayClientMessage(Text.tr("filter.title").withStyle(ChatFormatting.GOLD)
                    .append(Text.tr("filter." + setting).withStyle(ChatFormatting.YELLOW)), true);
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.2f);
        }
    }
}
