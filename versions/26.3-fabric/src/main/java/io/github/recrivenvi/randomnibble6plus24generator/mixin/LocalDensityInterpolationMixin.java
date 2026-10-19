package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.LocalDensityTiles;
import net.minecraft.world.level.levelgen.densityfunction.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
        targets =
                "net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction$Sampler")
abstract class LocalDensityInterpolationMixin {
    @WrapOperation(
            method = "sampleWithBlockStep",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler;sampleVolume(Lnet/minecraft/world/level/levelgen/densityfunction/SamplerContext;Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;)V"))
    private void source(
            DensitySampler sampler,
            SamplerContext context,
            DensityBuffer output,
            DensityVolume volume,
            Operation<Void> original) {
        if (!LocalDensityTiles.sampleSource(sampler, context, output, volume))
            original.call(sampler, context, output, volume);
    }
}
