package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.probe.terrain.NativeTerrainVerifier;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class StructureStageMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void noFeatures(CallbackInfo callback) {
        if (NativeStructureVerifier.enabled() || NativeTerrainVerifier.enabled())
            throw new IllegalStateException("Native structure-only Oracle reached FEATURES");
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void beginB(
            net.minecraft.world.level.WorldGenLevel region,
            net.minecraft.world.level.chunk.ChunkAccess target,
            net.minecraft.world.level.StructureManager structures,
            CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.beginWriter(region, target);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void endB(CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.endWriter();
    }
}
