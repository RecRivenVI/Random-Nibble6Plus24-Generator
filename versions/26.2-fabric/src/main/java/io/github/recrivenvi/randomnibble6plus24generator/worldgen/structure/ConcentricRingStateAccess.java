package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import org.jspecify.annotations.Nullable;

/** A scope belongs to one isolated structure state, never to the physical dimension state. */
public interface ConcentricRingStateAccess {
    void randomnibble6plus24generator$setRingScope(ConcentricRingScope scope);

    @Nullable ConcentricRingScope randomnibble6plus24generator$ringScope();
}
