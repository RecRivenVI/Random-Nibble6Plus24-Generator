package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;

class MosaicPhysicalGenerationPlanTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void coldInitializeLightDerivesOnlyCenterArtifactFromVanillaDependencies() {
        ChunkPos center = new ChunkPos(7, -9);
        MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(
                center, ChunkStatus.INITIALIZE_LIGHT);

        assertEquals(ChunkPyramid.GENERATION_PYRAMID
                .getStepTo(ChunkStatus.INITIALIZE_LIGHT)
                .accumulatedDependencies().getRadius(), plan.accumulatedRadius());
        assertEquals(java.util.Map.of(center, ChunkStatus.FEATURES), plan.materializationObligations());
    }

    @Test
    void coldLightDerivesExactlyThreeByThreeArtifactsFromVanillaDependencies() {
        ChunkPos center = new ChunkPos(7, -9);
        MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(center, ChunkStatus.LIGHT);

        assertEquals(11, plan.accumulatedRadius());
        assertEquals(9, plan.materializationObligations().size());
        Set<ChunkPos> expected = new java.util.HashSet<>();
        for (int z = center.z() - 1; z <= center.z() + 1; z++) {
            for (int x = center.x() - 1; x <= center.x() + 1; x++) expected.add(new ChunkPos(x, z));
        }
        assertEquals(expected, plan.materializationObligations().keySet());
        assertTrue(plan.materializationObligations().values().stream()
                .allMatch(status -> status == ChunkStatus.INITIALIZE_LIGHT));
    }

    @Test
    void outerRadiusTenAndElevenPrerequisitesDoNotBecomeArtifacts() {
        ChunkPos center = ChunkPos.ZERO;
        MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(center, ChunkStatus.LIGHT);

        assertTrue(plan.materializationObligations().keySet().stream()
                .allMatch(pos -> Math.max(Math.abs(pos.x()), Math.abs(pos.z())) <= 1));
    }

    @Test
    void lightPhysicalRequirementsRetainTheExactVanillaOuterStatusShape() {
        MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(ChunkPos.ZERO, ChunkStatus.LIGHT);
        java.util.Map<ChunkStatus, Long> counts = plan.physicalStatusRequirements().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        status -> status,
                        java.util.stream.Collectors.counting()));

        assertEquals(529, plan.physicalStatusRequirements().size());
        assertEquals(1L, counts.get(ChunkStatus.LIGHT));
        assertEquals(8L, counts.get(ChunkStatus.INITIALIZE_LIGHT));
        assertEquals(16L, counts.get(ChunkStatus.CARVERS));
        assertEquals(24L, counts.get(ChunkStatus.BIOMES));
        assertEquals(480L, counts.get(ChunkStatus.STRUCTURE_STARTS));
        assertEquals(9, plan.materializationObligations().size());
    }

    @Test
    void seedDependentPhysicalRequestStillPublishesCompleteFeaturesArtifact() {
        ChunkPos center = new ChunkPos(-3, 4);
        for (ChunkStatus status : java.util.List.of(
                ChunkStatus.STRUCTURE_STARTS,
                ChunkStatus.BIOMES,
                ChunkStatus.CARVERS,
                ChunkStatus.FEATURES)) {
            assertEquals(
                    java.util.Map.of(center, ChunkStatus.FEATURES),
                    MosaicPhysicalGenerationPlan.derive(center, status).materializationObligations());
        }
    }

    @Test
    void spawnAndFullRequestsRetainCanonicalArtifactObligations() {
        ChunkPos center = new ChunkPos(12, -14);
        for (ChunkStatus status : java.util.List.of(ChunkStatus.SPAWN, ChunkStatus.FULL)) {
            MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(center, status);
            assertTrue(plan.materializationObligations().containsKey(center));
            assertTrue(plan.materializationObligations().values().stream()
                    .allMatch(required -> !required.isBefore(ChunkStatus.FEATURES)));
        }
    }
}
