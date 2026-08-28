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
        long localUncachedBiomeReads,
        List<String> featureVisibleBiomeSequence,
        Map<String, Integer> virtualStatusDistribution,
        Map<String, String> featureWriteSummary,
        Map<String, Long> physicalLevelEscapeSummary) {

    public FeatureGenerationTrace {
        requestedWriters = List.copyOf(requestedWriters);
        completedWriters = List.copyOf(completedWriters);
        chunksAtOrBeyondFeatures = Set.copyOf(chunksAtOrBeyondFeatures);
        featureVisibleBiomeSequence = List.copyOf(featureVisibleBiomeSequence);
        virtualStatusDistribution = Map.copyOf(virtualStatusDistribution);
        featureWriteSummary = Map.copyOf(featureWriteSummary);
        physicalLevelEscapeSummary = Map.copyOf(physicalLevelEscapeSummary);
    }
}
