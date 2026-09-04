package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2RSpawnTrace;

@Mixin(GenerationChunkHolder.class)
abstract class GenerationChunkHolderMixin {

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
