package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalSpawner;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
abstract class MosaicPopulationMixin {
    @Inject(method = "generateSpawn", at = @At("HEAD"), cancellable = true)
    private static void population(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (context.generator() instanceof MosaicChunkGenerator)
            callback.setReturnValue(MosaicPhysicalSpawner.spawn(context, step, cache, chunk));
    }
}
