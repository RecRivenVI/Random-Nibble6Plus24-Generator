package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NoiseChunk.class)
public interface NoiseChunkAccess {
    @Accessor("interpolators")
    List<NoiseChunk.NoiseInterpolator> mosaicInterpolators();
}
