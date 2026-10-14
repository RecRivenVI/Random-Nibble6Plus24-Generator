package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.lang.reflect.Constructor;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MosaicPhysicalMaterializerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    @SuppressWarnings("unchecked")
    void physicalCompletionAndFailureCleanOnlyTheirIndexedDependencies() throws Exception {
        MosaicPhysicalMaterializer.resetVerificationState();
        var type = MosaicPhysicalMaterializer.class;
        var indexField = type.getDeclaredField("PHYSICAL_PLANS");
        var allowancesField = type.getDeclaredField("PHYSICAL_STATUS_ALLOWANCES");
        var obligationsField = type.getDeclaredField("MATERIALIZATION_OBLIGATIONS");
        indexField.setAccessible(true);
        allowancesField.setAccessible(true);
        obligationsField.setAccessible(true);
        var index =
                (PhysicalGenerationPlanIndex<MosaicPhysicalMaterializer.GenerationKey>)
                        indexField.get(null);
        var allowances =
                (Map<
                                MosaicPhysicalMaterializer.GenerationKey,
                                net.minecraft.world.level.chunk.status.ChunkStatus>)
                        allowancesField.get(null);
        var obligations =
                (Map<
                                MosaicPhysicalMaterializer.GenerationKey,
                                net.minecraft.world.level.chunk.status.ChunkStatus>)
                        obligationsField.get(null);
        var full = net.minecraft.world.level.chunk.status.ChunkStatus.FULL;
        var light = net.minecraft.world.level.chunk.status.ChunkStatus.LIGHT;
        var a =
                new MosaicPhysicalMaterializer.GenerationKey(
                        null, Level.OVERWORLD, new ChunkPos(1, 0), 2, 1, 2, 1);
        var b =
                new MosaicPhysicalMaterializer.GenerationKey(
                        null, Level.OVERWORLD, new ChunkPos(2, 0), 2, 1, 2, 1);
        var shared =
                new MosaicPhysicalMaterializer.GenerationKey(
                        null, Level.OVERWORLD, ChunkPos.ZERO, 2, 1, 2, 1);
        var nether =
                new MosaicPhysicalMaterializer.GenerationKey(
                        null, Level.NETHER, ChunkPos.ZERO, 2, 1, 2, 1);
        var complete = type.getDeclaredMethod("completePhysicalRequest", a.getClass());
        var failure = type.getDeclaredMethod("clearPlansContaining", a.getClass());
        complete.setAccessible(true);
        failure.setAccessible(true);
        try {
            index.replace(a, Map.of(shared, full, a, full));
            index.replace(b, Map.of(shared, light));
            index.replace(nether, Map.of(nether, full));
            allowances.put(shared, full);
            allowances.put(a, full);
            allowances.put(nether, full);
            obligations.put(shared, full);
            obligations.put(a, full);
            complete.invoke(null, a);
            assertEquals(light, allowances.get(shared));
            assertEquals(full, obligations.get(shared));
            assertFalse(allowances.containsKey(a));
            assertFalse(obligations.containsKey(a));
            failure.invoke(null, shared);
            assertFalse(allowances.containsKey(shared));
            assertFalse(obligations.containsKey(shared));
            assertEquals(full, allowances.get(nether));
            assertEquals(1, index.planCount());
            complete.invoke(null, nether);
            assertEquals(0, index.dependencyCount());
            index.replace(a, Map.of(shared, full));
            var close =
                    type.getDeclaredMethod(
                            "clearClosedServer", net.minecraft.server.MinecraftServer.class);
            close.setAccessible(true);
            close.invoke(null, new Object[] {null});
            assertEquals(0, index.planCount());
            assertEquals(0, index.dependencyCount());
        } finally {
            MosaicPhysicalMaterializer.resetVerificationState();
        }
    }

    @Test
    void rejectsEveryWrongArtifactProvenanceAxisBeforePublish() throws Exception {
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        ChunkPos requested = new ChunkPos(4, -5);
        long expectedSeed = 123L;
        CanonicalChunkArtifact valid =
                artifact("minecraft:overworld", requested, expectedSeed, 2, 1, 1);

        assertDoesNotThrow(
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.OVERWORLD, requested, expectedSeed, profile, valid));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.NETHER, requested, expectedSeed, profile, valid));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.OVERWORLD,
                                new ChunkPos(5, -5),
                                expectedSeed,
                                profile,
                                valid));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.OVERWORLD, requested, expectedSeed + 1, profile, valid));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.OVERWORLD,
                                requested,
                                expectedSeed,
                                profile,
                                artifact("minecraft:overworld", requested, expectedSeed, 3, 1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.OVERWORLD,
                                requested,
                                expectedSeed,
                                profile,
                                artifact("minecraft:overworld", requested, expectedSeed, 2, 2, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MosaicPhysicalMaterializer.validateProvenance(
                                Level.OVERWORLD,
                                requested,
                                expectedSeed,
                                profile,
                                artifact("minecraft:overworld", requested, expectedSeed, 2, 1, 2)));
    }

    private static CanonicalChunkArtifact artifact(
            String dimension,
            ChunkPos pos,
            long localSeed,
            int format,
            int seedAlgorithm,
            int featureOrdering)
            throws Exception {
        Constructor<CanonicalChunkArtifact> constructor =
                CanonicalChunkArtifact.class.getDeclaredConstructor(
                        String.class,
                        ChunkPos.class,
                        int.class,
                        int.class,
                        long.class,
                        MosaicWorldProfile.class,
                        byte[].class,
                        long[].class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                dimension,
                pos,
                -64,
                384,
                localSeed,
                new MosaicWorldProfile(format, seedAlgorithm, featureOrdering, 1, Level.OVERWORLD),
                new byte[] {1},
                new long[0]);
    }
}
