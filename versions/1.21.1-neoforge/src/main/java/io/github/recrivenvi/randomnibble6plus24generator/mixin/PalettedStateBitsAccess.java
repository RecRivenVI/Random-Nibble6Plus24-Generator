package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PalettedContainer.Strategy.class)
public interface PalettedStateBitsAccess {
    @Invoker("calculateBitsForSerialization")
    int mosaic$serializedBits(IdMap<?> values, int paletteSize);
}
