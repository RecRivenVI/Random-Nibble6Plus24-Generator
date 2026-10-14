package io.github.recrivenvi.randomnibble6plus24generator.featureatest.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.OreHeightOracle;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class OreHeightOracleMixin {
    @WrapOperation(
            method = "doFill",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler$Bound;sampleVolume(Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;)Lnet/minecraft/world/level/levelgen/densityfunction/ScopedDensityBuffer;"))
    private ScopedDensityBuffer density(
            DensitySampler.Bound sampler,
            DensityVolume volume,
            Operation<ScopedDensityBuffer> original,
            NoiseChunk noise,
            ChunkAccess chunk) {
        var buffer = original.call(sampler, volume);
        OreHeightOracle.density(chunk, volume, buffer);
        return buffer;
    }

    @Inject(method = "doFill", at = @At("RETURN"))
    private void noise(NoiseChunk noise, ChunkAccess chunk, CallbackInfo callback) {
        OreHeightOracle.noise(chunk);
    }
}
