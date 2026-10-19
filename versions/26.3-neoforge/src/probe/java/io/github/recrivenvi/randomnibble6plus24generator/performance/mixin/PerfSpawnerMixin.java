package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalSpawner;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MosaicPhysicalSpawner.class, remap = false)
abstract class PerfSpawnerMixin {
    @WrapMethod(method = "biomeInputs", require = 0)
    private static java.util.Map<net.minecraft.world.level.ChunkPos, ChunkAccess> inputs(
            net.minecraft.server.level.ServerLevel level,
            ChunkAccess chunk,
            long seed,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext
                    runtime,
            Operation<java.util.Map<net.minecraft.world.level.ChunkPos, ChunkAccess>> original) {
        boolean carried =
                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                                .MosaicSpawnBiomeCarrier)
                                        chunk)
                                .randomnibble6plus24generator$spawnBiomes()
                        != null;
        PerfTrace.count(carried ? "spawn.carriedHits" : "spawn.coldRecoveries", 1);
        long start = System.nanoTime();
        try {
            return original.call(level, chunk, seed, runtime);
        } finally {
            PerfTrace.count("spawn.prepare.biomeInputsNanos", System.nanoTime() - start);
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "generateSpawn",
            at =
                    @org.spongepowered.asm.mixin.injection.At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)Lnet/minecraft/world/level/levelgen/RandomState;"))
    private static net.minecraft.world.level.levelgen.RandomState randomState(
            net.minecraft.core.HolderGetter<net.minecraft.world.level.levelgen.synth.NormalNoise>
                    noises,
            long seed,
            net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings,
            Operation<net.minecraft.world.level.levelgen.RandomState> original) {
        long start = System.nanoTime();
        try {
            return original.call(noises, seed, settings);
        } finally {
            PerfTrace.count("spawn.prepare.randomStateNanos", System.nanoTime() - start);
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "generateSpawn",
            at =
                    @org.spongepowered.asm.mixin.injection.At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;spawnOriginalMobs(Lnet/minecraft/server/level/WorldGenRegion;)V"))
    private static void originalMobs(
            net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator,
            net.minecraft.server.level.WorldGenRegion region,
            Operation<Void> original) {
        long start = System.nanoTime();
        try {
            original.call(generator, region);
        } finally {
            PerfTrace.count("spawn.nativeMobBodyNanos", System.nanoTime() - start);
        }
    }

    @WrapMethod(method = "generateSpawn")
    private static CompletableFuture<ChunkAccess> spawn(
            WorldGenContext ctx,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original) {
        var span = PerfTrace.begin(ctx.level(), chunk.getPos(), "MOSAIC_SPAWN");
        PerfTrace.purpose("spawn");
        try {
            return original.call(ctx, step, cache, chunk);
        } finally {
            PerfTrace.purpose(null);
            PerfTrace.end(ctx.level(), chunk.getPos(), "MOSAIC_SPAWN", span);
        }
    }
}
