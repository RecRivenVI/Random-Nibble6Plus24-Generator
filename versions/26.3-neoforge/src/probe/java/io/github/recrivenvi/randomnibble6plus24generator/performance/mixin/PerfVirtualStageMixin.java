package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = IsolatedChunkStatusTasks.class, remap = false)
abstract class PerfVirtualStageMixin {
    @WrapMethod(method = "generateStructureStarts")
    private static CompletableFuture<ChunkAccess> starts(
            IsolatedGenerationContext ctx,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original) {
        PerfTrace.count("structureStartEvaluations." + PerfTrace.purpose(), 1);
        if (!PerfTrace.purpose().equals("spawn")) return original.call(ctx, step, cache, chunk);
        long start = System.nanoTime();
        try {
            return original.call(ctx, step, cache, chunk);
        } finally {
            PerfTrace.count("spawn.prepare.structureStartsNanos", System.nanoTime() - start);
        }
    }

    @WrapMethod(method = "generateStructureReferences")
    private static CompletableFuture<ChunkAccess> references(
            IsolatedGenerationContext ctx,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original) {
        if (!PerfTrace.purpose().equals("spawn")) return original.call(ctx, step, cache, chunk);
        long start = System.nanoTime();
        try {
            return original.call(ctx, step, cache, chunk);
        } finally {
            PerfTrace.count("spawn.prepare.structureReferencesNanos", System.nanoTime() - start);
        }
    }

    @WrapMethod(method = "generateBiomes")
    private static CompletableFuture<ChunkAccess> biomes(
            IsolatedGenerationContext ctx,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original) {
        if (!PerfTrace.purpose().equals("spawn")) return original.call(ctx, step, cache, chunk);
        long start = System.nanoTime();
        var future = original.call(ctx, step, cache, chunk);
        future.whenComplete(
                (v, e) ->
                        PerfTrace.count(
                                "spawn.prepare.actualBiomeFuturesNanos",
                                System.nanoTime() - start));
        return future;
    }
}
