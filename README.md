# Realm of Darkness

**A Diablo-style action RPG inside Minecraft.** Pick a class, fight elites and bosses, chase legendary and mythic loot, and push into rifts, dungeons and Helltides. Works in single player and on servers. English and Danish.

> This is a **beta**. Balance will change, and bugs are expected. Feedback is very welcome, see the bottom of this page.

## Features

- **4 classes**: Barbarian, Sorcerer, Ranger and Necromancer, each with its own skills and a skill tree with upgrades.
- **Skills on keys 1-4**, with mana, cooldowns, a dodge roll (V) and health and mana potions (R and G).
- **Loot like in Diablo**: common, magic, rare, legendary, set and mythic items with random bonuses, item levels and item power. Legendary powers, set bonuses and 6 mythic items.
- **Smithy**: salvage items into essence, re-roll bonuses, masterwork, repair, socket gems, and extract legendary powers into a codex to imprint them on other items.
- **Elites and bosses** with modifiers and special attacks, plus elite packs out in the world.
- **Rifts** in 10 tiers with affixes, waves and a timer.
- **Dungeons** with themes, keys and a boss at the end.
- **Helltide** at night: kill monsters for cinders and open Helltide chests.
- **World bosses** that wake up on a timer, for everyone on the server.
- **Lord of Darkness**: an uber boss with three phases and a high chance of mythic items.
- **Paragon board** after level 100, with **glyphs** that level up as you clear dungeons.
- **Daily bounties**, a **season journey** in 4 chapters, a merchant, a stash, a loot bag (I), a loot filter (J), town portal scrolls and a training dummy to measure your damage per second.
- Rings and amulets, an equipment screen with all your stats, and floating damage numbers.

## How to start

1. Install **NeoForge for Minecraft 1.21.1** and put the jar in your `mods` folder.
2. In the game, press **K** to pick a class.
3. Kill monsters, level up, and spend skill points in **K**. The smithy, merchant, stash, bounties and more are opened from the same screen.

| Key | What it does |
|---|---|
| K | Skills, class, smithy, merchant, stash, bounties |
| 1-4 | Use skills |
| V | Dodge |
| R / G | Health potion / mana potion |
| I | Loot bag |
| J | Loot filter |

All keys can be changed in Options > Controls.

Most settings (drop chances, elite and boss strength, skill costs, XP speed) are in the config file `morkeriget-common.toml`.

## Feedback and bugs

Please report bugs and ideas on GitHub: **[github.com/TonkerTank/morkeriget/issues](https://github.com/TonkerTank/morkeriget/issues)**

If something crashes, please attach the crash report or `logs/latest.log`.

## Building and tests

Needs Java 21.

- `./gradlew build` builds the jar into `build/libs`.
- `./gradlew runClient` starts the game with the mod.
- `./gradlew runGameTestServer` runs all game tests. They also run on GitHub for every push.

## License

MIT, see [LICENSE](LICENSE).
