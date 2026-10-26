package com.morkeriget.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonParser;
import com.morkeriget.Morkeriget;
import com.morkeriget.bounty.BountyType;
import com.morkeriget.dungeon.DungeonTheme;
import com.morkeriget.elite.BossAbility;
import com.morkeriget.elite.EliteModifier;
import com.morkeriget.glyph.Glyph;
import com.morkeriget.journey.Achievement;
import com.morkeriget.loot.ItemSet;
import com.morkeriget.loot.LegendaryPower;
import com.morkeriget.loot.MythicItem;
import com.morkeriget.loot.Rarity;
import com.morkeriget.merchant.Ware;
import com.morkeriget.progress.Paragon;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Upgrade;
import com.morkeriget.rift.RiftAffix;
import com.morkeriget.skill.Skill;
import com.morkeriget.smithy.Gem;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Every text the game shows is in the language files, in English and Danish. Run them with ./gradlew runGameTestServer
@GameTestHolder(Morkeriget.MODID)
@PrefixGameTestTemplate(false)
public final class TranslationTests {
    private TranslationTests() {
    }

    // The names and descriptions of everything that has one. Their keys are made from the names in the code, so a
    // missing one is only found by trying them all
    @GameTest(template = "empty")
    public static void everyNameHasText(GameTestHelper helper) {
        List<Component> texts = new ArrayList<>();
        for (LegendaryPower power : LegendaryPower.values()) {
            texts.add(power.displayName());
            texts.add(power.description());
        }
        for (MythicItem mythic : MythicItem.values()) {
            texts.add(mythic.displayName());
            texts.add(mythic.description());
        }
        for (Rarity rarity : Rarity.values()) {
            texts.add(rarity.displayName());
        }
        for (ItemSet set : ItemSet.values()) {
            texts.add(set.displayName());
            texts.add(set.twoPieceBonus());
            texts.add(set.fourPieceBonus());
        }
        for (ItemSet.Piece piece : ItemSet.Piece.values()) {
            texts.add(piece.displayName());
        }
        for (PlayerClass playerClass : PlayerClass.values()) {
            texts.add(playerClass.displayName());
            texts.add(playerClass.description());
            texts.add(playerClass.bonus());
        }
        for (Gem gem : Gem.values()) {
            texts.add(gem.displayName());
            for (int tier = 1; tier <= 5; tier++) {
                texts.add(gem.name(tier));
                texts.add(gem.bonus(tier));
            }
        }
        for (EliteModifier modifier : EliteModifier.values()) {
            texts.add(modifier.displayName());
        }
        for (RiftAffix affix : RiftAffix.values()) {
            texts.add(affix.displayName());
            texts.add(affix.description());
        }
        for (BountyType type : BountyType.values()) {
            texts.add(type.text(1));
            texts.add(type.text(5));
        }
        for (Ware ware : Ware.values()) {
            texts.add(ware.displayName());
        }
        for (Paragon paragon : Paragon.values()) {
            texts.add(paragon.displayName());
            texts.add(paragon.description());
        }
        for (Skill skill : Skill.values()) {
            texts.add(skill.displayName());
        }
        for (Upgrade upgrade : Upgrade.values()) {
            texts.add(upgrade.displayName());
            texts.add(upgrade.description());
        }
        for (Glyph glyph : Glyph.values()) {
            texts.add(glyph.displayName());
            texts.add(glyph.describe(1));
        }
        for (Achievement achievement : Achievement.values()) {
            texts.add(achievement.text());
        }
        for (int chapter = 1; chapter <= Achievement.CHAPTERS; chapter++) {
            texts.add(Achievement.chapterName(chapter));
            texts.add(Achievement.chapterReward(chapter));
        }
        Set<String> missing = new TreeSet<>();
        for (Component text : texts) {
            findMissing(text, missing);
        }
        // These two only make their names inside their own package, so their keys are checked directly
        for (DungeonTheme theme : DungeonTheme.values()) {
            check("theme." + key(theme), missing);
        }
        for (BossAbility ability : BossAbility.values()) {
            check("ability." + key(ability) + ".name", missing);
            check("ability." + key(ability) + ".warning", missing);
        }
        helper.assertTrue(missing.isEmpty(), "No English text for " + missing);
        helper.succeed();
    }

    // Danish has a text for every English one and nothing more, so no one sees a raw key
    @GameTest(template = "empty")
    public static void danishMatchesEnglish(GameTestHelper helper) {
        Set<String> english = keys("en_us");
        Set<String> danish = keys("da_dk");
        Set<String> missing = new TreeSet<>(english);
        missing.removeAll(danish);
        Set<String> extra = new TreeSet<>(danish);
        extra.removeAll(english);
        helper.assertTrue(missing.isEmpty(), "No Danish text for " + missing);
        helper.assertTrue(extra.isEmpty(), "Danish text without English " + extra);
        helper.succeed();
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void check(String key, Set<String> missing) {
        if (!Language.getInstance().has(Morkeriget.MODID + "." + key)) {
            missing.add(key);
        }
    }

    // The text's own key, the keys of what is filled into it and of what is added after it
    private static void findMissing(Component text, Set<String> missing) {
        if (text.getContents() instanceof TranslatableContents translatable) {
            if (!Language.getInstance().has(translatable.getKey())) {
                missing.add(translatable.getKey());
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component component) {
                    findMissing(component, missing);
                }
            }
        }
        for (Component sibling : text.getSiblings()) {
            findMissing(sibling, missing);
        }
    }

    private static Set<String> keys(String language) {
        String path = "/assets/" + Morkeriget.MODID + "/lang/" + language + ".json";
        try (InputStream stream = TranslationTests.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + path);
            }
            return new TreeSet<>(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().keySet());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Couldn't read " + path, e);
        }
    }
}
