package com.morkeriget.music;

import com.morkeriget.Morkeriget;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The mod's own music: for the main menu, and for boss fights, rifts and dungeons. See sounds.json and ClientMenuMusic
public final class MenuMusic {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Morkeriget.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MENU = music("menu_music");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS = music("boss_music");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT = music("rift_music");
    public static final DeferredHolder<SoundEvent, SoundEvent> DUNGEON = music("dungeon_music");

    private MenuMusic() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> music(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
        CombatMusic.register(modEventBus);
    }
}
