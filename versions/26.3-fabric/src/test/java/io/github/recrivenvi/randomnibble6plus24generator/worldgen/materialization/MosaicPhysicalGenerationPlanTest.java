package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MosaicPhysicalGenerationPlanTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void coldInitializeLightMaterializesEveryBiomeBearingDependency() {
        ChunkPos center = new ChunkPos(7, -9);
        MosaicPhysicalGenerationPlan plan =
                MosaicPhysicalGenerationPlan.derive(center, ChunkStatus.INITIALIZE_LIGHT);

        assertEquals(
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.INITIALIZE_LIGHT)
                        .accumulatedDependencies()
                        .getRadius(),
                plan.accumulatedRadius());
        assertEquals(
                plan.physicalStatusRequirements().entrySet().stream()
                        .filter(entry -> entry.getValue().isOrAfter(ChunkStatus.BIOMES))
                        .map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()),
                plan.materializationObligations().keySet());
    }

    @Test
    void coldLightDerivesExactlySevenBySevenBiomeBearingArtifacts() {
        ChunkPos center = new ChunkPos(7, -9);
        MosaicPhysicalGenerationPlan plan =
                MosaicPhysicalGenerationPlan.derive(center, ChunkStatus.LIGHT);

        assertEquals(11, plan.accumulatedRadius());
        assertEquals(49, plan.materializationObligations().size());
        Set<ChunkPos> expected = new java.util.HashSet<>();
        for (int z = center.z() - 3; z <= center.z() + 3; z++) {
            for (int x = center.x() - 3; x <= center.x() + 3; x++) expected.add(new ChunkPos(x, z));
        }
        assertEquals(expected, plan.materializationObligations().keySet());
        assertTrue(
                plan.materializationObligations().values().stream()
                        .allMatch(status -> status.isOrAfter(ChunkStatus.BIOMES)));
    }

    @Test
    void outerRadiusTenAndElevenPrerequisitesDoNotBecomeArtifacts() {
        ChunkPos center = ChunkPos.ZERO;
        MosaicPhysicalGenerationPlan plan =
                MosaicPhysicalGenerationPlan.derive(center, ChunkStatus.LIGHT);

        assertTrue(
                plan.materializationObligations().keySet().stream()
                        .allMatch(pos -> Math.max(Math.abs(pos.x()), Math.abs(pos.z())) <= 3));
    }

    @Test
    void lightPhysicalRequirementsRetainTheExactVanillaOuterStatusShape() {
        MosaicPhysicalGenerationPlan plan =
                MosaicPhysicalGenerationPlan.derive(ChunkPos.ZERO, ChunkStatus.LIGHT);
        java.util.Map<ChunkStatus, Long> counts =
                plan.physicalStatusRequirements().values().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        status -> status, java.util.stream.Collectors.counting()));

        assertEquals(529, plan.physicalStatusRequirements().size());
        assertEquals(1L, counts.get(ChunkStatus.LIGHT));
        assertEquals(8L, counts.get(ChunkStatus.INITIALIZE_LIGHT));
        assertEquals(16L, counts.get(ChunkStatus.TERRAIN));
        assertEquals(24L, counts.get(ChunkStatus.BIOMES));
        assertEquals(480L, counts.get(ChunkStatus.STRUCTURE_STARTS));
        assertEquals(49, plan.materializationObligations().size());
    }

    @Test
    void seedDependentPhysicalRequestStillPublishesCompleteFeaturesArtifact() {
        ChunkPos center = new ChunkPos(-3, 4);
        for (ChunkStatus status :
                java.util.List.of(
                        ChunkStatus.STRUCTURE_STARTS,
                        ChunkStatus.BIOMES,
                        ChunkStatus.TERRAIN,
                        ChunkStatus.FEATURES)) {
            assertEquals(
                    java.util.Map.of(center, ChunkStatus.FEATURES),
                    MosaicPhysicalGenerationPlan.derive(center, status)
                            .materializationObligations());
        }
    }

    @Test
    void spawnAndFullRequestsRetainCanonicalArtifactObligations() {
        ChunkPos center = new ChunkPos(12, -14);
        for (ChunkStatus status : java.util.List.of(ChunkStatus.SPAWN, ChunkStatus.FULL)) {
            MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(center, status);
            assertTrue(plan.materializationObligations().containsKey(center));
            assertTrue(
                    plan.materializationObligations().values().stream()
                            .allMatch(required -> !required.isBefore(ChunkStatus.BIOMES)));
        }
    }
}
