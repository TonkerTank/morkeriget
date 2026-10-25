package com.morkeriget;

import com.morkeriget.combat.DamageNumbers;
import com.morkeriget.combat.TargetFrame;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.jewelry.JewelryScreen;
import com.morkeriget.loot.FavoriteClicks;
import com.morkeriget.loot.LootBeams;
import com.morkeriget.loot.LootTooltips;
import com.morkeriget.merchant.Merchant;
import com.morkeriget.merchant.MerchantScreen;
import com.morkeriget.progress.XpBar;
import com.morkeriget.smithy.Gem;
import com.morkeriget.smithy.Smithy;
import com.morkeriget.smithy.SmithyScreen;
import com.morkeriget.skill.ManaBar;
import com.morkeriget.skill.SkillBar;
import com.morkeriget.skill.SkillEntities;
import com.morkeriget.skill.StormBoltRenderer;
import com.morkeriget.skill.SkillKeys;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
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
        // Thunderstorm's lightning bolts, drawn in code
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerEntityRenderer(SkillEntities.STORM_BOLT.get(), StormBoltRenderer::new));
        // The mod's XP in place of the vanilla XP bar
        modEventBus.addListener(XpBar::register);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> XpBar.reset());
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
        // The gems are the same white picture coloured differently
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> {
            for (Gem gem : Gem.values()) {
                event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | gem.color : -1, Smithy.GEMS.get(gem).get());
            }
        });
        // Loot tooltips compare the item with what you are wearing
        NeoForge.EVENT_BUS.addListener(LootTooltips::onTooltip);
        // Alt+click marks loot as a favorite, and favorites have a star on their slot
        NeoForge.EVENT_BUS.addListener(FavoriteClicks::onMousePressed);
        NeoForge.EVENT_BUS.addListener(FavoriteClicks::onMouseReleased);
        NeoForge.EVENT_BUS.addListener(FavoriteClicks::onForeground);
        // The screens for the jewelry slots, the smithy and the merchant
        modEventBus.addListener((RegisterMenuScreensEvent event) -> {
            event.register(Jewelry.MENU.get(), JewelryScreen::new);
            event.register(Smithy.MENU.get(), SmithyScreen::new);
            event.register(Merchant.MENU.get(), MerchantScreen::new);
        });
    }
}
