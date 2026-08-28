package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Set;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public record SurfaceGenerationRun(
        ChunkAccess targetChunk,
        SurfaceGenerationMetrics metrics,
        Set<ChunkStatus> executedStages) {
}
