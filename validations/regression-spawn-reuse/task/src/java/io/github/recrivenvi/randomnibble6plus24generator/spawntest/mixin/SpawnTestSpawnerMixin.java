package io.github.recrivenvi.randomnibble6plus24generator.spawntest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.spawntest.SpawnReuseVerifier;

import java.util.concurrent.CompletableFuture;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalSpawner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicSpawnBiomeCarrier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.V2SpawnOracle;

@Mixin(value=MosaicPhysicalSpawner.class, remap=false)
abstract class SpawnTestSpawnerMixin {
    @org.spongepowered.asm.mixin.Unique private static boolean injectedFailure;

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method="generateSpawn",at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",target="Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;spawnOriginalMobs(Lnet/minecraft/server/level/WorldGenRegion;)V"))
    private static void failBeforeMobBody(net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator,
            net.minecraft.server.level.WorldGenRegion region, Operation<Void> original) {
        if(Boolean.getBoolean("mosaic.spawn.test.failure") && !injectedFailure) {
            injectedFailure=true;
            throw new IllegalStateException("injected SPAWN cleanup regression");
        }
        original.call(generator,region);
    }
    @WrapMethod(method="generateSpawn")
    private static CompletableFuture<ChunkAccess> compare(WorldGenContext context, ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original) {
        var before = SpawnReuseVerifier.before(context.level(), chunk);
        CompletableFuture<ChunkAccess> result;
        try {
            result = Boolean.getBoolean("mosaic.spawn.test.oracle")
                    ? V2SpawnOracle.generateSpawn(context, step, cache, chunk)
                    : original.call(context,step,cache,chunk);
        } catch(IllegalStateException failure) {
            if(!"injected SPAWN cleanup regression".equals(failure.getMessage()))throw failure;
            if(io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry.bindingCount()!=0
                    || ((MosaicSpawnBiomeCarrier)chunk).randomnibble6plus24generator$spawnBiomes()!=null)
                throw new AssertionError("Failed SPAWN leaked bindings or carried inputs");
            // Test-only retry after failure before any mob writes. Production does not catch/retry.
            SpawnReuseVerifier.recordFailureCleanup();
            result=original.call(context,step,cache,chunk);
        }
        result.join();
        SpawnReuseVerifier.after(context.level(), chunk, before);
        if (!Boolean.getBoolean("mosaic.spawn.test.oracle")
                && ((MosaicSpawnBiomeCarrier)chunk).randomnibble6plus24generator$spawnBiomes()!=null)
            throw new AssertionError("SPAWN retained carried inputs");
        return result;
    }
}
