package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Finite physical requirements from this version's native pyramid, not another Target's radii. */
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
        var dependencies =
                ChunkPyramid.GENERATION_PYRAMID.getStepTo(status).accumulatedDependencies();
        Map<ChunkPos, ChunkStatus> requirements = new LinkedHashMap<>();
        Map<ChunkPos, ChunkStatus> artifacts = new LinkedHashMap<>();
        for (int z = -dependencies.getRadius(); z <= dependencies.getRadius(); z++)
            for (int x = -dependencies.getRadius(); x <= dependencies.getRadius(); x++) {
                var pos = new ChunkPos(owner.x + x, owner.z + z);
                var required = dependencies.get(Math.max(Math.abs(x), Math.abs(z)));
                if (x == 0 && z == 0 && status.isAfter(required)) required = status;
                requirements.put(pos, required);
                // Physical BIOMES futures are observable outside the light frontier. Their
                // obligations carry actual data; unsupported environments retain full Artifacts.
                if (required.isOrAfter(ChunkStatus.BIOMES)) artifacts.put(pos, required);
            }
        // An explicit seed-dependent physical request is satisfied by a complete pre-light
        // result, never by running MosaicChunkGenerator's forbidden master-seed methods.
        if (status.isOrBefore(ChunkStatus.FEATURES) && status != ChunkStatus.BIOMES)
            artifacts.put(owner, ChunkStatus.FEATURES);
        return new MosaicPhysicalGenerationPlan(owner, status, requirements, artifacts);
    }
}
