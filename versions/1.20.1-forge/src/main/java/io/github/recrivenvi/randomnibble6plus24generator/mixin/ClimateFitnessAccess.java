package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Climate.ParameterPoint.class)
public interface ClimateFitnessAccess {
    @Invoker("fitness")
    long mosaic$fitness(Climate.TargetPoint point);
}
