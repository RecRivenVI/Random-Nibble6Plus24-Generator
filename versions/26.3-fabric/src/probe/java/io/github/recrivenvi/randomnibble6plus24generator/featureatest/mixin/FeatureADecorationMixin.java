package io.github.recrivenvi.randomnibble6plus24generator.featureatest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureAVerifier;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
abstract class FeatureADecorationMixin {
    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void writer(
            WorldGenLevel level, ChunkAccess chunk, StructureManager manager, CallbackInfo ci) {
        FeatureAVerifier.writer(level, chunk);
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureDesignComparison
                .writer(chunk.getPos());
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureATreeVerifier
                .writer(level);
    }

    @Redirect(
            method = "applyBiomeDecoration",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/StructureManager;shouldGenerateStructures()Z"))
    private boolean nativeOreOracleOnly(StructureManager manager) {
        return !FeatureAVerifier.oracle()
                && !io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify
                        .FeatureATreeVerifier.nativeControl()
                && manager.shouldGenerateStructures();
    }
}
