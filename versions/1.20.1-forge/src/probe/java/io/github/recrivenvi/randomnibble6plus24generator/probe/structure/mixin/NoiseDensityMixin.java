package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.terrain.NoiseDensityEvidence;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
abstract class NoiseDensityMixin {
    @Inject(method = "fillSlice", at = @At("RETURN"))
    private void capture(boolean first, int cellX, CallbackInfo callback) {
        NoiseDensityEvidence.capture((NoiseChunk) (Object) this, first, cellX);
    }
}
