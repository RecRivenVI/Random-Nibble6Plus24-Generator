package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StrictFeatureRegion.class, remap = false)
abstract class FeatureCarvingReadMixin {
    @Inject(method = "carvingMask", at = @At("RETURN"))
    private void read(
            ChunkPos pos,
            GenerationStep.Carving stage,
            CallbackInfoReturnable<CarvingMask> callback) {
        NativeFeatureVerifier.carvingMaskRead(pos, stage, callback.getReturnValue());
    }
}
