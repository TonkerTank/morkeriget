package com.morkeriget.bounty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.util.RandomSource;

// A player's bounties for one day. day is the real date, e.g. "2026-10-18", so new bounties come at midnight.
// bonusClaimed is whether the extra reward for finishing all of them has been taken
public record BountyBoard(String day, List<Bounty> bounties, boolean bonusClaimed) {
    public static final int COUNT = 3;
    public static final BountyBoard EMPTY = new BountyBoard("", List.of(), false);

    public static final Codec<BountyBoard> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("day").forGetter(BountyBoard::day),
            Bounty.CODEC.listOf().fieldOf("bounties").forGetter(BountyBoard::bounties),
            Codec.BOOL.fieldOf("bonus_claimed").forGetter(BountyBoard::bonusClaimed)
    ).apply(instance, BountyBoard::new));

    // Three different kinds of bounty
    static BountyBoard roll(String day, RandomSource random) {
        List<BountyType> types = new ArrayList<>(Arrays.asList(BountyType.values()));
        List<Bounty> bounties = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            BountyType type = types.remove(random.nextInt(types.size()));
            bounties.add(new Bounty(type, type.goal(random), 0, false));
        }
        return new BountyBoard(day, List.copyOf(bounties), false);
    }

    public boolean allClaimed() {
        return !bounties.isEmpty() && bounties.stream().allMatch(Bounty::claimed);
    }

    BountyBoard with(int index, Bounty bounty) {
        List<Bounty> changed = new ArrayList<>(bounties);
        changed.set(index, bounty);
        return new BountyBoard(day, List.copyOf(changed), bonusClaimed);
    }

    BountyBoard withBonusClaimed() {
        return new BountyBoard(day, bounties, true);
    }
}
