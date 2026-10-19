package io.github.recrivenvi.randomnibble6plus24generator.featurebtest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.IndependentFeatureBVerifier;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class IndependentBDecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void writer(
            WorldGenLevel region,
            ChunkAccess target,
            StructureManager structures,
            CallbackInfo callback) {
        IndependentFeatureBVerifier.observeWriter(region, target);
    }
}
