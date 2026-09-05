package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2RSpawnTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicGenerationLifecycleOwner;

@Mixin(GenerationChunkHolder.class)
abstract class GenerationChunkHolderMixin {
    @Shadow @Final private AtomicReference<net.minecraft.server.level.ChunkGenerationTask> task;

    @Shadow
    protected abstract void failAndClearPendingFuturesBetween(ChunkStatus fromExclusive, ChunkStatus toInclusive);

    @WrapMethod(method = "applyStep")
    private CompletableFuture<ChunkResult<ChunkAccess>> randomnibble6plus24generator$rejectClosingWorldWork(
            ChunkStep step, GeneratingChunkMap chunkMap, StaticCache2D<GenerationChunkHolder> cache,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        if (chunkMap instanceof MosaicGenerationLifecycleOwner owner) {
            var lifecycle = owner.randomnibble6plus24generator$generationLifecycle();
            if (lifecycle.active() && lifecycle.closing()) {
                randomnibble6plus24generator$terminatePendingWork();
                return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
            }
        }
        return original.call(step, chunkMap, cache);
    }

    @WrapOperation(method = "applyStep", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;handle(Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<ChunkResult<ChunkAccess>> randomnibble6plus24generator$classifyLifecycleTermination(
            CompletableFuture<ChunkAccess> future,
            BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>> nativeHandler,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original,
            @Local(argsOnly = true) GeneratingChunkMap chunkMap) {
        if (!(chunkMap instanceof MosaicGenerationLifecycleOwner owner)) return original.call(future, nativeHandler);
        var lifecycle = owner.randomnibble6plus24generator$generationLifecycle();
        if (!lifecycle.active()) return original.call(future, nativeHandler);
        return original.call(future, (BiFunction<ChunkAccess, Throwable, ChunkResult<ChunkAccess>>) (chunk, failure) -> {
            if (lifecycle.isExpected(failure)) {
                lifecycle.recordExpectedTermination();
                randomnibble6plus24generator$terminatePendingWork();
                return GenerationChunkHolder.UNLOADED_CHUNK;
            }
            // All genuine failures (including plain/foreign cancellations) retain Vanilla's crash path.
            return nativeHandler.apply(chunk, failure);
        });
    }

    @org.spongepowered.asm.mixin.Unique
    private void randomnibble6plus24generator$terminatePendingWork() {
        var running = task.get();
        if (running != null) running.markForCancellation();
        failAndClearPendingFuturesBetween(null, ChunkStatus.FULL);
    }

    @Shadow
    private volatile ChunkStatus highestAllowedStatus;

    @Inject(method = "applyStep", at = @At("HEAD"))
    private void randomnibble6plus24generator$allowExplicitPhysicalDerivedStep(
            ChunkStep step,
            GeneratingChunkMap chunkMap,
            StaticCache2D<GenerationChunkHolder> cache,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> callback) {
        GenerationChunkHolder self = (GenerationChunkHolder) (Object) this;
        ChunkStatus allowance = MosaicPhysicalMaterializer.physicalStepAllowance(chunkMap, self.getPos());
        if (allowance != null && step.targetStatus().isOrBefore(allowance)
                && (highestAllowedStatus == null || allowance.isAfter(highestAllowedStatus))) {
            // A planned Mosaic dependency may temporarily raise the status a
            // holder is allowed to execute.  Never lower Vanilla's ticket
            // derived ceiling: doing so leaves a completed FULL future
            // inaccessible during later BLOCK_TICKING promotion and makes
            // ServerLevel.getChunk report an unloaded chunk.
            highestAllowedStatus = allowance;
        }
    }

    @Inject(method = "scheduleChunkGenerationTask", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceGenerationScheduling(
            ChunkStatus status,
            net.minecraft.server.level.ChunkMap chunkMap,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> callback) {
        GenerationChunkHolder self = (GenerationChunkHolder) (Object) this;
        if (self instanceof net.minecraft.server.level.ChunkHolder holder) {
            Phase3C2RSpawnTrace.recordHolderUpdate(chunkMap, holder, "generation-before");
        }
        Phase3C2RSpawnTrace.recordGenerationScheduling(self, status);
    }

    @Inject(method = "completeFuture", at = @At("RETURN"))
    private void randomnibble6plus24generator$observeAtomicPhysicalPublish(
            ChunkStatus status,
            ChunkAccess chunk,
            CallbackInfo callback) {
        MosaicPhysicalMaterializer.onHolderFutureCompleted(
                (GenerationChunkHolder) (Object) this, status, chunk);
    }
}
