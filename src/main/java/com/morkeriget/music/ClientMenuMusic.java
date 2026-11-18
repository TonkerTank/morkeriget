package com.morkeriget.music;

import com.morkeriget.Morkeriget;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

// Plays the mod's music instead of Minecraft's: in the menus before a world is loaded, and in boss fights, rifts and
// dungeons (the server says which, see CombatMusic). The fight music takes over at once and goes back to normal when
// the fight is over. A fight track whose file is missing is skipped, so the game's own music plays then. Client only
public final class ClientMenuMusic {
    private static CombatMusic.Mode mode = CombatMusic.Mode.NONE;
    // No delay, so the music starts at once and plays again straight away when it ends. The files loop smoothly
    private static Music menu;
    private static Music boss;
    private static Music rift;
    private static Music dungeon;

    private ClientMenuMusic() {
    }

    // Minecraft lets a track play to the end when the game's own music comes back, so the fight track is stopped here
    // when the fight is over. Going from one fight to another needs nothing, since the new track replaces the old one
    public static void setMode(CombatMusic.Mode newMode) {
        CombatMusic.Mode old = mode;
        mode = newMode;
        if (old != newMode && newMode == CombatMusic.Mode.NONE) {
            Music playing = switch (old) {
                case BOSS -> boss;
                case RIFT -> rift;
                case DUNGEON -> dungeon;
                case NONE -> null;
            };
            if (playing != null) {
                Minecraft.getInstance().getMusicManager().stopPlaying(playing);
            }
        }
    }

    public static void reset() {
        mode = CombatMusic.Mode.NONE;
    }

    public static void onSelectMusic(SelectMusicEvent event) {
        if (event.getOriginalMusic() == Musics.MENU) {
            if (menu == null) {
                menu = new Music(MenuMusic.MENU, 0, 0, true);
            }
            event.setMusic(menu);
            return;
        }
        if (Minecraft.getInstance().player == null) {
            return;
        }
        Music fight = switch (mode) {
            case BOSS -> boss != null ? boss : (boss = fightMusic("boss"));
            case RIFT -> rift != null ? rift : (rift = fightMusic("rift"));
            case DUNGEON -> dungeon != null ? dungeon : (dungeon = fightMusic("dungeon"));
            case NONE -> null;
        };
        if (fight != null && hasFile(fight == boss ? "boss" : fight == rift ? "rift" : "dungeon")) {
            event.setMusic(fight);
        }
    }

    private static Music fightMusic(String name) {
        return new Music(switch (name) {
            case "boss" -> MenuMusic.BOSS;
            case "rift" -> MenuMusic.RIFT;
            default -> MenuMusic.DUNGEON;
        }, 0, 0, true);
    }

    // Whether the track's file is in the mod. Resource packs can add or replace it too
    private static boolean hasFile(String name) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "sounds/music/" + name + ".ogg");
        return Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
    }
}
