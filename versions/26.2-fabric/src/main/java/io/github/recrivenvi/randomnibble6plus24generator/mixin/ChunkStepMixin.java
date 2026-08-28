package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
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

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaControlHarness;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaFeatureOrderProbe;

@Mixin(ChunkStep.class)
abstract class ChunkStepMixin {

    @Shadow
    @Final
    private ChunkStatus targetStatus;

    @Inject(method = "apply", at = @At("RETURN"), cancellable = true)
    private void randomnibble6plus24generator$captureNativeControlStage(
            WorldGenContext worldGenContext,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        CompletableFuture<ChunkAccess> future = callback.getReturnValue();
        if (NativeVanillaControlHarness.shouldCapture(worldGenContext, targetStatus, chunk)) {
            future = future.thenApply(generated -> {
                NativeVanillaControlHarness.capture(worldGenContext, targetStatus, generated);
                return generated;
            });
        }
        if (NativeVanillaFeatureOrderProbe.shouldObserve(worldGenContext, targetStatus, chunk)) {
            future = future.thenApply(generated -> {
                NativeVanillaFeatureOrderProbe.observe(worldGenContext, targetStatus, generated);
                return generated;
            });
        }
        if (future != callback.getReturnValue()) {
            callback.setReturnValue(future);
        }
    }
}
