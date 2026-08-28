package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.ChunkPos;

public record FeatureGenerationTrace(
        int orderingAlgorithmVersion,
        int blockStateWriteRadius,
        List<ChunkPos> requestedWriters,
        List<ChunkPos> completedWriters,
        Set<ChunkPos> chunksAtOrBeyondFeatures,
        int maxConcurrentFeatureWriters,
        long observedWorldSeed,
        long paleMossGeneratorRedirects,
        long cappedProcessorSeedRedirects,
        long decorationSeedReads,
        long featureSeedInvocationCount,
        long featureSeedSequenceHash,
        Map<String, String> featureWriteSummary) {

    public FeatureGenerationTrace {
        requestedWriters = List.copyOf(requestedWriters);
        completedWriters = List.copyOf(completedWriters);
        chunksAtOrBeyondFeatures = Set.copyOf(chunksAtOrBeyondFeatures);
        featureWriteSummary = Map.copyOf(featureWriteSummary);
    }
}
