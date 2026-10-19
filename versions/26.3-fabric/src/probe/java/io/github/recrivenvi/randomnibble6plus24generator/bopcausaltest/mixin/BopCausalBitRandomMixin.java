package io.github.recrivenvi.randomnibble6plus24generator.bopcausaltest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.BopCausalTrace;
import net.minecraft.world.level.levelgen.BitRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BitRandomSource.class)
interface BopCausalBitRandomMixin {
    @Inject(method = "nextInt()I", at = @At("RETURN"))
    private void integer(CallbackInfoReturnable<Integer> ci) {
        BopCausalTrace.api(this, "int", ci.getReturnValue());
    }

    @Inject(method = "nextInt(I)I", at = @At("RETURN"))
    private void bounded(int bound, CallbackInfoReturnable<Integer> ci) {
        BopCausalTrace.api(this, "bounded", bound, ci.getReturnValue());
    }

    @Inject(method = "nextLong", at = @At("RETURN"))
    private void longValue(CallbackInfoReturnable<Long> ci) {
        BopCausalTrace.api(this, "long", ci.getReturnValue());
    }

    @Inject(method = "nextBoolean", at = @At("RETURN"))
    private void bool(CallbackInfoReturnable<Boolean> ci) {
        BopCausalTrace.api(this, "boolean", ci.getReturnValue());
    }

    @Inject(method = "nextFloat", at = @At("RETURN"))
    private void floatValue(CallbackInfoReturnable<Float> ci) {
        BopCausalTrace.api(this, "floatBits", Float.floatToRawIntBits(ci.getReturnValue()));
    }

    @Inject(method = "nextDouble", at = @At("RETURN"))
    private void doubleValue(CallbackInfoReturnable<Double> ci) {
        BopCausalTrace.api(this, "doubleBits", Double.doubleToRawLongBits(ci.getReturnValue()));
    }
}
