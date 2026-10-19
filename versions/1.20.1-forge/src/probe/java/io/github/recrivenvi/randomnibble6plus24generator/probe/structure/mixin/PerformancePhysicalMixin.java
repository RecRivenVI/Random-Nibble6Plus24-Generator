package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.util.concurrent.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = MosaicPhysicalLevelState.class, remap = false)
abstract class PerformancePhysicalMixin {
    @Shadow @Final private ServerLevel level;

    @Redirect(
            method = "prepare",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/ExecutorService;execute(Ljava/lang/Runnable;)V"))
    private void worker(ExecutorService executor, Runnable original, ChunkAccess input) {
        executor.execute(
                LegacyPerformanceProbe.worker(
                        level, input.getPos(), (MosaicPhysicalLevelState) (Object) this, original));
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void request(
            ChunkAccess input,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    ci) {
        LegacyPerformanceProbe.phase(level, input.getPos(), "PREPARE_REQUEST");
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void publication(
            ChunkAccess input,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    ci) {
        if (!LegacyPerformanceProbe.enabled()) return;
        ci.getReturnValue()
                .whenComplete(
                        (chunk, error) -> {
                            if (error != null) LegacyPerformanceProbe.failed(error);
                            else if (chunk.left().isPresent())
                                LegacyPerformanceProbe.phase(
                                        level, input.getPos(), "PUBLISHED_POI_END");
                        });
    }

    @Inject(method = "generate", at = @At("HEAD"))
    private void canonicalBegin(
            ChunkPos pos,
            MosaicRuntimeContext runtime,
            CallbackInfoReturnable<BFeaturePreparation.Result> ci) {
        LegacyPerformanceProbe.canonical(level, pos, false, null);
    }

    @Inject(method = "generate", at = @At("RETURN"))
    private void canonicalEnd(
            ChunkPos pos,
            MosaicRuntimeContext runtime,
            CallbackInfoReturnable<BFeaturePreparation.Result> ci) {
        LegacyPerformanceProbe.canonical(level, pos, true, ci.getReturnValue());
    }

    @Inject(method = "completed", at = @At("HEAD"))
    private void completed(ChunkPos pos, ChunkStatus status, CallbackInfo ci) {
        LegacyPerformanceProbe.phase(level, pos, "PHYSICAL_" + status);
    }
}
