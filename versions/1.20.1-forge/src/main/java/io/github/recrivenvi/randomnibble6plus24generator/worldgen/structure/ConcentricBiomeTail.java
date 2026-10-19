package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.ClimateFitnessAccess;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.MultiNoiseParametersAccess;
import net.minecraft.world.level.biome.*;

/** Certifies a unique native final leaf; ambiguous queries must retain their complete search. */
public final class ConcentricBiomeTail {
    // This is an overflow-proof domain, not a clamp or an approximate biome decision.
    private static final long SAFE_COORDINATE = 1_000_000;
    private final MultiNoiseBiomeSource source;
    private final Climate.ParameterList<?> parameters;
    private final boolean bounded;

    public ConcentricBiomeTail(MultiNoiseBiomeSource source) {
        this.source = source;
        parameters = ((MultiNoiseParametersAccess) source).mosaic$parameters();
        bounded =
                parameters.getClass() == Climate.ParameterList.class
                        && !parameters.values().isEmpty()
                        && parameters.values().stream()
                                .allMatch(entry -> bounded(entry.getFirst()));
    }

    public boolean replay(Climate.TargetPoint point) {
        if (!bounded || !bounded(point)) return false;
        long minimum = Long.MAX_VALUE;
        int minima = 0;
        for (var entry : parameters.values()) {
            long distance =
                    ((ClimateFitnessAccess) (Object) entry.getFirst()).mosaic$fitness(point);
            if (distance < minimum) {
                minimum = distance;
                minima = 1;
            } else if (distance == minimum) minima++;
        }
        // Certification has not touched the native RTree. A false result preserves its exact
        // entry leaf, so the caller can execute the original search without resetting anything.
        if (minima != 1) return false;
        // Native RandomState.sampler strips NoiseChunk markers. Its point evaluation is pure;
        // the mutable search state is this ParameterList's thread-local leaf. With one minimum,
        // the native final query has the same exit leaf regardless of the intermediate queries.
        source.getNoiseBiome(point);
        return true;
    }

    private static boolean bounded(long value) {
        return value >= -SAFE_COORDINATE && value <= SAFE_COORDINATE;
    }

    private static boolean bounded(Climate.Parameter value) {
        return value.min() <= value.max() && bounded(value.min()) && bounded(value.max());
    }

    private static boolean bounded(Climate.ParameterPoint point) {
        return bounded(point.temperature())
                && bounded(point.humidity())
                && bounded(point.continentalness())
                && bounded(point.erosion())
                && bounded(point.depth())
                && bounded(point.weirdness())
                && bounded(point.offset());
    }

    private static boolean bounded(Climate.TargetPoint point) {
        return bounded(point.temperature())
                && bounded(point.humidity())
                && bounded(point.continentalness())
                && bounded(point.erosion())
                && bounded(point.depth())
                && bounded(point.weirdness());
    }
}
