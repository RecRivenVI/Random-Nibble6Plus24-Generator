package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedChunkStatusTasks;

@Mixin(ChunkStatusTasks.class)
abstract class ChunkStatusTasksMixin {

    @Inject(method = "generateStructureStarts", at = @At("HEAD"), cancellable = true)
    private static void randomnibble6plus24generator$routeStructureStarts(
            WorldGenContext worldGenContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
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
        GenerationContextRegistry.find(worldGenContext).ifPresent(context -> callback.setReturnValue(
                IsolatedChunkStatusTasks.generateCarvers(context, step, cache, chunk)));
    }
}
