package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedChunkStatusTasks;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureFrontierEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeFeatureExecutionTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;

@Mixin(ChunkStatusTasks.class)
abstract class ChunkStatusTasksMixin {

    @Inject(method = {"generateSpawn", "full"}, at = @At("HEAD"))
    private static void randomnibble6plus24generator$rejectPostFeaturesPhysicalMosaic(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (GenerationContextRegistry.find(worldGenContext).isEmpty()
                && MosaicPhysicalMaterializer.isPhysicalMosaic(worldGenContext.level())) {
            PhysicalMosaicTrace.rejectForbiddenStatus(step.targetStatus());
        }
    }

    @Inject(method = "initializeLight", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$preparePhysicalMosaicInitializeLight(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (GenerationContextRegistry.find(worldGenContext).isPresent()
                || !MosaicPhysicalMaterializer.isPhysicalMosaic(worldGenContext.level())) return;
        boolean physicalEngine = worldGenContext.lightEngine()
                == worldGenContext.level().getChunkSource().getLightEngine();
        try {
            PhysicalMosaicTrace.beginPhysicalStage(
                    worldGenContext.level(), ChunkStatus.INITIALIZE_LIGHT, chunk, physicalEngine);
            MosaicPhysicalPoiReconciler.reconcile(worldGenContext.level(), chunk);
        } catch (RuntimeException exception) {
            CompletableFuture<ChunkAccess> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            callback.setReturnValue(failed);
        }
    }

    @Inject(method = "light", at = @At("HEAD"))
    private static void randomnibble6plus24generator$observePhysicalMosaicLight(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (GenerationContextRegistry.find(worldGenContext).isPresent()
                || !MosaicPhysicalMaterializer.isPhysicalMosaic(worldGenContext.level())) return;
        boolean physicalEngine = worldGenContext.lightEngine()
                == worldGenContext.level().getChunkSource().getLightEngine();
        PhysicalMosaicTrace.beginPhysicalStage(
                worldGenContext.level(), ChunkStatus.LIGHT, chunk, physicalEngine);
    }

    @Inject(method = "generateStructureStarts", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeStructureStarts(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        captureStage(worldGenContext, ChunkStatus.EMPTY, chunk);
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateStructureStarts(context, step, cache, chunk)));
    }

    @Inject(method = "generateStructureReferences", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeStructureReferences(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        captureStage(worldGenContext, ChunkStatus.STRUCTURE_STARTS, chunk);
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateStructureReferences(context, step, cache, chunk)));
    }

    @Inject(method = "generateBiomes", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeBiomes(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        captureStage(worldGenContext, ChunkStatus.STRUCTURE_REFERENCES, chunk);
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateBiomes(context, step, cache, chunk)));
    }

    @Inject(method = "generateNoise", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeNoise(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        captureStage(worldGenContext, ChunkStatus.BIOMES, chunk);
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateNoise(context, step, cache, chunk)));
    }

    @Inject(method = "generateSurface", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeSurface(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        FeatureFrontierEvidence.captureSurfacePre(worldGenContext, cache, chunk);
        captureStage(worldGenContext, ChunkStatus.NOISE, chunk);
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateSurface(context, step, cache, chunk)));
    }

    @Inject(method = "generateCarvers", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeCarvers(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        captureStage(worldGenContext, ChunkStatus.SURFACE, chunk);
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateCarvers(context, step, cache, chunk)));
    }

    @Inject(method = "generateFeatures", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeFeatures(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        captureStage(worldGenContext, net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS, chunk);
        var isolated = GenerationContextRegistry.find(worldGenContext);
        if (isolated.isPresent()) {
            isolated.get().recordFeatureVisibleBiomes(FeatureFrontierEvidence.featureVisibleBiomeSignature(
                    cache, chunk.getPos(), worldGenContext.level().registryAccess()));
            callback.setReturnValue(IsolatedChunkStatusTasks.generateFeatures(
                    isolated.get(), step, cache, chunk));
            return;
        }
        if (NativeFeatureExecutionTrace.active()) {
            NativeFeatureExecutionTrace.recordFeatureVisibleBiomes(
                    FeatureFrontierEvidence.featureVisibleBiomeSignature(
                            cache, chunk.getPos(), worldGenContext.level().registryAccess()));
        }
        NativeFeatureExecutionTrace.beginWriter(chunk.getPos());
        FeatureFrontierEvidence.capture(
                FeatureFrontierEvidence.Mode.NATIVE,
                worldGenContext,
                cache,
                chunk,
                FeatureFrontierEvidence.Phase.PRE);
    }

    @Inject(method = "generateFeatures", at = @At("RETURN"), cancellable = true)
    private static void randomnibble6plus24generator$captureNativeFeaturesPost(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (GenerationContextRegistry.find(worldGenContext).isPresent()
                || (!FeatureFrontierEvidence.active(FeatureFrontierEvidence.Mode.NATIVE)
                        && !NativeFeatureExecutionTrace.active())) {
            return;
        }
        callback.setReturnValue(callback.getReturnValue().thenApply(generated -> {
            FeatureFrontierEvidence.capture(
                    FeatureFrontierEvidence.Mode.NATIVE,
                    worldGenContext,
                    cache,
                    generated,
                    FeatureFrontierEvidence.Phase.POST);
            NativeFeatureExecutionTrace.completeWriter(generated.getPos());
            return generated;
        }));
    }

    private static void captureStage(
            WorldGenContext worldGenContext,
            net.minecraft.world.level.chunk.status.ChunkStatus status,
            ChunkAccess chunk) {
        if (FeatureFrontierEvidence.shouldCaptureStage(worldGenContext, status, chunk)) {
            FeatureFrontierEvidence.captureStage(worldGenContext, status, chunk);
        }
    }
}
