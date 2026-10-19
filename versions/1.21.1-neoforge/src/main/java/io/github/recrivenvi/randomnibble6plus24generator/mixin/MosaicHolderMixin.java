package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenerationChunkHolder.class)
abstract class MosaicHolderMixin {
    @Shadow private volatile ChunkStatus highestAllowedStatus;
    @Shadow @Final private AtomicReference<ChunkGenerationTask> task;

    @Shadow
    protected abstract void failAndClearPendingFuturesBetween(ChunkStatus from, ChunkStatus to);

    @Unique private MosaicPhysicalLevelOwner mosaic$owner;

    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkResult<ChunkAccess>> mosaic$apply(
            ChunkStep step,
            GeneratingChunkMap map,
            StaticCache2D<GenerationChunkHolder> cache,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        if (map instanceof ChunkMap chunks) {
            var level = ((ChunkMapQueues) chunks).mosaic$level();
            if (MosaicWorldIdentity.runtimeContext(level).isPresent()) {
                var owner = (MosaicPhysicalLevelOwner) level;
                if (mosaic$owner != null && mosaic$owner != owner)
                    throw new IllegalStateException("Physical holder moved to another world");
                mosaic$owner = owner;
                var life = owner.randomnibble6plus24generator$generationLifecycle();
                if (life.closing()) {
                    mosaic$terminate();
                    return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
                }
                var allowance =
                        owner.mosaic$physicalState()
                                .allowance(((GenerationChunkHolder) (Object) this).getPos());
                // Never lower the native ticket-derived ceiling (the player-spawn regression).
                if (allowance != null
                        && step.targetStatus().isOrBefore(allowance)
                        && (highestAllowedStatus == null
                                || allowance.isAfter(highestAllowedStatus)))
                    highestAllowedStatus = allowance;
            }
        }
        return original.call(step, map, cache);
    }

    @WrapOperation(
            method = "applyStep",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;handle(Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> mosaic$termination(
            CompletableFuture<ChunkAccess> future,
            BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> handler,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original,
            @Local(argsOnly = true) GeneratingChunkMap map) {
        if (mosaic$owner == null) return original.call(future, handler);
        var life = mosaic$owner.randomnibble6plus24generator$generationLifecycle();
        var executor = ((ChunkMapQueues) (ChunkMap) map).mosaic$mainThreadExecutor();
        // A completed input executes non-async handle on its registering worldgen thread.
        // Dispatch the Mosaic completion itself, not merely the prepared result.
        return future.handleAsync(
                (BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>>)
                        (chunk, failure) -> {
                            if (life.isExpected(failure) || (failure == null && life.closing())) {
                                life.recordExpectedTermination();
                                mosaic$terminate();
                                return GenerationChunkHolder.UNLOADED_CHUNK;
                            }
                            try {
                                return handler.apply(chunk, failure);
                            } catch (Throwable publicationFailure) {
                                if (life.isExpected(publicationFailure)) {
                                    life.recordExpectedTermination();
                                    mosaic$terminate();
                                    return GenerationChunkHolder.UNLOADED_CHUNK;
                                }
                                MinecraftServer.setFatalException(
                                        new net.minecraft.ReportedException(
                                                net.minecraft.CrashReport.forThrowable(
                                                        publicationFailure,
                                                        "Mosaic holder publication")));
                                throw publicationFailure;
                            }
                        },
                executor);
    }

    @Inject(method = "completeFuture", at = @At("HEAD"))
    private void mosaic$published(ChunkStatus status, ChunkAccess chunk, CallbackInfo callback) {
        if (mosaic$owner != null)
            mosaic$owner
                    .mosaic$physicalState()
                    .published((GenerationChunkHolder) (Object) this, status, chunk);
    }

    @Unique
    private void mosaic$terminate() {
        var running = task.get();
        if (running != null) running.markForCancellation();
        failAndClearPendingFuturesBetween(null, ChunkStatus.FULL);
    }
}
