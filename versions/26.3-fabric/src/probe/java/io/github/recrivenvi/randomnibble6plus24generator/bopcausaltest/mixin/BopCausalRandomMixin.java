package io.github.recrivenvi.randomnibble6plus24generator.bopcausaltest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.BopCausalTrace;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(WorldgenRandom.class)
abstract class BopCausalRandomMixin {
    @Inject(method = "setDecorationSeed", at = @At("HEAD"))
    private void begin(long seed, int x, int z, CallbackInfoReturnable<Long> ci) {
        BopCausalTrace.decoration(this, seed, x, z);
    }

    @Inject(method = "setFeatureSeed", at = @At("HEAD"))
    private void slot(long seed, int index, int step, CallbackInfo ci) {
        BopCausalTrace.slot(this, seed, index, step);
    }

    @Inject(method = "setSeed", at = @At("RETURN"))
    private void seed(long seed, CallbackInfo ci) {
        BopCausalTrace.seeded(this, seed);
    }

    @Inject(method = "next", at = @At("RETURN"))
    private void draw(int bits, CallbackInfoReturnable<Integer> ci) {
        BopCausalTrace.draw(this, bits, ci.getReturnValue());
    }

    @Inject(
            method = {"fork", "forkPositional"},
            at = @At("HEAD"))
    private void fork(CallbackInfoReturnable<?> ci) {
        if (BopCausalTrace.traced(this))
            throw new IllegalStateException("Unaudited causal random fork");
    }
}
