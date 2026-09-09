package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureCandidateScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureCandidateStateAccess;

@Mixin(ChunkGeneratorStructureState.class)
abstract class StructureCandidateStateMixin implements StructureCandidateStateAccess {
    @Unique private @Nullable StructureCandidateScope randomnibble6plus24generator$candidates;

    @Override public void randomnibble6plus24generator$setCandidateScope(StructureCandidateScope scope) {
        if (randomnibble6plus24generator$candidates != null) throw new IllegalStateException("Candidate scope already owned");
        randomnibble6plus24generator$candidates = java.util.Objects.requireNonNull(scope);
    }

    @Override public @Nullable StructureCandidateScope randomnibble6plus24generator$candidateScope() {
        return randomnibble6plus24generator$candidates;
    }
}
