package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldGenRegion.class)
abstract class WorldGenRegionSeedMixin {
    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long localSeed(ServerLevel level) {
        var population =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                        .SpawnConstructorScope.current(level);
        if (population != null) return population.seed();
        var binding = SeedLocalRegionScope.current(level);
        return binding == null ? level.getSeed() : binding.context().seed();
    }

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerChunkCache;randomState()Lnet/minecraft/world/level/levelgen/RandomState;"))
    private RandomState localRandomState(ServerChunkCache chunks) {
        var population =
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                        .SpawnConstructorScope.current();
        if (population != null) {
            if (population.level().getChunkSource() != chunks)
                throw new IllegalStateException("Foreign population chunk source");
            return population.random();
        }
        var binding = SeedLocalRegionScope.current(chunks);
        return binding == null ? chunks.randomState() : binding.context().randomState();
    }

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/StructureManager;forWorldGenRegion(Lnet/minecraft/server/level/WorldGenRegion;)Lnet/minecraft/world/level/StructureManager;"))
    private StructureManager localStructures(StructureManager physical, WorldGenRegion region) {
        var binding = SeedLocalRegionScope.current();
        return binding == null ? physical.forWorldGenRegion(region) : binding.structures();
    }
}
