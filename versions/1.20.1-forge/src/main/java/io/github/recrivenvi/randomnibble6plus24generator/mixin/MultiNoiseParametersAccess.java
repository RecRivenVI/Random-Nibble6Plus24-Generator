package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiNoiseBiomeSource.class)
public interface MultiNoiseParametersAccess {
    @Invoker("parameters")
    Climate.ParameterList<Holder<Biome>> mosaic$parameters();
}
