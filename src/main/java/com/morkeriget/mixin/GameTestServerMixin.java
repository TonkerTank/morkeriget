package com.morkeriget.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

// The game test server only makes the dimensions of a flat world, so the dungeon dimension from the mod's data pack
// would be missing in the tests. This bakes them with the data pack's dimensions, like a normal new world. The class
// is only loaded by ./gradlew runGameTestServer, so it changes nothing in the game
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static WorldDimensions.Complete morkeriget$withDataPackDimensions(WorldDimensions dimensions, Registry<LevelStem> none,
                                                                              LevelSettings settings, WorldLoader.DataLoadContext context) {
        return dimensions.bake(context.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
    }
}
