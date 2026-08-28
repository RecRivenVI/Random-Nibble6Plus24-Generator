package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

public record SurfaceGenerationMetrics(
        long contextSetupNanos,
        long structureStateNanos,
        long elapsedNanos,
        int virtualChunkCount,
        int virtualStorageScanCount,
        long suppressedPhysicalPoiUpdates,
        int physicalLoadedChunksBefore,
        int physicalLoadedChunksAfter) {
}
