package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class FeatureWriterMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void beginB(
            WorldGenLevel region,
            ChunkAccess target,
            StructureManager structures,
            CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.beginWriter(region, target);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void endB(CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.endWriter();
    }
}
