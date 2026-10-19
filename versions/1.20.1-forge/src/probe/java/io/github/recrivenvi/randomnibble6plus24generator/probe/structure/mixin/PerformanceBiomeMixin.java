package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.mojang.datafixers.util.Pair;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeSource.class)
abstract class PerformanceBiomeMixin {
    @Inject(
            method =
                    "findBiomeHorizontal(IIIIILjava/util/function/Predicate;Lnet/minecraft/util/RandomSource;ZLnet/minecraft/world/level/biome/Climate$Sampler;)Lcom/mojang/datafixers/util/Pair;",
            at = @At("HEAD"))
    private void fullSearch(
            int x,
            int y,
            int z,
            int radius,
            int step,
            Predicate<Holder<Biome>> predicate,
            RandomSource random,
            boolean closest,
            Climate.Sampler sampler,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> ci) {
        if (LegacyPerformanceProbe.inRingTask()) LegacyPerformanceProbe.ring(true);
    }
}
