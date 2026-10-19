package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.datafixers.util.Pair;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingScope;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeSource.class)
abstract class ConcentricBiomeSearchMixin {
    @Inject(
            method =
                    "findBiomeHorizontal(IIIILjava/util/function/Predicate;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/biome/Climate$Sampler;)Lcom/mojang/datafixers/util/Pair;",
            at = @At("HEAD"),
            cancellable = true)
    private void mosaic$relevantSearch(
            int x,
            int y,
            int z,
            int radius,
            Predicate<Holder<Biome>> preferred,
            RandomSource random,
            Climate.Sampler sampler,
            CallbackInfoReturnable<Pair<BlockPos, Holder<Biome>>> callback) {
        var scope = ConcentricRingScope.currentSearch();
        // Only a native supplier from a scoped local state can take this path. Native handles
        // null by retaining its initial slot; finish removes that proven out-of-domain slot.
        if (scope != null
                && scope.skipLookup((BiomeSource) (Object) this, x, y, z, radius, sampler))
            callback.setReturnValue(null);
    }
}
