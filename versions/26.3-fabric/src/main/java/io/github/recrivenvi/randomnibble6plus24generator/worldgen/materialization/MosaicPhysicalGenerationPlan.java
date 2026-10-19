package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/**
 * Immutable physical dependency plan derived from the active Vanilla generation pyramid. Only
 * positions whose required physical status supplies biomes need an isolated canonical Artifact.
 * Earlier structure-only scheduler prerequisites remain physical pass-through holders.
 */
public record MosaicPhysicalGenerationPlan(
        ChunkPos requestedPos,
        ChunkStatus requestedStatus,
        int accumulatedRadius,
        Map<ChunkPos, ChunkStatus> materializationObligations,
        Map<ChunkPos, ChunkStatus> physicalStatusRequirements) {

    public MosaicPhysicalGenerationPlan {
        materializationObligations =
                Collections.unmodifiableMap(new LinkedHashMap<>(materializationObligations));
        physicalStatusRequirements =
                Collections.unmodifiableMap(new LinkedHashMap<>(physicalStatusRequirements));
    }

    public static MosaicPhysicalGenerationPlan derive(
            ChunkPos requestedPos, ChunkStatus requestedStatus) {
        return derive(requestedPos, requestedStatus, false);
    }

    public static MosaicPhysicalGenerationPlan derive(
            ChunkPos requestedPos, ChunkStatus requestedStatus, boolean partialBiomes) {
        if (requestedStatus == ChunkStatus.EMPTY) {
            return new MosaicPhysicalGenerationPlan(
                    requestedPos, requestedStatus, 0, Map.of(), Map.of());
        }
        if (requestedStatus.isAfter(ChunkStatus.FULL)) {
            throw new IllegalArgumentException(
                    "Phase 3C1 cannot plan physical status " + requestedStatus);
        }

        ChunkStep step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(requestedStatus);
        ChunkDependencies dependencies = step.accumulatedDependencies();
        Map<ChunkPos, ChunkStatus> obligations = new LinkedHashMap<>();
        Map<ChunkPos, ChunkStatus> requirements = new LinkedHashMap<>();

        for (int radius = 0; radius <= dependencies.getRadius(); radius++) {
            ChunkStatus required = dependencies.get(radius);
            addSquareRing(requirements, requestedPos, radius, required);
        }
        requirements.merge(requestedPos, requestedStatus, MosaicPhysicalGenerationPlan::later);

        if (requestedStatus.isOrBefore(ChunkStatus.FEATURES)) {
            // Legacy requests retain the complete Artifact. A validated B capability may answer
            // just BIOMES with native S(C) data; terrain/features still require the whole Artifact.
            obligations.put(
                    requestedPos,
                    partialBiomes && requestedStatus == ChunkStatus.BIOMES
                            ? ChunkStatus.BIOMES
                            : ChunkStatus.FEATURES);
        } else if (requestedStatus.isOrBefore(ChunkStatus.LIGHT)) {
            for (int radius = 0; radius <= dependencies.getRadius(); radius++) {
                ChunkStatus required = dependencies.get(radius);
                if (required.isBefore(ChunkStatus.BIOMES)) continue;
                addSquareRing(obligations, requestedPos, radius, required);
            }
        } else {
            // SPAWN/FULL also expose physical biome dependencies to runtime environment queries.
            // Those holders must contain canonical data before advertising a BIOMES future.
            obligations.put(requestedPos, ChunkStatus.FEATURES);
            for (int radius = 0; radius <= dependencies.getRadius(); radius++) {
                ChunkStatus required = dependencies.get(radius);
                if (required.isBefore(ChunkStatus.BIOMES)) continue;
                addSquareRing(obligations, requestedPos, radius, required);
            }
        }
        return new MosaicPhysicalGenerationPlan(
                requestedPos, requestedStatus, dependencies.getRadius(), obligations, requirements);
    }

    private static void addSquareRing(
            Map<ChunkPos, ChunkStatus> obligations,
            ChunkPos center,
            int radius,
            ChunkStatus required) {
        if (radius == 0) {
            obligations.merge(center, required, MosaicPhysicalGenerationPlan::later);
            return;
        }
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                obligations.merge(
                        new ChunkPos(center.x() + dx, center.z() + dz),
                        required,
                        MosaicPhysicalGenerationPlan::later);
            }
        }
    }

    private static ChunkStatus later(ChunkStatus left, ChunkStatus right) {
        return right.isAfter(left) ? right : left;
    }
}
