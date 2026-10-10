package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import org.jspecify.annotations.Nullable;

/**
 * Target-local access implemented by {@link
 * net.minecraft.world.level.chunk.ChunkGeneratorStructureState}.
 */
public interface StructureCandidateStateAccess {
    void randomnibble6plus24generator$setCandidateScope(StructureCandidateScope scope);

    @Nullable StructureCandidateScope randomnibble6plus24generator$candidateScope();
}
