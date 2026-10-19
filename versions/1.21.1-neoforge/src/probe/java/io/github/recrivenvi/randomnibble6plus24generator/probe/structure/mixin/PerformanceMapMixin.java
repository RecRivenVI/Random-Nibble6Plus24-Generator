package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
abstract class PerformanceMapMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "scheduleGenerationTask", at = @At("HEAD"))
    private void scheduled(
            ChunkStatus status,
            ChunkPos position,
            CallbackInfoReturnable<ChunkGenerationTask> callback) {
        LegacyPerformanceProbe.scheduled(level, position, status);
    }

    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkAccess> step(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            Operation<CompletableFuture<ChunkAccess>> original) {
        if (!LegacyPerformanceProbe.enabled()) return original.call(holder, step, cache);
        LegacyPerformanceProbe.step(level, holder.getPos(), step.targetStatus(), false);
        var future = original.call(holder, step, cache);
        future.whenComplete(
                (chunk, error) -> {
                    if (error != null) LegacyPerformanceProbe.failed(error);
                    else
                        LegacyPerformanceProbe.step(
                                level, holder.getPos(), step.targetStatus(), true);
                });
        return future;
    }
}
