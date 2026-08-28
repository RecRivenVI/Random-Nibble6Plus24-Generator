package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "scheduleGenerationTask", at = @At("HEAD"))
    private void randomnibble6plus24generator$registerPhysicalMosaicTarget(
            ChunkStatus targetStatus,
            ChunkPos pos,
            CallbackInfoReturnable<ChunkGenerationTask> callback) {
        if (MosaicPhysicalMaterializer.isPhysicalMosaic(level)) {
            MosaicPhysicalMaterializer.registerPhysicalRequest(level, targetStatus, pos);
        }
    }

    @Inject(method = "applyStep", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$skipPhysicalSeedWorldgen(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (!MosaicPhysicalMaterializer.isPhysicalMosaic(level) || step.targetStatus() == ChunkStatus.EMPTY) return;
        ChunkStatus target = step.targetStatus();
        if (target.isOrBefore(ChunkStatus.FEATURES)) {
            callback.setReturnValue(MosaicPhysicalMaterializer.passThroughPhysicalStep(level, holder, target));
            return;
        }
        if (target == ChunkStatus.INITIALIZE_LIGHT) return;
        if (target == ChunkStatus.LIGHT) {
            try {
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace
                        .beforeLightStep(level, holder);
            } catch (RuntimeException exception) {
                CompletableFuture<ChunkAccess> failed = new CompletableFuture<>();
                failed.completeExceptionally(exception);
                callback.setReturnValue(MosaicPhysicalMaterializer.onPhysicalStepFuture(
                        level, holder, target, failed));
            }
            return;
        }
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace
                .rejectForbiddenStatus(target);
    }

    @Inject(method = "applyStep", at = @At("RETURN"), cancellable = true)
    private void randomnibble6plus24generator$publishArtifactAtEmptyResult(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (step.targetStatus() == ChunkStatus.EMPTY
                && MosaicPhysicalMaterializer.isPhysicalMosaic(level)
                && MosaicPhysicalMaterializer.hasMaterializationObligation(level, holder.getPos())) {
            callback.setReturnValue(MosaicPhysicalMaterializer.materializeLoadedTarget(
                    level, holder, callback.getReturnValue()));
        } else if ((step.targetStatus() == ChunkStatus.INITIALIZE_LIGHT
                        || step.targetStatus() == ChunkStatus.LIGHT)
                && MosaicPhysicalMaterializer.isPhysicalMosaic(level)) {
            callback.setReturnValue(MosaicPhysicalMaterializer.onPhysicalStepFuture(
                    level, holder, step.targetStatus(), callback.getReturnValue()));
        }
    }
}
