package com.morkeriget.smithy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.morkeriget.Morkeriget;
import com.morkeriget.loot.LegendaryPower;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// The codex of power, like in Diablo 4: in the smithy a legendary power is taken out of an item (the item is used up)
// and kept here for good. Then it can be put on any other legendary for essence. Saved on the player and kept on death
public final class Codex {
    // What it costs to put a power from the codex on an item
    public static final int IMPRINT_COST = 25;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Morkeriget.MODID);
    // The names of the powers the player has
    private static final Supplier<AttachmentType<List<String>>> POWERS = ATTACHMENTS.register("codex",
            () -> AttachmentType.builder(() -> List.<String>of()).serialize(Codec.STRING.listOf()).copyOnDeath().build());

    private Codex() {
    }

    static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static boolean has(ServerPlayer player, LegendaryPower power) {
        return player.getData(POWERS).contains(power.name());
    }

    static void add(ServerPlayer player, LegendaryPower power) {
        if (!has(player, power)) {
            List<String> powers = new ArrayList<>(player.getData(POWERS));
            powers.add(power.name());
            player.setData(POWERS, List.copyOf(powers));
        }
    }

    // One bit for each power the player has, in the order of LegendaryPower.values(). Sent to the smithy screen
    static int mask(ServerPlayer player) {
        int mask = 0;
        for (LegendaryPower power : LegendaryPower.values()) {
            if (has(player, power)) {
                mask |= 1 << power.ordinal();
            }
        }
        return mask;
    }
}
