package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree")
public interface ClimateSearchAccess {
    @Accessor("lastResult")
    ThreadLocal<?> mosaic$lastResult();
}
