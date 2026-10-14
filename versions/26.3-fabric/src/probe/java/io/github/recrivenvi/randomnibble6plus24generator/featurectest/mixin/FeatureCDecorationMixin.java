package io.github.recrivenvi.randomnibble6plus24generator.featurectest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCFlowVerifier;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class FeatureCDecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void begin(
            WorldGenLevel level, ChunkAccess target, StructureManager manager, CallbackInfo ci) {
        FeatureCFlowVerifier.begin(level, target);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void end(
            WorldGenLevel level, ChunkAccess target, StructureManager manager, CallbackInfo ci) {
        FeatureCFlowVerifier.end();
    }
}
