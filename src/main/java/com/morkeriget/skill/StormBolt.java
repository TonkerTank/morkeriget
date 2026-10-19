package com.morkeriget.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

// One lightning bolt from Thunderstorm, only there to be seen. The server puts it where the bolt hits and takes it away
// again after a moment, and every client draws it with StormBoltRenderer. The hit and the damage are done by TreeSkills,
// not by this. It never touches blocks, never burns anything and isn't saved with the world
public class StormBolt extends Entity {
    // How long it is seen, in ticks. The server keeps it a little longer, so clients that get it a tick late still
    // see it fade out the whole way
    public static final int LIFETIME = 8;
    private static final int SERVER_LIFETIME = LIFETIME + 2;

    public StormBolt(EntityType<? extends StormBolt> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // The shape comes from this, so it stays the same while it is seen. The id is sent to every client with the entity,
    // so everyone sees the same bolt without anything extra having to be sent
    public long seed() {
        return getUUID().getMostSignificantBits() ^ getUUID().getLeastSignificantBits();
    }

    @Override
    public void tick() {
        if (!level().isClientSide() && tickCount >= SERVER_LIFETIME) {
            discard();
        }
    }

    // Seen from far away, since it is 30 blocks tall and the entity itself is tiny
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
