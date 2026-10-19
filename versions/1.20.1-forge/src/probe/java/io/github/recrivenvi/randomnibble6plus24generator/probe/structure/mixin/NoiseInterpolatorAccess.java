package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.NoiseInterpolator.class)
public interface NoiseInterpolatorAccess {
    @Accessor("slice0")
    double[][] mosaicSlice0();

    @Accessor("slice1")
    double[][] mosaicSlice1();
}
