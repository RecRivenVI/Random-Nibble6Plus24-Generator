package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Small verification counter set; every generator operation still fails closed. */
public final class PhysicalMosaicTrace {

    private static final ConcurrentHashMap<String, LongAdder> GENERATOR_CALLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> FORBIDDEN_STATUS_CALLS = new ConcurrentHashMap<>();

    private PhysicalMosaicTrace() {
    }

    public static void recordGeneratorCall(String operation) {
        GENERATOR_CALLS.computeIfAbsent(operation, ignored -> new LongAdder()).increment();
    }

    public static void rejectForbiddenStatus(ChunkStatus status) {
        FORBIDDEN_STATUS_CALLS.computeIfAbsent(status.getName(), ignored -> new LongAdder()).increment();
        throw new IllegalStateException("Phase 3A forbids physical Mosaic status " + status);
    }

    public static Snapshot snapshot() {
        return new Snapshot(copy(GENERATOR_CALLS), copy(FORBIDDEN_STATUS_CALLS));
    }

    public static void reset() {
        GENERATOR_CALLS.clear();
        FORBIDDEN_STATUS_CALLS.clear();
    }

    private static Map<String, Long> copy(ConcurrentHashMap<String, LongAdder> source) {
        java.util.Map<String, Long> result = new java.util.TreeMap<>();
        source.forEach((key, value) -> result.put(key, value.sum()));
        return Map.copyOf(result);
    }

    public record Snapshot(Map<String, Long> generatorCalls, Map<String, Long> forbiddenStatusCalls) {
        public long totalGeneratorCalls() {
            return generatorCalls.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
