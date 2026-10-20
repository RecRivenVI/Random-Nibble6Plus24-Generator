package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.function.BiConsumer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Native terrain entry and stored palettes, with no FEATURES or virtual chunk holders. */
public final class DetachedTerrainInputs {
    private DetachedTerrainInputs() {}

    public static LightweightTerrainGenerator.Result generate(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        return LightweightTerrainGenerator.generate(
                context,
                structures,
                (stage, chunk) -> {
                    // A persisted native prerequisite retains the exact reference container
                    // iteration.
                    if (stage == ChunkStatus.STRUCTURE_REFERENCES)
                        structures.copyNativeReferencesTo(chunk);
                    checkpoint.accept(stage, chunk);
                });
    }
}
