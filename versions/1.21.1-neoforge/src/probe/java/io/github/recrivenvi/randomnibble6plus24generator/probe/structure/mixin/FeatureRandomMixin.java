package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldgenRandom.class)
abstract class FeatureRandomMixin {
    @Inject(method = "setDecorationSeed", at = @At("HEAD"))
    private void decoration(long seed, int x, int z, CallbackInfoReturnable<Long> callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.decorationSeed(seed, x, z);
    }

    @Inject(method = "setFeatureSeed", at = @At("HEAD"))
    private void feature(long seed, int index, int step, CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.featureSeed(seed, index, step);
    }
}
