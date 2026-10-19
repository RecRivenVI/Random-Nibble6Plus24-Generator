package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PreFeaturesVerifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStep.class)
abstract class PreFeaturesStepMixin {
    @WrapMethod(method = "apply")
    private CompletableFuture<ChunkAccess> reportFailure(
            WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            Operation<CompletableFuture<ChunkAccess>> original) {
        try {
            return original.call(context, cache, chunk)
                    .whenComplete(
                            (generated, failure) -> {
                                if (failure != null) printFailure(failure);
                            });
        } catch (RuntimeException | Error failure) {
            printFailure(failure);
            throw failure;
        }
    }

    private static void printFailure(Throwable failure) {
        failure.printStackTrace();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof net.minecraft.ReportedException reported)
                System.err.println(reported.getReport().getDetails());
        }
    }

    @Inject(method = "apply", at = @At("HEAD"))
    private void rejectDecoration(
            WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        PreFeaturesVerifier.requirePreFeatures(((ChunkStep) (Object) this).targetStatus());
    }

    @Inject(method = "apply", at = @At("RETURN"), cancellable = true)
    private void captureAfterStatusCompletion(
            WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        ChunkStep step = (ChunkStep) (Object) this;
        if (PreFeaturesVerifier.shouldCapture(context, chunk)) {
            callback.setReturnValue(
                    callback.getReturnValue()
                            .thenApply(
                                    generated -> {
                                        PreFeaturesVerifier.capture(
                                                context, step, cache, generated);
                                        return generated;
                                    }));
        }
    }
}
