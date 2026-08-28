package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Constructor;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicPhysicalMaterializerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void rejectsEveryWrongArtifactProvenanceAxisBeforePublish() throws Exception {
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        ChunkPos requested = new ChunkPos(4, -5);
        long expectedSeed = 123L;
        CanonicalChunkArtifact valid = artifact(
                "minecraft:overworld", requested, expectedSeed, 2, 1, 1);

        assertDoesNotThrow(() -> MosaicPhysicalMaterializer.validateProvenance(
                Level.OVERWORLD, requested, expectedSeed, profile, valid));
        assertThrows(IllegalArgumentException.class, () -> MosaicPhysicalMaterializer.validateProvenance(
                Level.NETHER, requested, expectedSeed, profile, valid));
        assertThrows(IllegalArgumentException.class, () -> MosaicPhysicalMaterializer.validateProvenance(
                Level.OVERWORLD, new ChunkPos(5, -5), expectedSeed, profile, valid));
        assertThrows(IllegalArgumentException.class, () -> MosaicPhysicalMaterializer.validateProvenance(
                Level.OVERWORLD, requested, expectedSeed + 1, profile, valid));
        assertThrows(IllegalArgumentException.class, () -> MosaicPhysicalMaterializer.validateProvenance(
                Level.OVERWORLD, requested, expectedSeed, profile,
                artifact("minecraft:overworld", requested, expectedSeed, 3, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> MosaicPhysicalMaterializer.validateProvenance(
                Level.OVERWORLD, requested, expectedSeed, profile,
                artifact("minecraft:overworld", requested, expectedSeed, 2, 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> MosaicPhysicalMaterializer.validateProvenance(
                Level.OVERWORLD, requested, expectedSeed, profile,
                artifact("minecraft:overworld", requested, expectedSeed, 2, 1, 2)));
    }

    private static CanonicalChunkArtifact artifact(
            String dimension,
            ChunkPos pos,
            long localSeed,
            int format,
            int seedAlgorithm,
            int featureOrdering) throws Exception {
        Constructor<CanonicalChunkArtifact> constructor = CanonicalChunkArtifact.class.getDeclaredConstructor(
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
