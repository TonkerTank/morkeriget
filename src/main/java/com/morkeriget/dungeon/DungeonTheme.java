package com.morkeriget.dungeon;

import java.util.List;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// What a dungeon is built of and who lives there, picked at random each time. Blocks that are in a list more than once
// are used more often. The nether's magma floor burns, and the ice floor is slippery
enum DungeonTheme {
    CRYPT("Krypten",
            List.of(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE),
            List.of(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_DEEPSLATE),
            Blocks.DEEPSLATE_TILES, Blocks.SOUL_LANTERN, Blocks.COBWEB, true,
            List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK, EntityType.VINDICATOR, EntityType.WITCH),
            List.of(EntityType.VINDICATOR, EntityType.WITHER_SKELETON, EntityType.ZOMBIE, EntityType.HUSK)),
    NETHER("Helvedesgruben",
            List.of(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS),
            List.of(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.MAGMA_BLOCK),
            Blocks.NETHER_BRICKS, Blocks.LANTERN, Blocks.SHROOMLIGHT, false,
            List.of(EntityType.WITHER_SKELETON, EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.SKELETON, EntityType.HUSK),
            List.of(EntityType.WITHER_SKELETON, EntityType.VINDICATOR)),
    FROZEN("Den Frosne Krypt",
            List.of(Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.POLISHED_DIORITE, Blocks.SNOW_BLOCK),
            List.of(Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.POLISHED_DIORITE, Blocks.SNOW_BLOCK),
            Blocks.PACKED_ICE, Blocks.SOUL_LANTERN, Blocks.BLUE_ICE, false,
            List.of(EntityType.STRAY, EntityType.STRAY, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.VINDICATOR),
            List.of(EntityType.STRAY, EntityType.VINDICATOR, EntityType.ZOMBIE)),
    OVERGROWN("Den Glemte Have",
            List.of(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
            List.of(Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, Blocks.COARSE_DIRT, Blocks.MOSSY_COBBLESTONE, Blocks.ROOTED_DIRT),
            Blocks.MOSSY_STONE_BRICKS, Blocks.LANTERN, Blocks.COBWEB, true,
            List.of(EntityType.ZOMBIE, EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.WITCH, EntityType.BOGGED, EntityType.BOGGED),
            List.of(EntityType.ZOMBIE, EntityType.VINDICATOR, EntityType.BOGGED));

    final String displayName;
    private final List<Block> walls;
    private final List<Block> floors;
    private final Block ceiling;
    private final Block light;
    // Something in the corners: cobwebs hang under the ceiling, the others stand on the floor
    private final Block decoration;
    private final boolean hanging;
    private final List<EntityType<? extends Monster>> monsters;
    private final List<EntityType<? extends Monster>> bosses;

    DungeonTheme(String displayName, List<Block> walls, List<Block> floors, Block ceiling, Block light, Block decoration, boolean hanging,
                 List<EntityType<? extends Monster>> monsters, List<EntityType<? extends Monster>> bosses) {
        this.displayName = displayName;
        this.walls = walls;
        this.floors = floors;
        this.ceiling = ceiling;
        this.light = light;
        this.decoration = decoration;
        this.hanging = hanging;
        this.monsters = monsters;
        this.bosses = bosses;
    }

    static DungeonTheme random(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }

    BlockState wall(RandomSource random) {
        return walls.get(random.nextInt(walls.size())).defaultBlockState();
    }

    BlockState floor(RandomSource random) {
        return floors.get(random.nextInt(floors.size())).defaultBlockState();
    }

    BlockState ceiling() {
        return ceiling.defaultBlockState();
    }

    // A lantern block, hung from the ceiling
    Block light() {
        return light;
    }

    BlockState decoration() {
        return decoration.defaultBlockState();
    }

    // How far above the floor the corner decoration goes, in a room this high
    int decorationHeight(int roomHeight) {
        return hanging ? roomHeight - 1 : 1;
    }

    EntityType<? extends Monster> monster(RandomSource random) {
        return monsters.get(random.nextInt(monsters.size()));
    }

    EntityType<? extends Monster> boss(RandomSource random) {
        return bosses.get(random.nextInt(bosses.size()));
    }
}
