package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.CompletionHandoffEvidence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenerationChunkHolder.class)
abstract class CompletionOrderMixin {
    @WrapOperation(
            method = "applyStep",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/GeneratingChunkMap;applyStep(Lnet/minecraft/server/level/GenerationChunkHolder;Lnet/minecraft/world/level/chunk/status/ChunkStep;Lnet/minecraft/util/StaticCache2D;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkAccess> forceReady(
            GeneratingChunkMap map,
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            Operation<CompletableFuture<ChunkAccess>> original) {
        return CompletionHandoffEvidence.beforeHandle(
                holder, step.targetStatus(), original.call(map, holder, step, cache));
    }

    @Inject(method = "completeFuture", at = @At("HEAD"))
    private void recordThread(ChunkStatus status, ChunkAccess chunk, CallbackInfo callback) {
        CompletionHandoffEvidence.completed((GenerationChunkHolder) (Object) this);
    }
}
