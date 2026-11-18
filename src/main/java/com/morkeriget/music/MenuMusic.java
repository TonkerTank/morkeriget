package com.morkeriget.music;

import com.morkeriget.Morkeriget;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's own music for the main menu, see sounds.json and ClientMenuMusic
public final class MenuMusic {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Morkeriget.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MENU = SOUNDS.register("menu_music",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "menu_music")));

    private MenuMusic() {
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
