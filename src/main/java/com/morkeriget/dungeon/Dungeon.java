package com.morkeriget.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.Morkeriget;
import com.morkeriget.Text;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.glyph.Glyphs;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.portal.TownPortal;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.rift.RiftAffix;
import com.morkeriget.rift.Rifts;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;
import com.morkeriget.uber.Uber;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// One dungeon and the group in it. The rooms are cells in a grid, each a box of stone with a door to the next, laid out
// as a winding row from the entrance to the boss, with guarded treasure rooms off to the side. Each dungeon has a theme
// that decides what it is built of and which monsters live there, and the affixes from its key. See Dungeons
final class Dungeon {
    static final int LIVES = 3;
    // Each room is a CELL x CELL box: walls on the outside, floor at FLOOR_Y and the ceiling HEIGHT above it
    private static final int CELL = 16;
    private static final int HEIGHT = 6;
    private static final int FLOOR_Y = 64;
    private static final int ARENA_HEIGHT = 10;
    // How far apart the dungeons are. The row of rooms can wind at most this far from its entrance
    private static final int SLOT_SPACING = 512;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    // 20 ticks is one second. Time to pick up the loot after the boss, and how long a dungeon waits for a group that
    // has left without using the exit
    private static final int HOME_DELAY = 30 * 20;
    private static final int EMPTY_TIMEOUT = 60 * 20;
    static final ResourceLocation TIER_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "dungeon_tier");
    private static final ResourceLocation AFFIX_BOOST = ResourceLocation.fromNamespaceAndPath(Morkeriget.MODID, "dungeon_affix");
    // The affixes from the key, the same as on rift stones: how long the warning is before an explosion or meteor goes
    // off, how big they are and how much of a player's max health they take
    private static final int HAZARD_WARNING = 30;
    private static final double EXPLOSION_RADIUS = 3.0;
    private static final float EXPLOSION_DAMAGE = 0.15f;
    private static final int METEOR_INTERVAL = 7 * 20;
    private static final double UBER_MYTHIC_CHANCE = 0.5;
    private static final double GLYPH_CHANCE = 0.1;
    private static final double GLYPH_CHANCE_PER_TIER = 0.03;
    private static final double METEOR_RADIUS = 2.5;
    private static final float METEOR_DAMAGE = 0.25f;

    private enum Kind {
        ENTRANCE, NORMAL, SEAL, BOSS, TREASURE
    }

    private static final class Room {
        final int cellX;
        final int cellZ;
        // How many cells it covers each way, 2 for the uber boss's arena
        int size = 1;
        Kind kind = Kind.NORMAL;
        boolean entered;
        boolean cleared;
        // Its monsters that are still alive
        final Set<UUID> alive = new HashSet<>();

        Room(int cellX, int cellZ) {
            this.cellX = cellX;
            this.cellZ = cellZ;
        }
    }

    // Something that goes off at a place after a warning: an explosion from a dead monster or a meteor
    private record Hazard(Vec3 pos, int at, double radius, float damage, boolean meteor) {
    }

    final ServerLevel level;
    final int slot;
    private final int tier;
    private final int monsterLevel;
    private final DungeonTheme theme;
    private final List<RiftAffix> affixes;
    // A fight with Mørkets Herre instead of a normal dungeon: only the entrance and a big arena, see Uber
    final boolean uber;
    final Set<UUID> members = new HashSet<>();
    // The row from the entrance to the boss
    private final List<Room> rooms = new ArrayList<>();
    // The treasure rooms off to the side of it
    private final List<Room> sideRooms = new ArrayList<>();
    // The room each treasure room is joined to, in the same order as sideRooms
    private final List<Room> treasureParents = new ArrayList<>();
    // The monsters that really died, as opposed to just being gone
    private final Set<UUID> died = new HashSet<>();
    // Monsters the dungeon didn't spawn itself but that came in while it was going, like the boss's helpers. They don't
    // count for clearing rooms, but they go away with the dungeon, see Dungeons.onJoinLevel
    private final Set<UUID> extras = new HashSet<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final ServerBossEvent bar = new ServerBossEvent(Text.tr("dungeon.bar"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private UUID bossId;
    private int lives = LIVES;
    private boolean failed;
    // Counts down to sending everyone home once the boss is dead, -1 before that
    private int homeIn = -1;
    private int empty;
    private int ticks;

    Dungeon(ServerLevel level, int slot, int tier, int monsterLevel, DungeonTheme theme, List<RiftAffix> affixes, boolean uber,
            List<ServerPlayer> party) {
        this.level = level;
        this.slot = slot;
        this.tier = tier;
        this.monsterLevel = monsterLevel;
        this.theme = theme;
        this.affixes = affixes;
        this.uber = uber;
        for (ServerPlayer player : party) {
            members.add(player.getUUID());
        }
    }

    DungeonTheme theme() {
        return theme;
    }

    boolean has(RiftAffix affix) {
        return affixes.contains(affix);
    }

    boolean owns(UUID monster) {
        return monster.equals(bossId) || extras.contains(monster) || allRooms().stream().anyMatch(room -> room.alive.contains(monster));
    }

    void addExtra(Entity monster) {
        extras.add(monster.getUUID());
    }

    // Whether the spot is in this dungeon's part of the dimension, see SLOT_SPACING
    boolean contains(double x) {
        return Math.abs(x - slot * SLOT_SPACING) < SLOT_SPACING / 2.0;
    }

    // A treasure room's chest can't be opened until its guards are dead
    boolean isLockedChest(BlockPos pos) {
        return sideRooms.stream().anyMatch(room -> !room.cleared && pos.equals(chestPos(room)));
    }

    private BlockPos chestPos(Room room) {
        return new BlockPos(originX(room) + 7, FLOOR_Y + 1, originZ(room) + 7);
    }

    // For the tests
    List<BlockPos> treasureChests() {
        return sideRooms.stream().map(this::chestPos).toList();
    }

    // Still going, so a player who dies can come back in
    boolean isOpen() {
        return !failed && homeIn < 0;
    }

    int lives() {
        return lives;
    }

    private List<Room> allRooms() {
        List<Room> all = new ArrayList<>(rooms);
        all.addAll(sideRooms);
        return all;
    }

    // ---- Building ----

    // One more room for every second tier, from 5 at tier I to 9 at tier X. One treasure room, two from tier IV
    void build(RandomSource random) {
        if (uber) {
            buildArena(random);
            return;
        }
        int count = 5 + (tier - 1) / 2;
        layOut(random, count);
        rooms.get(0).kind = Kind.ENTRANCE;
        rooms.get(count - 2).kind = Kind.SEAL;
        rooms.get(count - 1).kind = Kind.BOSS;
        addSideRooms(random, tier >= 4 ? 2 : 1);
        for (Room room : allRooms()) {
            buildRoom(room, random);
        }
        for (int i = 1; i < rooms.size(); i++) {
            setDoor(rooms.get(i - 1), rooms.get(i), rooms.get(i).kind == Kind.BOSS);
        }
        for (int i = 0; i < sideRooms.size(); i++) {
            setDoor(treasureParents.get(i), sideRooms.get(i), false);
        }
        Room entrance = rooms.get(0);
        entrance.entered = true;
        entrance.cleared = true;
    }

    // The entrance, and the arena right next to it, two cells each way and taller, for a boss twice the normal size
    private void buildArena(RandomSource random) {
        Room entrance = new Room(0, 0);
        entrance.kind = Kind.ENTRANCE;
        entrance.entered = true;
        entrance.cleared = true;
        Room arena = new Room(1, 0);
        arena.kind = Kind.BOSS;
        arena.size = 2;
        rooms.add(entrance);
        rooms.add(arena);
        buildRoom(entrance, random);
        buildRoom(arena, random);
        setDoor(entrance, arena, false);
    }

    // A random walk through the grid that never crosses itself. It can walk into a dead end, so it tries again then
    private void layOut(RandomSource random, int count) {
        for (int attempt = 0; attempt < 100; attempt++) {
            rooms.clear();
            rooms.add(new Room(0, 0));
            while (rooms.size() < count) {
                Room last = rooms.get(rooms.size() - 1);
                List<int[]> free = freeCells(last);
                if (free.isEmpty()) {
                    break;
                }
                int[] next = free.get(random.nextInt(free.size()));
                rooms.add(new Room(next[0], next[1]));
            }
            if (rooms.size() == count) {
                return;
            }
        }
        // A straight row can't fail
        rooms.clear();
        for (int i = 0; i < count; i++) {
            rooms.add(new Room(i, 0));
        }
    }

    // The cells next to the room that no room is in yet
    private List<int[]> freeCells(Room room) {
        List<int[]> free = new ArrayList<>();
        for (int[] direction : DIRECTIONS) {
            int x = room.cellX + direction[0];
            int z = room.cellZ + direction[1];
            if (allRooms().stream().noneMatch(other -> other.cellX == x && other.cellZ == z)) {
                free.add(new int[] {x, z});
            }
        }
        return free;
    }

    // Each treasure room hangs off a normal room on the way, with a door into it. If there is no space, there is one less
    private void addSideRooms(RandomSource random, int count) {
        List<Room> parents = new ArrayList<>(rooms.stream().filter(room -> room.kind == Kind.NORMAL).toList());
        while (sideRooms.size() < count && !parents.isEmpty()) {
            Room parent = parents.remove(random.nextInt(parents.size()));
            List<int[]> free = freeCells(parent);
            if (free.isEmpty()) {
                continue;
            }
            int[] cell = free.get(random.nextInt(free.size()));
            Room treasure = new Room(cell[0], cell[1]);
            treasure.kind = Kind.TREASURE;
            sideRooms.add(treasure);
            treasureParents.add(parent);
        }
    }

    private int originX(Room room) {
        return slot * SLOT_SPACING + room.cellX * CELL;
    }

    private int originZ(Room room) {
        return room.cellZ * CELL;
    }

    // A chest left from an old dungeon in the same place is emptied first, or its loot would spill out into the new one
    private void set(int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (level.getBlockEntity(pos) instanceof Container container) {
            container.clearContent();
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    // Walls, floor and ceiling of the theme, air inside, lights under the ceiling and something in some corners. The
    // whole box is written, so whatever an old dungeon left in the same place is gone
    private void buildRoom(Room room, RandomSource random) {
        int x0 = originX(room);
        int z0 = originZ(room);
        int span = span(room);
        int height = height(room);
        double center = span / 2.0 - 0.5;
        for (int dx = 0; dx < span; dx++) {
            for (int dz = 0; dz < span; dz++) {
                boolean wall = dx == 0 || dz == 0 || dx == span - 1 || dz == span - 1;
                boolean middle = Math.abs(dx - center) < 3 * room.size && Math.abs(dz - center) < 3 * room.size;
                for (int dy = 0; dy <= height; dy++) {
                    BlockState state;
                    if (dy == 0) {
                        state = middle && room.kind == Kind.BOSS ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : middle && room.kind == Kind.TREASURE ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
                                : theme.floor(random);
                    } else if (dy == height) {
                        state = theme.ceiling();
                    } else if (wall) {
                        state = theme.wall(random);
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(x0 + dx, FLOOR_Y + dy, z0 + dz, state);
                }
            }
        }
        BlockState light = (room.kind == Kind.BOSS ? Blocks.LANTERN : theme.light()).defaultBlockState().setValue(LanternBlock.HANGING, true);
        int[] lights = room.size > 1 ? new int[] {4, span / 2, span - 5} : new int[] {4, span - 5};
        for (int a : lights) {
            for (int b : lights) {
                set(x0 + a, FLOOR_Y + height - 1, z0 + b, light);
            }
        }
        for (int a : new int[] {1, span - 2}) {
            for (int b : new int[] {1, span - 2}) {
                if (random.nextFloat() < 0.5f) {
                    set(x0 + a, FLOOR_Y + theme.decorationHeight(height), z0 + b, theme.decoration());
                }
            }
        }
        if (room.kind == Kind.ENTRANCE) {
            set(x0 + 7, FLOOR_Y + 1, z0 + 7, Blocks.LODESTONE.defaultBlockState());
        } else if (room.kind == Kind.TREASURE) {
            BlockPos pos = chestPos(room);
            set(pos.getX(), pos.getY(), pos.getZ(), Blocks.CHEST.defaultBlockState());
            fillChest(pos, random);
        }
    }

    // How many blocks the room is across, walls included
    private static int span(Room room) {
        return CELL * room.size;
    }

    // The arena is taller, so the uber boss fits
    private static int height(Room room) {
        return room.size > 1 ? ARENA_HEIGHT : HEIGHT;
    }

    // Better than what monsters drop, but not as good as the boss: two items, essence and a gem
    private void fillChest(BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            return;
        }
        int itemLevel = monsterLevel + 3;
        Loot.Quality quality = Loot.Quality.dungeon(tier, 0, 0);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            items.add(Loot.create(random, Rarity.roll(random, Rarity.RARE), itemLevel, null, quality));
        }
        items.add(new ItemStack(Smithy.ESSENCE.get(), 3 + tier + random.nextInt(4)));
        items.add(Gems.random(random, monsterLevel, tier / 4));
        int index = 0;
        for (ItemStack item : items) {
            index += 1 + random.nextInt(4);
            chest.setItem(index % chest.getContainerSize(), item);
        }
    }

    // The 3 x 3 opening through both walls between two rooms next to each other
    private List<BlockPos> doorway(Room a, Room b) {
        List<BlockPos> blocks = new ArrayList<>();
        int dx = b.cellX - a.cellX;
        int dz = b.cellZ - a.cellZ;
        for (int i = 6; i <= 8; i++) {
            for (int y = 1; y <= 3; y++) {
                if (dx != 0) {
                    blocks.add(new BlockPos(originX(a) + (dx > 0 ? CELL - 1 : 0), FLOOR_Y + y, originZ(a) + i));
                    blocks.add(new BlockPos(originX(b) + (dx > 0 ? 0 : CELL - 1), FLOOR_Y + y, originZ(b) + i));
                } else {
                    blocks.add(new BlockPos(originX(a) + i, FLOOR_Y + y, originZ(a) + (dz > 0 ? CELL - 1 : 0)));
                    blocks.add(new BlockPos(originX(b) + i, FLOOR_Y + y, originZ(b) + (dz > 0 ? 0 : CELL - 1)));
                }
            }
        }
        return blocks;
    }

    // Open, or barred so you can see through. Bars are set up joined to each other, since no neighbour updates are sent
    private void setDoor(Room a, Room b, boolean barred) {
        BlockState state = Blocks.AIR.defaultBlockState();
        if (barred) {
            boolean alongZ = b.cellX != a.cellX;
            state = Blocks.IRON_BARS.defaultBlockState()
                    .setValue(IronBarsBlock.NORTH, alongZ).setValue(IronBarsBlock.SOUTH, alongZ)
                    .setValue(IronBarsBlock.EAST, !alongZ).setValue(IronBarsBlock.WEST, !alongZ);
        }
        for (BlockPos pos : doorway(a, b)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    boolean isExit(BlockPos pos) {
        Room entrance = rooms.get(0);
        return pos.equals(new BlockPos(originX(entrance) + 7, FLOOR_Y + 1, originZ(entrance) + 7));
    }

    void toEntrance(ServerPlayer player) {
        Room entrance = rooms.get(0);
        double x = originX(entrance) + 7.5;
        double z = originZ(entrance) + 3.5;
        player.teleportTo(level, x, FLOOR_Y + 1, z, 0, 0);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, FLOOR_Y + 1.5, z, 30, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, x, FLOOR_Y + 1, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    // ---- Running ----

    // Returns true when the dungeon is over
    boolean tick() {
        ticks++;
        List<ServerPlayer> inside = players();
        updateBar(inside);
        if (failed) {
            return true;
        }
        // Everyone took the exit
        if (members.isEmpty()) {
            return true;
        }
        if (homeIn >= 0) {
            return --homeIn <= 0;
        }
        // Everyone left some other way, e.g. died with no lives or logged out
        if (inside.isEmpty()) {
            return ++empty > EMPTY_TIMEOUT;
        }
        empty = 0;
        if (ticks % 10 == 0) {
            for (Room room : allRooms()) {
                if (!room.entered && inside.stream().anyMatch(player -> isIn(player, room))) {
                    enter(room, inside);
                }
            }
        }
        boolean fighting = false;
        for (Room room : allRooms()) {
            if (room.entered && !room.cleared) {
                checkMonsters(room);
                fighting |= !room.alive.isEmpty();
            }
        }
        if (has(RiftAffix.METEORS) && fighting && ticks % METEOR_INTERVAL == 0) {
            ServerPlayer target = inside.get(level.getRandom().nextInt(inside.size()));
            hazards.add(new Hazard(target.position(), ticks + HAZARD_WARNING, METEOR_RADIUS, METEOR_DAMAGE, true));
            level.playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.5f);
        }
        tickHazards(inside);
        return false;
    }

    // The members who are here and alive
    private List<ServerPlayer> players() {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level && player.isAlive() && contains(player.getX())) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean isIn(ServerPlayer player, Room room) {
        double x = player.getX() - originX(room);
        double z = player.getZ() - originZ(room);
        return x > 1 && x < span(room) - 1 && z > 1 && z < span(room) - 1;
    }

    private void enter(Room room, List<ServerPlayer> inside) {
        room.entered = true;
        RandomSource random = level.getRandom();
        // More monsters for more players, so a group has as much to do as one player alone
        int extra = (inside.size() - 1) * 2;
        double eliteChance = Math.min(0.4, 0.1 + 0.03 * (tier - 1)) * (has(RiftAffix.ELITE_ARMY) ? 2 : 1);
        switch (room.kind) {
            case NORMAL -> {
                for (int i = 0; i < 3 + tier / 2 + random.nextInt(3) + extra; i++) {
                    spawn(room, theme.monster(random), random.nextDouble() < eliteChance ? 1 : 0, inside);
                }
            }
            case SEAL -> {
                spawn(room, theme.monster(random), 2, inside);
                for (int i = 0; i < 3 + tier / 3 + extra; i++) {
                    spawn(room, theme.monster(random), random.nextDouble() < eliteChance / 2 ? 1 : 0, inside);
                }
                tell(inside, Text.tr("dungeon.seal_guard").withStyle(ChatFormatting.RED));
            }
            // One elite with one modifier and a single guard, more for a bigger group. It is a side room, so it shouldn't
            // be harder than the way to the boss
            case TREASURE -> {
                spawn(room, theme.monster(random), 1, inside);
                for (int i = 0; i < 1 + extra / 2; i++) {
                    spawn(room, theme.monster(random), 0, inside);
                }
                tell(inside, Text.tr("dungeon.treasure_room").withStyle(ChatFormatting.GOLD));
            }
            case BOSS -> {
                // Mørkets Herre is a wither skeleton, with more helpers around it
                Monster boss = spawn(room, uber ? EntityType.WITHER_SKELETON : theme.boss(random), -1, inside);
                for (int i = 0; i < (uber ? 4 : 2) + extra / 2; i++) {
                    spawn(room, theme.monster(random), 0, inside);
                }
                if (boss != null) {
                    tell(inside, Component.literal("☠ ").append(Text.tr("dungeon.boss_waits", boss.getDisplayName()))
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }
                level.playSound(null, originX(room) + span(room) / 2, FLOOR_Y + 1, originZ(room) + span(room) / 2, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE,
                        uber ? 1.5f : 0.7f, uber ? 0.6f : 1.0f);
            }
            default -> {
            }
        }
        if (room.alive.isEmpty() && room.kind != Kind.BOSS) {
            cleared(room, inside);
        }
    }

    // elite is how many elite modifiers it gets, 0 for a normal monster and -1 for the boss
    private Monster spawn(Room room, EntityType<? extends Monster> type, int elite, List<ServerPlayer> inside) {
        Monster monster = type.create(level);
        if (monster == null) {
            return null;
        }
        RandomSource random = level.getRandom();
        // Anywhere in the room but by the walls and the treasure chest, the boss in the middle
        double center = span(room) / 2.0;
        double x = originX(room) + (elite < 0 ? center : 3 + random.nextInt(span(room) - 6) + 0.5);
        double z = originZ(room) + (elite < 0 ? center : 3 + random.nextInt(span(room) - 6) + 0.5);
        if (room.kind == Kind.TREASURE && Math.abs(x - originX(room) - 7.5) < 1 && Math.abs(z - originZ(room) - 7.5) < 1) {
            x += 2;
        }
        monster.moveTo(x, FLOOR_Y + 1, z, random.nextFloat() * 360, 0);
        // Gives it its normal gear, like a bow for skeletons
        monster.finalizeSpawn(level, level.getCurrentDifficultyAt(monster.blockPosition()), MobSpawnType.EVENT, null);
        Elites.markChecked(monster);
        if (Config.MONSTER_LEVELS.get()) {
            MonsterLevels.apply(monster, elite < 0 ? monsterLevel + 1 : monsterLevel);
        }
        if (elite < 0) {
            if (uber) {
                Bosses.makeUberBoss(monster);
            } else {
                Bosses.makeBoss(monster);
            }
            // The boss can take 30% of your health in one hit at tier I, and 4% more per tier
            Bosses.setMaxHit(monster, 0.3f + 0.04f * (tier - 1));
            bossId = monster.getUUID();
        } else if (elite > 0) {
            Elites.makeElite(monster, elite);
        }
        // On top of the level, each tier above I gives the monsters 15% more health and 6% more damage. The boss has four
        // times the health of a normal boss at tier I and 60% more per tier, and can only lose a little of it in one hit,
        // see Dungeons.onBossDamage. Armored adds 40%. The extra health gives no extra XP, see Dungeons.xpScale
        // The uber boss has twice the health of a tier X boss
        double bossHealth = (4 + 0.6 * (tier - 1)) * (uber ? 2 : 1) - 1;
        double health = (elite < 0 ? bossHealth : 0.15 * (tier - 1)) + (has(RiftAffix.ARMORED) ? 0.4 : 0);
        if (health > 0) {
            boost(monster, TIER_BOOST, Attributes.MAX_HEALTH, health);
            monster.setHealth(monster.getMaxHealth());
        }
        if (tier > 1) {
            boost(monster, TIER_BOOST, Attributes.ATTACK_DAMAGE, 0.06 * (tier - 1));
        }
        if (has(RiftAffix.SWIFT)) {
            boost(monster, AFFIX_BOOST, Attributes.MOVEMENT_SPEED, 0.25);
        }
        monster.getPersistentData().putBoolean(Dungeons.MONSTER_TAG, true);
        monster.setPersistenceRequired();
        if (!inside.isEmpty()) {
            monster.setTarget(inside.get(random.nextInt(inside.size())));
        }
        // In the list before it joins the world, or Dungeons.onJoinLevel would think it is left over
        room.alive.add(monster.getUUID());
        level.addFreshEntity(monster);
        level.sendParticles(ParticleTypes.SOUL, x, FLOOR_Y + 1.5, z, 12, 0.3, 0.6, 0.3, 0.02);
        return monster;
    }

    private static void boost(Monster monster, ResourceLocation id, Holder<Attribute> attribute, double amount) {
        AttributeInstance instance = monster.getAttribute(attribute);
        if (instance != null) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // Monsters that are gone without dying count as dead too, so a room can't get stuck. Only once the monsters in
    // their chunk are loaded, since they are just not there yet before that. The boss comes back instead
    private void checkMonsters(Room room) {
        room.alive.removeIf(id -> {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                return false;
            }
            if (entity == null && !died.contains(id)) {
                ChunkPos chunk = new ChunkPos(new BlockPos(originX(room) + span(room) / 2, FLOOR_Y, originZ(room) + span(room) / 2));
                if (!level.areEntitiesLoaded(chunk.toLong())) {
                    return false;
                }
                if (id.equals(bossId)) {
                    bossId = null;
                    room.entered = false;
                    return true;
                }
            }
            return true;
        });
        if (room.alive.isEmpty() && room.entered && room.kind != Kind.BOSS) {
            cleared(room, players());
        }
    }

    private void cleared(Room room, List<ServerPlayer> inside) {
        room.cleared = true;
        if (room.kind == Kind.SEAL) {
            setDoor(room, rooms.get(rooms.size() - 1), false);
            tell(inside, Text.tr("dungeon.seal_broken").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            level.playSound(null, originX(room) + 8, FLOOR_Y + 1, originZ(room) + 8, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
            level.playSound(null, originX(room) + 8, FLOOR_Y + 1, originZ(room) + 8, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
        } else {
            String text = room.kind == Kind.TREASURE ? "dungeon.treasure_yours" : "dungeon.room_cleared";
            for (ServerPlayer player : inside) {
                player.displayClientMessage(Text.tr(text).withStyle(room.kind == Kind.TREASURE ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
            }
        }
    }

    // Explosive: a dungeon monster that dies blows up a moment later
    void monsterDied(Entity monster) {
        died.add(monster.getUUID());
        if (has(RiftAffix.EXPLOSIVE)) {
            hazards.add(new Hazard(monster.position(), ticks + HAZARD_WARNING, EXPLOSION_RADIUS, EXPLOSION_DAMAGE, false));
        }
        if (monster.getUUID().equals(bossId) && homeIn < 0) {
            complete(monster.position());
        }
    }

    // Each hazard shows a warning where it will go off, then hurts the players who are still too close
    private void tickHazards(List<ServerPlayer> inside) {
        Iterator<Hazard> pending = hazards.iterator();
        while (pending.hasNext()) {
            Hazard hazard = pending.next();
            Vec3 pos = hazard.pos;
            if (ticks < hazard.at) {
                if (ticks % 4 == 0) {
                    for (int i = 0; i < 16; i++) {
                        double angle = i * Math.PI * 2 / 16;
                        level.sendParticles(hazard.meteor ? ParticleTypes.FLAME : ParticleTypes.SMOKE, pos.x + Math.cos(angle) * hazard.radius,
                                pos.y + 0.1, pos.z + Math.sin(angle) * hazard.radius, 1, 0, 0, 0, 0);
                    }
                    if (hazard.meteor) {
                        // The meteor coming down from the ceiling
                        double height = Math.min(HEIGHT - 1, (hazard.at - ticks) * 0.2);
                        level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + height, pos.z, 3, 0.2, 0.2, 0.2, 0);
                    }
                }
                continue;
            }
            pending.remove();
            level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.5, pos.z, 3, hazard.radius / 3, 0.3, hazard.radius / 3, 0);
            level.playSound(null, BlockPos.containing(pos), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, hazard.meteor ? 0.7f : 1.2f);
            for (ServerPlayer player : inside) {
                if (player.position().distanceTo(pos) <= hazard.radius) {
                    player.hurt(level.damageSources().explosion(null, null), player.getMaxHealth() * hazard.damage);
                }
            }
        }
    }

    // Everyone loses one of the shared lives. When they are used up the dungeon is lost
    void playerDied(ServerPlayer player) {
        if (!isOpen()) {
            return;
        }
        lives--;
        List<ServerPlayer> everyone = online();
        if (lives <= 0) {
            failed = true;
            tell(everyone, Text.tr("dungeon.lost").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        } else {
            tell(everyone, Component.literal(player.getGameProfile().getName() + " faldt - " + lives + " liv tilbage")
                    .withStyle(ChatFormatting.RED));
        }
    }

    // A player who takes the exit is out of the dungeon. The others can go on
    void leave(ServerPlayer player) {
        members.remove(player.getUUID());
        bar.removePlayer(player);
        Dungeons.sendBack(player);
        player.displayClientMessage(Text.tr("dungeon.left").withStyle(ChatFormatting.GRAY), true);
    }

    private List<ServerPlayer> online() {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private static void tell(List<ServerPlayer> players, Component message) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(message);
        }
    }

    // Loot for each member where the boss died, a key for the next tier each, and XP. The loot is better than what
    // monsters drop: a legendary with a greater bonus for everyone, items five levels above the monsters, bonuses that
    // roll better and stronger the higher the tier (see Loot.Quality.dungeon), an extra bonus on each item, and only the
    // best materials from item level 20 (see Loot.createFor). Higher tiers give more items and more greater bonuses on
    // the legendary, and each affix on the key gives an item more and 25% more essence, XP and chance of a mythic
    private void complete(Vec3 at) {
        homeIn = HOME_DELAY;
        RandomSource random = level.getRandom();
        if (uber) {
            completeUber(at, random);
            return;
        }
        Rarity minimum = tier >= 5 ? Rarity.LEGENDARY : Rarity.RARE;
        int itemLevel = monsterLevel + 5;
        double affixBonus = 1 + RiftAffix.REWARD_PER_AFFIX * affixes.size();
        // Every reward has a bonus more than its rarity gives, and the legendary two from tier VI
        Loot.Quality quality = Loot.Quality.dungeon(tier, 0, 1);
        int nextTier = Math.min(Dungeons.MAX_TIER, tier + 1);
        List<ServerPlayer> everyone = online();
        for (ServerPlayer player : everyone) {
            List<ItemStack> rewards = new ArrayList<>();
            PlayerClass playerClass = Progression.playerClass(player);
            // The legendary has at least one greater bonus, sometimes two or three, more often at higher tiers
            int sure = 1 + (random.nextDouble() < 0.1 + 0.03 * (tier - 1) ? 1 : 0) + (random.nextDouble() < 0.02 + 0.01 * (tier - 1) ? 1 : 0);
            rewards.add(Loot.createFor(player, random, Rarity.LEGENDARY, itemLevel, Loot.Quality.dungeon(tier, sure, tier >= 6 ? 2 : 1)));
            for (int i = 0; i < 1 + (tier - 1) / 3 + affixes.size(); i++) {
                rewards.add(Loot.createFor(player, random, Rarity.roll(random, minimum), itemLevel, quality));
            }
            if (random.nextDouble() < Config.MYTHIC_CHANCE_RIFT.get() * tier * affixBonus) {
                ItemStack mythic = Loot.createMythic(random, itemLevel, playerClass);
                rewards.add(mythic);
                Loot.announceMythic(player, mythic);
            }
            rewards.addAll(Smithy.essence((int) Math.round((5 + tier * 3 + random.nextInt(5)) * affixBonus)));
            for (int i = 0; i < 1 + tier / 3; i++) {
                rewards.add(Gems.random(random, monsterLevel, tier / 4));
            }
            rewards.add(Dungeons.key(nextTier, random));
            // One of the materials for Mørkets Kald, see Uber. More likely at higher tiers
            if (tier >= 3 && random.nextDouble() < 0.25 + 0.05 * tier) {
                rewards.add(Uber.soulShard());
            }
            // A glyph for the paragon board, from 13% at tier I to 40% at tier X
            if (random.nextDouble() < GLYPH_CHANCE + GLYPH_CHANCE_PER_TIER * tier) {
                rewards.add(Glyphs.random(random));
            }
            rewards.add(TownPortal.scroll());
            for (ItemStack reward : rewards) {
                level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, reward));
            }
            Progression.addXp(player, (int) ((150 + 25 * monsterLevel) * (1 + 0.25 * (tier - 1)) * affixBonus));
            Glyphs.dungeonCleared(player, tier);
            player.sendSystemMessage(Text.tr("dungeon.cleared", theme.displayName(), Rifts.roman(tier)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Text.tr("dungeon.cleared.loot", HOME_DELAY / 20, Rifts.roman(nextTier)).withStyle(style -> style.withBold(false).withColor(ChatFormatting.YELLOW))));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 80, 0.5, 1, 0.5, 0.4);
    }

    // The best rewards in the game: two legendaries with more and greater bonuses than a tier X dungeon gives, a good
    // chance of a mythic, plenty of essence, better gems and always a glyph
    private void completeUber(Vec3 at, RandomSource random) {
        int itemLevel = monsterLevel + 10;
        for (ServerPlayer player : online()) {
            List<ItemStack> rewards = new ArrayList<>();
            PlayerClass playerClass = Progression.playerClass(player);
            for (int i = 0; i < 2; i++) {
                rewards.add(Loot.createFor(player, random, Rarity.LEGENDARY, itemLevel, Loot.Quality.dungeon(Dungeons.MAX_TIER, 2, 2)));
            }
            rewards.add(Loot.createFor(player, random, Rarity.LEGENDARY, itemLevel, Loot.Quality.dungeon(Dungeons.MAX_TIER, 0, 1)));
            if (random.nextDouble() < UBER_MYTHIC_CHANCE) {
                ItemStack mythic = Loot.createMythic(random, itemLevel, playerClass);
                rewards.add(mythic);
                Loot.announceMythic(player, mythic);
            }
            rewards.addAll(Smithy.essence(80 + random.nextInt(41)));
            for (int i = 0; i < 3; i++) {
                rewards.add(Gems.random(random, monsterLevel, 3));
            }
            rewards.add(Glyphs.random(random));
            rewards.add(TownPortal.scroll());
            for (ItemStack reward : rewards) {
                level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, reward));
            }
            Progression.addXp(player, 3000 + 100 * monsterLevel);
            Glyphs.dungeonCleared(player, Dungeons.MAX_TIER);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Text.tr("dungeon.uber.loot", HOME_DELAY / 20).withStyle(ChatFormatting.YELLOW)));
            player.connection.send(new ClientboundSetTitleTextPacket(Text.tr("dungeon.uber.fallen").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 200, 1, 2, 1, 0.5);
    }

    // "Den Frosne Krypt III - rum 3/6 - 2 liv" for the members who are here. Treasure rooms don't count
    private void updateBar(List<ServerPlayer> inside) {
        long cleared = rooms.stream().filter(room -> room.cleared).count();
        Component name = uber ? Text.tr("boss.uber") : Text.tr("dungeon.name", theme.displayName(), Rifts.roman(tier));
        Component text = homeIn >= 0 ? Text.tr("dungeon.bar.home", name, (homeIn + 19) / 20)
                : uber ? Text.tr("dungeon.bar.uber", name, lives)
                : Text.tr("dungeon.bar.rooms", name, Math.min(rooms.size(), cleared + 1), rooms.size(), lives);
        bar.setName(text.copy().withStyle(ChatFormatting.RED));
        bar.setProgress(homeIn >= 0 ? homeIn / (float) HOME_DELAY : cleared / (float) rooms.size());
        for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
            if (!inside.contains(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : inside) {
            bar.addPlayer(player);
        }
    }

    // Everyone still here goes home, and the monsters go away. The ones in chunks that aren't loaded are removed when
    // they are loaded again, see Dungeons.onJoinLevel
    void close() {
        bar.removeAllPlayers();
        for (ServerPlayer player : online()) {
            // One who is dead respawns at home by themselves
            if (player.isAlive()) {
                Dungeons.sendBack(player);
            }
        }
        for (Room room : allRooms()) {
            discard(room.alive);
        }
        discard(extras);
    }

    private void discard(Set<UUID> monsters) {
        for (UUID id : monsters) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        monsters.clear();
    }
}
