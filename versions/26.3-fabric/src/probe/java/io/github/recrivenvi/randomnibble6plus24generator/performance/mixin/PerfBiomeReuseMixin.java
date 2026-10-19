package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.LightweightFeatureBaseline;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.TerrainBiomeInputs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A/B control only: disable input retention while keeping the same structure reuse and terrain. */
@Mixin(value = LightweightFeatureBaseline.class, remap = false)
abstract class PerfBiomeReuseMixin {
    @ModifyVariable(
            method =
                    "generate(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/IsolatedGenerationContext;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/features/LocalFeatureInputs$Observer;Ljava/util/function/BiConsumer;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/ScopedStructureSources;ZLio/github/recrivenvi/randomnibble6plus24generator/worldgen/terrain/TerrainBiomeInputs;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/features/LightweightFeatureBaseline$Result;",
            at = @At("HEAD"),
            argsOnly = true)
    private static TerrainBiomeInputs control(TerrainBiomeInputs input) {
        return Boolean.getBoolean("mosaic.profile.bReferenceBiomes") ? null : input;
    }
}
