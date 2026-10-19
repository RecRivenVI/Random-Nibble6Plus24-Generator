package io.github.recrivenvi.randomnibble6plus24generator.bopcausaltest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.BopCausalTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.placement.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkGenerator.class)
abstract class BopCausalDecorationMixin {
    @WrapMethod(method = "applyBiomeDecoration")
    private void trace(
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structures,
            Operation<Void> original) {
        BopCausalTrace.begin(level, chunk);
        try {
            original.call(level, chunk, structures);
        } finally {
            BopCausalTrace.end();
        }
    }

    @WrapOperation(
            method = "applyBiomeDecoration",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean place(
            FeaturePlacer placer,
            PlacedFeature feature,
            RandomSource random,
            BlockPos pos,
            Operation<Boolean> original) {
        BopCausalTrace.enter(feature);
        boolean value = original.call(placer, feature, random, pos);
        BopCausalTrace.placed(value);
        return value;
    }
}
