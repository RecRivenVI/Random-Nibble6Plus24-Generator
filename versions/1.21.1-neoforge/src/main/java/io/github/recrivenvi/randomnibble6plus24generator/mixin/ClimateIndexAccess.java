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
    @Unique private volatile ThreadLocal<?> mosaic$nativeSearch;

    @Coerce
    @ModifyExpressionValue(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/biome/Climate$RTree;create(Ljava/util/List;)Lnet/minecraft/world/level/biome/Climate$RTree;"),
            require = 0)
    private Object mosaic$captureNativeIndex(@Coerce Object tree) {
        // RTree is a protected native type. Keep the original object and expose only its state.
        mosaic$nativeSearch = ((ClimateSearchAccess) tree).mosaic$lastResult();
        return tree;
    }

    @Override
    public ThreadLocal<?> mosaic$searchState() {
        return mosaic$nativeSearch;
    }
}
