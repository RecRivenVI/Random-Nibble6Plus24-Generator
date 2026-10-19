package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
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
abstract class MosaicChunkMapMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private WorldGenContext worldGenContext;

    @Inject(method = "scheduleGenerationTask", at = @At("HEAD"))
    private void mosaic$plan(
            ChunkStatus status,
            ChunkPos pos,
            CallbackInfoReturnable<ChunkGenerationTask> callback) {
        if (MosaicWorldIdentity.runtimeContext(level).isPresent()) {
            var owner = (MosaicPhysicalLevelOwner) level;
            if (!owner.randomnibble6plus24generator$generationLifecycle().closing())
                owner.mosaic$physicalState().plan(status, pos);
        }
    }

    @Inject(method = "applyStep", at = @At("HEAD"), cancellable = true)
    private void mosaic$physicalStep(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (MosaicWorldIdentity.runtimeContext(level).isEmpty()
                || step.targetStatus() == ChunkStatus.EMPTY) return;
        var owner = (MosaicPhysicalLevelOwner) level;
        var lifecycle = owner.randomnibble6plus24generator$generationLifecycle();
        callback.setReturnValue(
                lifecycle.closing()
                        ? CompletableFuture.failedFuture(lifecycle.cancellation())
                        : owner.mosaic$physicalState().step(holder, step, cache, worldGenContext));
    }

    @Inject(method = "applyStep", at = @At("RETURN"), cancellable = true)
    private void mosaic$loaded(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (step.targetStatus() == ChunkStatus.EMPTY
                && MosaicWorldIdentity.runtimeContext(level).isPresent())
            callback.setReturnValue(
                    ((MosaicPhysicalLevelOwner) level)
                            .mosaic$physicalState()
                            .loaded(holder, callback.getReturnValue()));
    }
}
