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
 * Immutable physical dependency plan derived from the active Vanilla generation pyramid.
 * Only positions whose required physical status is FEATURES or later need an isolated
 * canonical Artifact; outer scheduler prerequisites remain physical pass-through holders.
 */
public record MosaicPhysicalGenerationPlan(
        ChunkPos requestedPos,
        ChunkStatus requestedStatus,
        int accumulatedRadius,
        Map<ChunkPos, ChunkStatus> materializationObligations,
        Map<ChunkPos, ChunkStatus> physicalStatusRequirements) {

    public MosaicPhysicalGenerationPlan {
        materializationObligations = Collections.unmodifiableMap(
                new LinkedHashMap<>(materializationObligations));
        physicalStatusRequirements = Collections.unmodifiableMap(
                new LinkedHashMap<>(physicalStatusRequirements));
    }

    public static MosaicPhysicalGenerationPlan derive(ChunkPos requestedPos, ChunkStatus requestedStatus) {
        if (requestedStatus == ChunkStatus.EMPTY) {
            return new MosaicPhysicalGenerationPlan(requestedPos, requestedStatus, 0, Map.of(), Map.of());
        }
        if (requestedStatus.isAfter(ChunkStatus.LIGHT)) {
            throw new IllegalArgumentException("Phase 3B cannot plan physical status " + requestedStatus);
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
            // Mosaic never executes a partial seed-dependent physical pipeline. Any non-empty
            // request for a missing target publishes the complete pre-light Artifact instead.
            obligations.put(requestedPos, ChunkStatus.FEATURES);
        } else {
            for (int radius = 0; radius <= dependencies.getRadius(); radius++) {
                ChunkStatus required = dependencies.get(radius);
                if (required.isBefore(ChunkStatus.FEATURES)) continue;
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
