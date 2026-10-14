package io.github.recrivenvi.randomnibble6plus24generator.featurectest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCFlowVerifier;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(WorldgenRandom.class)
abstract class FeatureCRandomMixin {
    @Inject(method = "setDecorationSeed", at = @At("HEAD"))
    private void decoration(long seed, int x, int z, CallbackInfoReturnable<Long> ci) {
        FeatureCFlowVerifier.random(this);
    }

    @Inject(method = "setFeatureSeed", at = @At("HEAD"))
    private void slot(long seed, int index, int step, CallbackInfo ci) {
        FeatureCFlowVerifier.slot(this, seed, index, step);
    }

    @Inject(method = "next", at = @At("RETURN"))
    private void draw(int bits, CallbackInfoReturnable<Integer> ci) {
        FeatureCFlowVerifier.draw(this, bits, ci.getReturnValue());
    }
}
