package io.github.recrivenvi.randomnibble6plus24generator.performance;

import java.util.*;

/** Bounded diagnostic token history. No carrier, piece or world is retained or reused. */
public final class StructureReuseHistory {
    private final Map<Long, List<Object>> positions = new HashMap<>();
    private long evaluations, nativeNanos, repeat2, repeat4, repeat16;
    private long nanos2, nanos4, nanos16;
    private int maximumStates;

    public void observe(long position, Object input, long evaluationNanos) {
        var states = positions.computeIfAbsent(position, key -> new ArrayList<>());
        int age = -1;
        for (int index = 0; index < states.size(); index++)
            if (states.get(index) == input) {
                age = index;
                break;
            }
        evaluations++;
        nativeNanos += evaluationNanos;
        if (age >= 0) {
            repeat16++;
            nanos16 += evaluationNanos;
            if (age < 4) {
                repeat4++;
                nanos4 += evaluationNanos;
            }
            if (age < 2) {
                repeat2++;
                nanos2 += evaluationNanos;
            }
        }
        if (age >= 0) states.remove(age);
        states.add(0, input);
        if (states.size() > 16) states.remove(16);
        maximumStates = Math.max(maximumStates, states.size());
    }

    public void invalidate(Collection<Long> sources) {
        sources.forEach(positions::remove);
    }

    public Map<String, Long> counters() {
        return Map.ofEntries(
                Map.entry("evaluations", evaluations),
                Map.entry("nativeNanos", nativeNanos),
                Map.entry("repeat2", repeat2),
                Map.entry("repeat4", repeat4),
                Map.entry("repeat16", repeat16),
                Map.entry("nanos2", nanos2),
                Map.entry("nanos4", nanos4),
                Map.entry("nanos16", nanos16),
                Map.entry("maximumStates", (long) maximumStates));
    }

    public int retainedPositions() {
        return positions.size();
    }

    public void clear() {
        positions.clear();
    }
}
