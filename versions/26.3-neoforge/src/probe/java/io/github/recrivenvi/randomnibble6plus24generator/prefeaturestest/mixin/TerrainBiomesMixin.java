package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.TerrainProbeEvidence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGenerator.class)
abstract class TerrainBiomesMixin {
    @Inject(method = "createBiomes", at = @At("RETURN"), cancellable = true)
    private void palette(
            RandomState random,
            Blender blender,
            StructureManager structures,
            ChunkAccess chunk,
            CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback) {
        callback.setReturnValue(
                callback.getReturnValue()
                        .thenApply(
                                generated -> {
                                    TerrainProbeEvidence.biomes(generated);
                                    return generated;
                                }));
    }
}
