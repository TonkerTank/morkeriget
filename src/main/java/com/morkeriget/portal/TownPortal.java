package com.morkeriget.portal;

import com.morkeriget.Morkeriget;
import com.morkeriget.rift.Rifts;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Town portal scrolls: reading one takes you home to your bed (or the world spawn) and remembers where you were.
// Reading one again at home takes you back there for free
public final class TownPortal {
    private static final String RETURN_TAG = "morkeriget_portal_return";
    // How close to home you have to be for the scroll to take you back instead
    private static final double HOME_RANGE = 16.0;

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Morkeriget.MODID);
    public static final DeferredItem<Item> SCROLL = ITEMS.registerItem("town_portal", TownPortalItem::new, new Item.Properties().stacksTo(16));

    private TownPortal() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(SCROLL);
            }
        });
    }

    // Called when the player has finished reading the scroll. Returns true if a scroll was used up
    static boolean use(ServerPlayer player) {
        if (Rifts.isInRift(player)) {
            player.displayClientMessage(Component.literal("Du kan ikke bruge en portal i en rift"), true);
            return false;
        }
        ServerLevel homeLevel = homeLevel(player);
        Vec3 home = homePosition(player, homeLevel);
        CompoundTag data = player.getPersistentData();

        // Back to where you came from, if you are at home and have been away
        if (data.contains(RETURN_TAG) && player.level() == homeLevel && player.position().distanceTo(home) <= HOME_RANGE) {
            CompoundTag back = data.getCompound(RETURN_TAG);
            ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(back.getString("dimension"))));
            if (level != null) {
                data.remove(RETURN_TAG);
                teleport(player, level, new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z")));
                player.displayClientMessage(Component.literal("Portalen fører dig tilbage").withStyle(ChatFormatting.AQUA), true);
                return false;
            }
        }

        CompoundTag back = new CompoundTag();
        back.putString("dimension", player.level().dimension().location().toString());
        back.putDouble("x", player.getX());
        back.putDouble("y", player.getY());
        back.putDouble("z", player.getZ());
        data.put(RETURN_TAG, back);
        teleport(player, homeLevel, home);
        player.displayClientMessage(Component.literal("Portalen fører dig hjem. Læs en rulle igen for at vende tilbage")
                .withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    // Where your bed or respawn anchor is, or else the world spawn
    private static ServerLevel homeLevel(ServerPlayer player) {
        ServerLevel level = player.getRespawnPosition() != null ? player.server.getLevel(player.getRespawnDimension()) : null;
        return level != null ? level : player.server.overworld();
    }

    private static Vec3 homePosition(ServerPlayer player, ServerLevel level) {
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && level.dimension() == player.getRespawnDimension()) {
            // On top of the bed
            return new Vec3(respawn.getX() + 0.5, respawn.getY() + 0.6, respawn.getZ() + 0.5);
        }
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, level.getSharedSpawnPos());
        return Vec3.atBottomCenterOf(spawn);
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 to) {
        portalEffect(player.serverLevel(), player.position());
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        portalEffect(level, to);
    }

    private static void portalEffect(Level level, Vec3 at) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 80, 0.4, 0.8, 0.4, 0.5);
            serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
    }

    public static ItemStack scroll() {
        return new ItemStack(SCROLL.get());
    }
}
