package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.NativeBiomeSearchState;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(Climate.ParameterList.class)
abstract class ClimateIndexAccess implements NativeBiomeSearchState.Owner {
    @Unique private ThreadLocal<?> mosaic$nativeCursor;

    @Coerce
    @ModifyExpressionValue(
            method = "<init>(Ljava/util/List;I)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/biome/Climate$RTree;create(Ljava/util/List;I)Lnet/minecraft/world/level/biome/Climate$RTree;"),
            require = 0)
    private Object mosaic$captureIndex(@Coerce Object original) {
        // 26.3's private constructor serves both normal and rebuilt native indexes.
        mosaic$nativeCursor = ((ClimateSearchAccess) original).mosaic$lastResult();
        return original;
    }

    @Override
    public ThreadLocal<?> mosaic$nativeBiomeCursor() {
        return mosaic$nativeCursor;
    }
}
