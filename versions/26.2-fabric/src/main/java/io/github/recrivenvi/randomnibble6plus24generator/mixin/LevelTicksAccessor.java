package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import it.unimi.dsi.fastutil.longs.Long2LongMap;

import net.minecraft.world.ticks.LevelTicks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelTicks.class)
public interface LevelTicksAccessor {

    @Accessor("nextTickForContainer")
    Long2LongMap randomnibble6plus24generator$getNextTickForContainer();
}
