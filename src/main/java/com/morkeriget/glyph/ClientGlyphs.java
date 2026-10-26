package com.morkeriget.glyph;

import java.util.ArrayList;
import java.util.List;

import com.morkeriget.progress.ParagonScreen;

import net.minecraft.client.Minecraft;

// The glyphs the player has socketed, as the server last sent them, for the paragon screen. Client only
public final class ClientGlyphs {
    private static List<String> glyphs = List.of();

    private ClientGlyphs() {
    }

    static void set(GlyphsPayload payload) {
        glyphs = payload.glyphs();
        if (Minecraft.getInstance().screen instanceof ParagonScreen screen) {
            screen.refresh();
        }
    }

    // One entry per slot the player has, null for a free one
    public static List<Glyphs.Socketed> get(int slots) {
        List<Glyphs.Socketed> socketed = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            socketed.add(slot < glyphs.size() ? Glyphs.parse(glyphs.get(slot)) : null);
        }
        return socketed;
    }
}
