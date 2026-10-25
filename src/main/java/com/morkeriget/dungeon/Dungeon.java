package com.morkeriget.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.morkeriget.Config;
import com.morkeriget.elite.Bosses;
import com.morkeriget.elite.Elites;
import com.morkeriget.elite.MonsterLevels;
import com.morkeriget.loot.Loot;
import com.morkeriget.loot.Rarity;
import com.morkeriget.portal.TownPortal;
import com.morkeriget.progress.PlayerClass;
import com.morkeriget.progress.Progression;
import com.morkeriget.rift.Rifts;
import com.morkeriget.smithy.Gems;
import com.morkeriget.smithy.Smithy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// One dungeon and the group in it. The rooms are cells in a grid, each a box of stone with a door to the next, laid out
// as a winding row from the entrance to the boss. See Dungeons
final class Dungeon {
    static final int LIVES = 3;
    // Each room is a CELL x CELL box: walls on the outside, floor at FLOOR_Y and the ceiling HEIGHT above it
    private static final int CELL = 16;
    private static final int HEIGHT = 6;
    private static final int FLOOR_Y = 64;
    // How far apart the dungeons are. The row of rooms can wind at most this far from its entrance
    private static final int SLOT_SPACING = 512;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    // 20 ticks is one second. Time to pick up the loot after the boss, and how long a dungeon waits for a group that
    // has left without using the exit
    private static final int HOME_DELAY = 30 * 20;
    private static final int EMPTY_TIMEOUT = 60 * 20;
    private static final List<EntityType<? extends Monster>> MONSTERS = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK,
            EntityType.VINDICATOR, EntityType.STRAY, EntityType.WITCH);
    private static final List<EntityType<? extends Monster>> BOSSES = List.of(
            EntityType.VINDICATOR, EntityType.WITHER_SKELETON, EntityType.ZOMBIE, EntityType.HUSK);
    private static final List<Block> WALLS = List.of(
            Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE);
    private static final List<Block> FLOORS = List.of(
            Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_DEEPSLATE);

    private enum Kind {
        ENTRANCE, NORMAL, SEAL, BOSS
    }

    private static final class Room {
        final int cellX;
        final int cellZ;
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

    final ServerLevel level;
    final int slot;
    private final int tier;
    private final int monsterLevel;
    final Set<UUID> members = new HashSet<>();
    private final List<Room> rooms = new ArrayList<>();
    // The monsters that really died, as opposed to just being gone
    private final Set<UUID> died = new HashSet<>();
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Dungeon"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private UUID bossId;
    private int lives = LIVES;
    private boolean failed;
    // Counts down to sending everyone home once the boss is dead, -1 before that
    private int homeIn = -1;
    private int empty;
    private int ticks;

    Dungeon(ServerLevel level, int slot, int tier, int monsterLevel, List<ServerPlayer> party) {
        this.level = level;
        this.slot = slot;
        this.tier = tier;
        this.monsterLevel = monsterLevel;
        for (ServerPlayer player : party) {
            members.add(player.getUUID());
        }
    }

    boolean owns(UUID monster) {
        return monster.equals(bossId) || rooms.stream().anyMatch(room -> room.alive.contains(monster));
    }

    // Still going, so a player who dies can come back in
    boolean isOpen() {
        return !failed && homeIn < 0;
    }

    // ---- Building ----

    // One more room for every second tier, from 5 at tier I to 9 at tier X
    void build(RandomSource random) {
        int count = 5 + (tier - 1) / 2;
        layOut(random, count);
        rooms.get(0).kind = Kind.ENTRANCE;
        rooms.get(count - 2).kind = Kind.SEAL;
        rooms.get(count - 1).kind = Kind.BOSS;
        for (Room room : rooms) {
            buildRoom(room, random);
        }
        for (int i = 1; i < rooms.size(); i++) {
            setDoor(rooms.get(i - 1), rooms.get(i), rooms.get(i).kind == Kind.BOSS);
        }
        Room entrance = rooms.get(0);
        entrance.entered = true;
        entrance.cleared = true;
    }

    // A random walk through the grid that never crosses itself. It can walk into a dead end, so it tries again then
    private void layOut(RandomSource random, int count) {
        for (int attempt = 0; attempt < 100; attempt++) {
            rooms.clear();
            rooms.add(new Room(0, 0));
            while (rooms.size() < count) {
                Room last = rooms.get(rooms.size() - 1);
                List<int[]> free = new ArrayList<>();
                for (int[] direction : DIRECTIONS) {
                    int x = last.cellX + direction[0];
                    int z = last.cellZ + direction[1];
                    if (rooms.stream().noneMatch(room -> room.cellX == x && room.cellZ == z)) {
                        free.add(new int[] {x, z});
                    }
                }
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

    private int originX(Room room) {
        return slot * SLOT_SPACING + room.cellX * CELL;
    }

    private int originZ(Room room) {
        return room.cellZ * CELL;
    }

    private void set(int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    // Stone all round, air inside, lanterns under the ceiling and cobwebs in some corners. The whole box is written,
    // so whatever an old dungeon left in the same place is gone
    private void buildRoom(Room room, RandomSource random) {
        int x0 = originX(room);
        int z0 = originZ(room);
        BlockState ceiling = Blocks.DEEPSLATE_TILES.defaultBlockState();
        for (int dx = 0; dx < CELL; dx++) {
            for (int dz = 0; dz < CELL; dz++) {
                boolean wall = dx == 0 || dz == 0 || dx == CELL - 1 || dz == CELL - 1;
                for (int dy = 0; dy <= HEIGHT; dy++) {
                    BlockState state;
                    if (dy == 0) {
                        state = room.kind == Kind.BOSS && Math.abs(dx - 7.5) < 3 && Math.abs(dz - 7.5) < 3
                                ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                : FLOORS.get(random.nextInt(FLOORS.size())).defaultBlockState();
                    } else if (dy == HEIGHT) {
                        state = ceiling;
                    } else if (wall) {
                        state = WALLS.get(random.nextInt(WALLS.size())).defaultBlockState();
                    } else {
                        state = Blocks.AIR.defaultBlockState();
                    }
                    set(x0 + dx, FLOOR_Y + dy, z0 + dz, state);
                }
            }
        }
        BlockState lantern = (room.kind == Kind.BOSS ? Blocks.LANTERN : Blocks.SOUL_LANTERN).defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int[] spot : new int[][] {{4, 4}, {11, 4}, {4, 11}, {11, 11}}) {
            set(x0 + spot[0], FLOOR_Y + HEIGHT - 1, z0 + spot[1], lantern);
        }
        for (int[] corner : new int[][] {{1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
            if (random.nextFloat() < 0.4f) {
                set(x0 + corner[0], FLOOR_Y + HEIGHT - 1, z0 + corner[1], Blocks.COBWEB.defaultBlockState());
            }
        }
        if (room.kind == Kind.ENTRANCE) {
            set(x0 + 7, FLOOR_Y + 1, z0 + 7, Blocks.LODESTONE.defaultBlockState());
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
            for (Room room : rooms) {
                if (!room.entered && inside.stream().anyMatch(player -> isIn(player, room))) {
                    enter(room, inside);
                }
            }
        }
        for (Room room : rooms) {
            if (room.entered && !room.cleared) {
                checkMonsters(room);
            }
        }
        return false;
    }

    // The members who are here and alive
    private List<ServerPlayer> players() {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level && player.isAlive()
                    && Math.abs(player.getX() - slot * SLOT_SPACING) < SLOT_SPACING / 2.0) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean isIn(ServerPlayer player, Room room) {
        double x = player.getX() - originX(room);
        double z = player.getZ() - originZ(room);
        return x > 1 && x < CELL - 1 && z > 1 && z < CELL - 1;
    }

    private void enter(Room room, List<ServerPlayer> inside) {
        room.entered = true;
        RandomSource random = level.getRandom();
        // More monsters for more players, so a group has as much to do as one player alone
        int extra = (inside.size() - 1) * 2;
        switch (room.kind) {
            case NORMAL -> {
                double eliteChance = Math.min(0.4, 0.1 + 0.03 * (tier - 1));
                for (int i = 0; i < 3 + tier / 2 + random.nextInt(3) + extra; i++) {
                    spawn(room, randomMonster(random), random.nextDouble() < eliteChance ? 1 : 0, inside);
                }
            }
            case SEAL -> {
                spawn(room, randomMonster(random), 2, inside);
                for (int i = 0; i < 3 + tier / 3 + extra; i++) {
                    spawn(room, randomMonster(random), 0, inside);
                }
                tell(inside, Component.literal("Seglets vogter! Dræb alt i rummet for at bryde seglet").withStyle(ChatFormatting.RED));
            }
            case BOSS -> {
                Monster boss = spawn(room, BOSSES.get(random.nextInt(BOSSES.size())), -1, inside);
                for (int i = 0; i < 2 + extra / 2; i++) {
                    spawn(room, randomMonster(random), 0, inside);
                }
                if (boss != null) {
                    tell(inside, Component.literal("☠ ").append(boss.getDisplayName()).append(" venter på jer!")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }
                level.playSound(null, originX(room) + 8, FLOOR_Y + 1, originZ(room) + 8, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.7f, 1.0f);
            }
            default -> {
            }
        }
        if (room.alive.isEmpty() && room.kind != Kind.BOSS) {
            cleared(room, inside);
        }
    }

    private static EntityType<? extends Monster> randomMonster(RandomSource random) {
        return MONSTERS.get(random.nextInt(MONSTERS.size()));
    }

    // elite is how many elite modifiers it gets, 0 for a normal monster and -1 for the boss
    private Monster spawn(Room room, EntityType<? extends Monster> type, int elite, List<ServerPlayer> inside) {
        Monster monster = type.create(level);
        if (monster == null) {
            return null;
        }
        RandomSource random = level.getRandom();
        // Anywhere in the room but by the walls, the boss in the middle
        double x = originX(room) + (elite < 0 ? 7.5 : 3 + random.nextInt(10) + 0.5);
        double z = originZ(room) + (elite < 0 ? 7.5 : 3 + random.nextInt(10) + 0.5);
        monster.moveTo(x, FLOOR_Y + 1, z, random.nextFloat() * 360, 0);
        // Gives it its normal gear, like a bow for skeletons
        monster.finalizeSpawn(level, level.getCurrentDifficultyAt(monster.blockPosition()), MobSpawnType.EVENT, null);
        Elites.markChecked(monster);
        if (Config.MONSTER_LEVELS.get()) {
            MonsterLevels.apply(monster, elite < 0 ? monsterLevel + 1 : monsterLevel);
        }
        if (elite < 0) {
            Bosses.makeBoss(monster);
            // The boss can take 30% of your health in one hit at tier I, and 4% more per tier
            Bosses.setMaxHit(monster, 0.3f + 0.04f * (tier - 1));
            bossId = monster.getUUID();
        } else if (elite > 0) {
            Elites.makeElite(monster, elite);
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

    // Monsters that are gone without dying count as dead too, so a room can't get stuck. Only once the monsters in
    // their chunk are loaded, since they are just not there yet before that. The boss comes back instead
    private void checkMonsters(Room room) {
        room.alive.removeIf(id -> {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                return false;
            }
            if (entity == null && !died.contains(id)) {
                ChunkPos chunk = new ChunkPos(new BlockPos(originX(room) + 8, FLOOR_Y, originZ(room) + 8));
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
            tell(inside, Component.literal("Seglet er brudt! Bossen venter i næste rum").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            level.playSound(null, originX(room) + 8, FLOOR_Y + 1, originZ(room) + 8, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
            level.playSound(null, originX(room) + 8, FLOOR_Y + 1, originZ(room) + 8, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
        } else {
            for (ServerPlayer player : inside) {
                player.displayClientMessage(Component.literal("Rummet er ryddet").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    void monsterDied(Entity monster) {
        died.add(monster.getUUID());
        if (monster.getUUID().equals(bossId) && homeIn < 0) {
            complete(monster.position());
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
            tell(everyone, Component.literal("I har ikke flere liv - dungeonet er tabt").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
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
        player.displayClientMessage(Component.literal("Du forlod dungeonet").withStyle(ChatFormatting.GRAY), true);
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
    // always roll in the top half, more so at higher tiers (from 50% at tier I to the top 5% at tier X), and a much
    // better chance of greater bonuses
    private void complete(Vec3 at) {
        homeIn = HOME_DELAY;
        RandomSource random = level.getRandom();
        Rarity minimum = tier >= 5 ? Rarity.LEGENDARY : Rarity.RARE;
        int itemLevel = monsterLevel + 5;
        double floor = 0.5 + 0.05 * (tier - 1);
        double greaterChance = 0.15 + 0.03 * (tier - 1);
        Loot.Quality sureGreater = new Loot.Quality(floor, greaterChance, 1);
        Loot.Quality quality = new Loot.Quality(floor, greaterChance, 0);
        int nextTier = Math.min(Dungeons.MAX_TIER, tier + 1);
        List<ServerPlayer> everyone = online();
        for (ServerPlayer player : everyone) {
            List<ItemStack> rewards = new ArrayList<>();
            PlayerClass playerClass = Progression.playerClass(player);
            rewards.add(Loot.create(random, Rarity.LEGENDARY, itemLevel, playerClass, sureGreater));
            for (int i = 0; i < 1 + (tier - 1) / 3; i++) {
                rewards.add(Loot.create(random, Rarity.roll(random, minimum), itemLevel, playerClass, quality));
            }
            if (random.nextDouble() < Config.MYTHIC_CHANCE_RIFT.get() * tier) {
                ItemStack mythic = Loot.createMythic(random, itemLevel, playerClass);
                rewards.add(mythic);
                Loot.announceMythic(player, mythic);
            }
            rewards.add(new ItemStack(Smithy.ESSENCE.get(), 5 + tier * 3 + random.nextInt(5)));
            for (int i = 0; i < 1 + tier / 3; i++) {
                rewards.add(Gems.random(random, monsterLevel, tier / 4));
            }
            rewards.add(Dungeons.key(nextTier));
            rewards.add(TownPortal.scroll());
            for (ItemStack reward : rewards) {
                level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, reward));
            }
            Progression.addXp(player, (int) ((150 + 25 * monsterLevel) * (1 + 0.25 * (tier - 1))));
            player.sendSystemMessage(Component.literal("Dungeon " + Rifts.roman(tier) + " er klaret! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("Saml jeres loot - I sendes hjem om " + HOME_DELAY / 20 + " sekunder. Du fik også en nøgle "
                            + Rifts.roman(nextTier) + ".").withStyle(ChatFormatting.YELLOW)));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 80, 0.5, 1, 0.5, 0.4);
    }

    // "Dungeon III - rum 3/6 - 2 liv" for the members who are here
    private void updateBar(List<ServerPlayer> inside) {
        long cleared = rooms.stream().filter(room -> room.cleared).count();
        String text = homeIn >= 0 ? "Dungeon " + Rifts.roman(tier) + " klaret - hjem om " + (homeIn + 19) / 20 + " sek."
                : "Dungeon " + Rifts.roman(tier) + " - rum " + Math.min(rooms.size(), cleared + 1) + "/" + rooms.size() + " - " + lives + " liv";
        bar.setName(Component.literal(text).withStyle(ChatFormatting.RED));
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

    // Everyone still here goes home, and the monsters go away
    void close() {
        bar.removeAllPlayers();
        for (ServerPlayer player : online()) {
            // One who is dead respawns at home by themselves
            if (player.isAlive()) {
                Dungeons.sendBack(player);
            }
        }
        for (Room room : rooms) {
            for (UUID id : room.alive) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
            room.alive.clear();
        }
    }
}
