package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LegacyRandomSource.class)
public interface LegacyRingRandomAccess {
    @Accessor("seed")
    AtomicLong mosaic$ringRandomState();
}
