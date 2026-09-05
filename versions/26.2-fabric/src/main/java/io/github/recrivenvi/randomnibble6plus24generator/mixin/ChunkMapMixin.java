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
import net.minecraft.world.level.chunk.status.WorldGenContext;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicGenerationLifecycle;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicGenerationLifecycleOwner;

@Mixin(ChunkMap.class)
abstract class ChunkMapMixin implements MosaicGenerationLifecycleOwner {
    @Override
    public MosaicGenerationLifecycle randomnibble6plus24generator$generationLifecycle() {
        return ((MosaicGenerationLifecycleOwner) level).randomnibble6plus24generator$generationLifecycle();
    }

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private WorldGenContext worldGenContext;

    @Inject(method = "scheduleGenerationTask", at = @At("HEAD"))
    private void randomnibble6plus24generator$registerPhysicalMosaicTarget(
            ChunkStatus targetStatus,
            ChunkPos pos,
            CallbackInfoReturnable<ChunkGenerationTask> callback) {
        if (randomnibble6plus24generator$generationLifecycle().active()
                && randomnibble6plus24generator$generationLifecycle().closing()) return;
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
        var lifecycle = randomnibble6plus24generator$generationLifecycle();
        if (lifecycle.active() && lifecycle.closing()) {
            callback.setReturnValue(CompletableFuture.failedFuture(lifecycle.cancellation()));
            return;
        }
        if (!MosaicPhysicalMaterializer.isPhysicalMosaic(level) || step.targetStatus() == ChunkStatus.EMPTY) return;
        ChunkStatus target = step.targetStatus();
        if (target.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)
                && target.isOrBefore(ChunkStatus.FULL)
                && MosaicPhysicalMaterializer.hasMaterializationObligation(level, holder.getPos())) {
            ChunkAccess current = holder.getChunkIfPresentUnchecked(target.getParent());
            if (current == null || current.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
                callback.setReturnValue(MosaicPhysicalMaterializer.materializePhysicalStep(
                        level, holder, step, cache, worldGenContext));
                return;
            }
        }
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
        if (target == ChunkStatus.SPAWN || target == ChunkStatus.FULL) return;
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace
                .rejectForbiddenStatus(target);
    }

    @Inject(method = "applyStep", at = @At("RETURN"), cancellable = true)
    private void randomnibble6plus24generator$publishArtifactAtEmptyResult(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (randomnibble6plus24generator$generationLifecycle().active()
                && randomnibble6plus24generator$generationLifecycle().closing()) return;
        if (step.targetStatus() == ChunkStatus.EMPTY
                && MosaicPhysicalMaterializer.isPhysicalMosaic(level)
                && MosaicPhysicalMaterializer.hasMaterializationObligation(level, holder.getPos())) {
            callback.setReturnValue(MosaicPhysicalMaterializer.materializeLoadedTarget(
                    level, holder, callback.getReturnValue()));
        } else if ((step.targetStatus() == ChunkStatus.INITIALIZE_LIGHT
                        || step.targetStatus() == ChunkStatus.LIGHT
                        || step.targetStatus() == ChunkStatus.SPAWN
                        || step.targetStatus() == ChunkStatus.FULL)
                && MosaicPhysicalMaterializer.isPhysicalMosaic(level)) {
            callback.setReturnValue(MosaicPhysicalMaterializer.onPhysicalStepFuture(
                    level, holder, step.targetStatus(), callback.getReturnValue()));
        }
    }
}
