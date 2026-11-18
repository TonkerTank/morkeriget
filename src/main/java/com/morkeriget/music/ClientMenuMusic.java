package com.morkeriget.music;

import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

// Plays the mod's menu music instead of Minecraft's whenever the game would play menu music
// (the title screen and the other menus before a world is loaded). Client only
public final class ClientMenuMusic {
    // No delay, so the music starts at once and plays again straight away when it ends. The file loops smoothly
    private static Music music;

    private ClientMenuMusic() {
    }

    public static void onSelectMusic(SelectMusicEvent event) {
        if (event.getOriginalMusic() != Musics.MENU) {
            return;
        }
        if (music == null) {
            music = new Music(MenuMusic.MENU, 0, 0, true);
        }
        event.setMusic(music);
    }
}
