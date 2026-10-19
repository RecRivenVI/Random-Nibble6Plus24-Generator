package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import javax.annotation.Nullable;

public interface ConcentricRingStateAccess {
    void mosaic$setRingScope(ConcentricRingScope scope);

    @Nullable
    ConcentricRingScope mosaic$ringScope();
}
