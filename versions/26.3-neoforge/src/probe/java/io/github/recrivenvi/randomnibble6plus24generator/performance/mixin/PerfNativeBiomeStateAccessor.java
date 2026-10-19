package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.NativeBiomeSearchState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = NativeBiomeSearchState.class, remap = false)
public interface PerfNativeBiomeStateAccessor {
    @Invoker("capture")
    Object mosaic$captureInput();
}
