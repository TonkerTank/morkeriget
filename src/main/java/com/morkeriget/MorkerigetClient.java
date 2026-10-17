package com.morkeriget;

import com.morkeriget.combat.DamageNumbers;
import com.morkeriget.loot.LootBeams;
import com.morkeriget.skill.ManaBar;
import com.morkeriget.skill.SkillKeys;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Morkeriget.MODID, dist = Dist.CLIENT)
public class MorkerigetClient {
    public MorkerigetClient(IEventBus modEventBus, ModContainer container) {
        // Coloured light beams over good loot lying on the ground
        NeoForge.EVENT_BUS.addListener(LootBeams::onEntityTick);
        // Keys for dodging and skills
        modEventBus.addListener(SkillKeys::register);
        NeoForge.EVENT_BUS.addListener(SkillKeys::onClientTick);
        // Mana bar next to the hotbar
        modEventBus.addListener(ManaBar::register);
        // Damage numbers that float up from monsters you hit
        NeoForge.EVENT_BUS.addListener(DamageNumbers::onClientTick);
        NeoForge.EVENT_BUS.addListener(DamageNumbers::onRenderLevel);
    }
}
