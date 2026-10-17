package com.morkeriget;

import org.slf4j.Logger;

import com.morkeriget.combat.PlayerHits;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.jewelry.Jewelry;
import com.morkeriget.loot.Loot;
import com.morkeriget.portal.TownPortal;
import com.morkeriget.progress.Progression;
import com.morkeriget.rift.Rifts;
import com.morkeriget.skill.Skills;
import com.morkeriget.smithy.Smithy;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Morkeriget.MODID)
public class Morkeriget {
    public static final String MODID = "morkeriget";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Morkeriget(IEventBus modEventBus, ModContainer modContainer) {
        // Settings in config/morkeriget-common.toml
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Monsters drop weapons and armour with a rarity and random bonuses
        Loot.register(modEventBus);
        // Some monsters spawn as stronger elites that always drop good loot
        Elites.register(modEventBus);
        // Monsters get a level based on the nearest player
        MonsterLevels.register(modEventBus);
        // Skills on keys, like Whirlwind
        Skills.register(modEventBus);
        // Special weapon affixes and damage numbers when the player hits something
        PlayerHits.register(modEventBus);
        // Levels, XP and skill points
        Progression.register(modEventBus);
        // Rings, amulets and the jewelry slots
        Jewelry.register(modEventBus);
        // Salvaging loot into essence and rerolling bonuses
        Smithy.register(modEventBus);
        // Rift stones open rifts with waves of monsters and a guardian at the end
        Rifts.register(modEventBus);
        // Town portal scrolls take you home and back again
        TownPortal.register(modEventBus);
    }
}
