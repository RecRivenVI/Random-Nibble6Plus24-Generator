package io.github.recrivenvi.randomnibble6plus24generator.featureatest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureAVerifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FeaturePlacer.class)
abstract class FeatureAPlacementMixin {
    @Shadow @Final private WorldGenLevel level;

    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void selectedNativeOracle(
            PlacedFeature feature,
            RandomSource random,
            BlockPos origin,
            CallbackInfoReturnable<Boolean> ci) {
        if (FeatureAVerifier.filter(feature, level, origin)
                || io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify
                        .FeatureATreeVerifier.filter(feature, level)) ci.setReturnValue(false);
    }

    @Inject(method = "placeWithBiomeCheck", at = @At("RETURN"))
    private void treeRng(
            PlacedFeature feature,
            RandomSource random,
            BlockPos origin,
            CallbackInfoReturnable<Boolean> ci) {
        io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureATreeVerifier
                .checkpoint(feature, random, level);
    }
}
