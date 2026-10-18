package com.morkeriget.progress;

import java.util.List;

import com.morkeriget.Morkeriget;
import com.morkeriget.skill.Skill;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet sent from the server to the player with their level, XP, upgrades, class and keys, for the skill screen and
// the HUD. ranks is in the same order as Upgrade.values(), playerClass is "" before a class is picked, and slots has
// the Skill.ordinal() on each of the keys 1-4, or -1 for an empty key
public record ProgressPayload(int level, int xp, int xpNeeded, int points, List<Integer> ranks, String playerClass, List<Integer> slots)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ProgressPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "progress"));

    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    // composite only takes six fields, so this one is written out
    public static final StreamCodec<ByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, payload.level);
                ByteBufCodecs.VAR_INT.encode(buffer, payload.xp);
                ByteBufCodecs.VAR_INT.encode(buffer, payload.xpNeeded);
                ByteBufCodecs.VAR_INT.encode(buffer, payload.points);
                INT_LIST.encode(buffer, payload.ranks);
                ByteBufCodecs.STRING_UTF8.encode(buffer, payload.playerClass);
                INT_LIST.encode(buffer, payload.slots);
            },
            buffer -> new ProgressPayload(ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), INT_LIST.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer), INT_LIST.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public int rank(Upgrade upgrade) {
        return upgrade.ordinal() < ranks.size() ? ranks.get(upgrade.ordinal()) : 0;
    }

    // The skill on the key (0 to 3), or null
    public Skill slot(int index) {
        int id = index < slots.size() ? slots.get(index) : -1;
        return id >= 0 && id < Skill.values().length ? Skill.values()[id] : null;
    }

    // null before a class is picked
    public PlayerClass chosenClass() {
        return PlayerClass.byName(playerClass);
    }
}
