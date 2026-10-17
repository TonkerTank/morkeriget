package com.morkeriget.skill;

import com.morkeriget.Config;
import com.morkeriget.combat.PlayerHits;
import com.morkeriget.loot.ItemSet;
import com.morkeriget.loot.SetBonuses;
import com.morkeriget.progress.Progression;
import com.morkeriget.progress.Upgrade;
import com.morkeriget.smithy.Gems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// The mana skills cost. It lives on the server and slowly fills up again. The client only gets told the value to draw it
public final class Mana {
    public static final float MAX = 100;
    private static final String MANA_TAG = "morkeriget_mana";

    private Mana() {
    }

    public static float get(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.contains(MANA_TAG) ? data.getFloat(MANA_TAG) : MAX;
    }

    public static void spend(ServerPlayer player, float amount) {
        set(player, get(player) - amount);
    }

    public static void add(ServerPlayer player, float amount) {
        set(player, Math.min(MAX, get(player) + amount));
    }

    private static void set(ServerPlayer player, float mana) {
        player.getPersistentData().putFloat(MANA_TAG, mana);
        PacketDistributor.sendToPlayer(player, new ManaPayload(mana));
    }

    // Regenerates twice a second. It also sends the value every time, so the bar is right after joining or respawning
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            // Each Spirit upgrade rank makes mana come back 20% faster, and two Stormcaller pieces 50% faster
            float regen = Config.MANA_REGEN.get().floatValue() * (1 + 0.2f * Progression.rank(player, Upgrade.SPIRIT));
            if (SetBonuses.has(player, ItemSet.STORMCALLER, 2)) {
                regen *= 1.5f;
            }
            // Sapphires in worn items
            regen *= 1 + PlayerHits.total(PlayerHits.equippedItems(player), Gems.MANA_REGEN_TAG) / 100f;
            set(player, Math.min(MAX, get(player) + regen / 2));
        }
    }
}
