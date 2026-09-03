package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Bounded test/dev counters for Overlay V1. */
public final class MosaicStructureOverlayTrace {

    private static final ConcurrentHashMap<String, LongAdder> CALLS = new ConcurrentHashMap<>();

    private MosaicStructureOverlayTrace() {
    }

    static void record(String operation, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        CALLS.computeIfAbsent(operation + "@" + dimension.identifier() + ":" + chunkPos,
                ignored -> new LongAdder()).increment();
    }

    public static Map<String, Long> snapshot() {
        java.util.Map<String, Long> result = new java.util.TreeMap<>();
        CALLS.forEach((key, value) -> result.put(key, value.sum()));
        return Map.copyOf(result);
    }

    public static void reset() {
        CALLS.clear();
    }
}
