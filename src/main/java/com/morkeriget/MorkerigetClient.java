package com.morkeriget;

import com.morkeriget.combat.DamageNumbers;
import com.morkeriget.combat.TargetFrame;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.jewelry.JewelryScreen;
import com.morkeriget.loot.LootBeams;
import com.morkeriget.skill.ManaBar;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.skill.SkillKeys;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = Morkeriget.MODID, dist = Dist.CLIENT)
public class MorkerigetClient {
    public MorkerigetClient(IEventBus modEventBus, ModContainer container) {
        // Coloured light beams over good loot lying on the ground
        NeoForge.EVENT_BUS.addListener(LootBeams::onEntityTick);
        // Keys for dodging and skills
        modEventBus.addListener(SkillKeys::register);
        NeoForge.EVENT_BUS.addListener(SkillKeys::onClientTickPre);
        NeoForge.EVENT_BUS.addListener(SkillKeys::onClientTick);
        // Mana bar next to the hotbar
        modEventBus.addListener(ManaBar::register);
        // Skill icons with keys and cooldowns next to the mana bar
        modEventBus.addListener(SkillBar::register);
        NeoForge.EVENT_BUS.addListener(SkillBar::onClientTick);
        // Damage numbers that float up from monsters you hit
        NeoForge.EVENT_BUS.addListener(DamageNumbers::onClientTick);
        NeoForge.EVENT_BUS.addListener(DamageNumbers::onRenderLevel);
        // Name, level and health of the monster you look at, at the top of the screen
        modEventBus.addListener(TargetFrame::register);
        NeoForge.EVENT_BUS.addListener(TargetFrame::onRenderGuiPre);
        NeoForge.EVENT_BUS.addListener(TargetFrame::onBossBar);
        // The screen for the jewelry slots
        modEventBus.addListener((RegisterMenuScreensEvent event) -> event.register(Jewelry.MENU.get(), JewelryScreen::new));
    }
}
