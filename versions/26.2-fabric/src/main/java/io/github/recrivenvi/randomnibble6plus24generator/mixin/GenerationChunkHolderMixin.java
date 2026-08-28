package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;

@Mixin(GenerationChunkHolder.class)
abstract class GenerationChunkHolderMixin {

    @Inject(method = "completeFuture", at = @At("RETURN"))
    private void randomnibble6plus24generator$observeAtomicPhysicalPublish(
            ChunkStatus status,
            ChunkAccess chunk,
            CallbackInfo callback) {
        MosaicPhysicalMaterializer.onHolderFutureCompleted(
                (GenerationChunkHolder) (Object) this, status, chunk);
    }
}
