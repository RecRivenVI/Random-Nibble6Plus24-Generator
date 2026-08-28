package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

public record CarverTrace(
        long observedWorldSeed,
        int carverStageInvocationCount,
        int sourceChunkCount,
        int configuredCarverCount,
        int changedBlockCount,
        int carvingMaskBitCount) {
}
