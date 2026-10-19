package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.terrain.NativeTerrainVerifier;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
abstract class StructureStageMixin {
    @Inject(method = "generateFeatures", at = @At("HEAD"))
    private static void noFeatures(
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        if (NativeStructureVerifier.enabled() || NativeTerrainVerifier.enabled())
            throw new IllegalStateException("Native structure-only Oracle reached FEATURES");
    }
}
