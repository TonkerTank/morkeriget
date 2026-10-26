package com.morkeriget.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

// Players for the game tests. Like GameTestHelper.makeMockServerPlayerInLevel, but in survival so they can be hurt and
// die, and with a connection that takes the mod's own packets. They have no client, so nothing moves them or ticks
// them the way a connection would: tests call tick themselves where it matters
public final class TestPlayers {
    private TestPlayers() {
    }

    public static ServerPlayer survivor(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        moveTo(player, level, helper.absoluteVec(Vec3.atBottomCenterOf(pos)));
        return player;
    }

    public static void moveTo(ServerPlayer player, ServerLevel level, Vec3 to) {
        player.teleportTo(level, to.x, to.y, to.z, 0, 0);
        arrived(player);
    }

    // What a real client answers after a teleport, e.g. into a dungeon. Until then the player can't be hurt, and the chunk
    // map doesn't know where they are
    public static void arrived(ServerPlayer player) {
        player.hasChangedDimension();
        player.serverLevel().getChunkSource().move(player);
    }

    // What the connection does every tick for a real player, e.g. the player tick events
    public static void tick(ServerPlayer player) {
        player.doTick();
    }

    public static void remove(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }
}
