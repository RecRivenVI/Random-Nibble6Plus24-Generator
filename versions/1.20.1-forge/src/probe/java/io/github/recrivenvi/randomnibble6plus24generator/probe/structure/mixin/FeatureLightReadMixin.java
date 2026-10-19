package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// This observer targets the mod-owned named method in native development runs.
@Mixin(value = StrictFeatureRegion.class, remap = false)
abstract class FeatureLightReadMixin {
    @Inject(method = "getBrightness", at = @At("RETURN"))
    private void read(LightLayer layer, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        NativeFeatureVerifier.lightRead(layer, pos, callback.getReturnValue());
    }

    @Inject(method = "getRawBrightness", at = @At("RETURN"))
    private void raw(BlockPos pos, int darken, CallbackInfoReturnable<Integer> callback) {
        NativeFeatureVerifier.rawLightRead(pos, darken, callback.getReturnValue());
    }
}
