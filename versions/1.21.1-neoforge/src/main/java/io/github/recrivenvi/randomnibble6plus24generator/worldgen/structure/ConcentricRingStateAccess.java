package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import javax.annotation.Nullable;

/**
 * A scope belongs to one isolated {@link
 * net.minecraft.world.level.chunk.ChunkGeneratorStructureState}, never to the physical dimension
 * state.
 */
public interface ConcentricRingStateAccess {
    void randomnibble6plus24generator$setRingScope(ConcentricRingScope scope);

    @Nullable
    ConcentricRingScope randomnibble6plus24generator$ringScope();
}
