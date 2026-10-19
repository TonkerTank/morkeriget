package com.morkeriget.skill;

import java.util.function.Supplier;

import com.morkeriget.Morkeriget;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// Entities the skills use. Their renderers are registered in MorkerigetClient, so nothing here needs the client
public final class SkillEntities {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Morkeriget.MODID);

    // Thunderstorm's lightning. Never saved, and sent to players up to 10 chunks away. It doesn't move, so it is
    // only sent once
    public static final Supplier<EntityType<StormBolt>> STORM_BOLT = ENTITIES.register("storm_bolt",
            () -> EntityType.Builder.<StormBolt>of(StormBolt::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(Morkeriget.MODID + ":storm_bolt"));

    private SkillEntities() {
    }

    static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
