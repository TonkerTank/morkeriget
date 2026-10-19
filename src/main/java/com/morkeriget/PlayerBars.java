package com.morkeriget;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

// A boss bar that can say something different to each player, e.g. how far it is and which way to go. A vanilla bar
// has one name for everyone, so this keeps one bar for each player. Call show() for every player who should see it,
// then finish(), which takes the bar away from everyone who wasn't shown it since the last finish(). Server only
public final class PlayerBars {
    private final BossEvent.BossBarColor color;
    private final BossEvent.BossBarOverlay overlay;
    private final Map<UUID, ServerBossEvent> bars = new HashMap<>();
    private final Set<UUID> shown = new HashSet<>();

    public PlayerBars(BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
        this.color = color;
        this.overlay = overlay;
    }

    public void show(ServerPlayer player, Component name, float progress) {
        ServerBossEvent bar = bars.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(name, color, overlay));
        bar.setName(name);
        bar.setProgress(progress);
        if (!bar.getPlayers().contains(player)) {
            // A player who logged out and in again is a new object, so the old one is taken off first
            bar.removeAllPlayers();
            bar.addPlayer(player);
        }
        shown.add(player.getUUID());
    }

    public void finish() {
        for (Iterator<Map.Entry<UUID, ServerBossEvent>> it = bars.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ServerBossEvent> entry = it.next();
            if (!shown.contains(entry.getKey())) {
                entry.getValue().removeAllPlayers();
                it.remove();
            }
        }
        shown.clear();
    }

    public void clear() {
        for (ServerBossEvent bar : bars.values()) {
            bar.removeAllPlayers();
        }
        bars.clear();
        shown.clear();
    }
}
