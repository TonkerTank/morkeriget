package com.morkeriget.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One bounty on the board: what it asks for, how far the player has got, and whether the reward has been taken
public record Bounty(BountyType type, int goal, int progress, boolean claimed) {
    public static final Codec<Bounty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(BountyType::byName, BountyType::name).fieldOf("type").forGetter(Bounty::type),
            Codec.INT.fieldOf("goal").forGetter(Bounty::goal),
            Codec.INT.fieldOf("progress").forGetter(Bounty::progress),
            Codec.BOOL.fieldOf("claimed").forGetter(Bounty::claimed)
    ).apply(instance, Bounty::new));

    public static final StreamCodec<ByteBuf, Bounty> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, bounty -> bounty.type.ordinal(),
            ByteBufCodecs.VAR_INT, Bounty::goal,
            ByteBufCodecs.VAR_INT, Bounty::progress,
            ByteBufCodecs.BOOL, Bounty::claimed,
            (type, goal, progress, claimed) -> new Bounty(BountyType.values()[type], goal, progress, claimed));

    public boolean done() {
        return progress >= goal;
    }

    Bounty withProgress(int amount) {
        return new Bounty(type, goal, Math.min(goal, progress + amount), claimed);
    }

    Bounty withClaimed() {
        return new Bounty(type, goal, progress, true);
    }
}
