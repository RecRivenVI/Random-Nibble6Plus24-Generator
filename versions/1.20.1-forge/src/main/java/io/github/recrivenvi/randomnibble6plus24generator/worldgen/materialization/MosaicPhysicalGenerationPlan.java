package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

/** This Target's native getRange/getDependencyStatus closure; no synthetic ChunkPyramid. */
public record MosaicPhysicalGenerationPlan(
        ChunkPos owner,
        ChunkStatus status,
        Map<ChunkPos, ChunkStatus> requirements,
        Map<ChunkPos, ChunkStatus> artifacts) {
    public MosaicPhysicalGenerationPlan {
        requirements = Collections.unmodifiableMap(new LinkedHashMap<>(requirements));
        artifacts = Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
    }

    public static MosaicPhysicalGenerationPlan derive(ChunkPos owner, ChunkStatus status) {
        if (status == ChunkStatus.EMPTY)
            return new MosaicPhysicalGenerationPlan(owner, status, Map.of(), Map.of());
        Map<ChunkPos, ChunkStatus> requirements = new LinkedHashMap<>();
        accumulate(owner, status, requirements, new HashSet<>());
        Map<ChunkPos, ChunkStatus> artifacts = new LinkedHashMap<>();
        requirements.forEach(
                (position, required) -> {
                    if (required.isOrAfter(ChunkStatus.BIOMES)) artifacts.put(position, required);
                });
        // A seed-dependent explicit request must never enter the physical master-seed generator.
        if (status.isOrAfter(ChunkStatus.STRUCTURE_STARTS)
                && ChunkStatus.FEATURES.isOrAfter(status))
            artifacts.put(owner, ChunkStatus.FEATURES);
        return new MosaicPhysicalGenerationPlan(owner, status, requirements, artifacts);
    }

    private record Task(ChunkPos owner, ChunkStatus status) {}

    private static void accumulate(
            ChunkPos owner,
            ChunkStatus status,
            Map<ChunkPos, ChunkStatus> requirements,
            Set<Task> expanded) {
        if (!expanded.add(new Task(owner, status))) return;
        requirements.merge(
                owner, status, (previous, next) -> previous.isOrAfter(next) ? previous : next);
        if (status == ChunkStatus.EMPTY) return;
        int radius = status.getRange();
        if (radius < 0)
            throw new IllegalStateException("Negative native generation dependency range");
        for (int z = -radius; z <= radius; z++)
            for (int x = -radius; x <= radius; x++) {
                int distance = Math.max(Math.abs(x), Math.abs(z));
                // This is 1.20.1 ChunkMap.getDependencyStatus, not a later-version radius table.
                var dependency =
                        distance == 0
                                ? status.getParent()
                                : ChunkStatus.getStatusAroundFullChunk(
                                        ChunkStatus.getDistance(status) + distance);
                if (dependency == status)
                    throw new IllegalStateException("Native generation dependency did not descend");
                accumulate(
                        new ChunkPos(owner.x + x, owner.z + z), dependency, requirements, expanded);
            }
    }
}
