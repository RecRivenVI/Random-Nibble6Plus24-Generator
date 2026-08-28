package io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;

class CanonicalChunkArtifactStructureTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void artifactInstanceFieldsAreDetachedValuesOnly() {
        assertTrue(Arrays.stream(CanonicalChunkArtifact.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> field.getType().isPrimitive()
                        || field.getType() == String.class
                        || field.getType() == byte[].class
                        || field.getType() == long[].class));
        assertFalse(Arrays.stream(CanonicalChunkArtifact.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("masterSeed")));
    }

    @Test
    void transportBuffersAreDefensivelyCopied() {
        CanonicalChunkArtifact artifact = CanonicalChunkArtifact.fromDetachedTransport(
                "minecraft:overworld", 2, -3, -64, 384, 123L,
                2, 1, 1, new byte[] {1, 2, 3}, new long[] {4L, 5L});
        String fingerprint = artifact.rawFingerprint();

        byte[] payload = artifact.encodedPayloadCopy();
        long[] positions = artifact.instantiatedBlockEntityPositionsCopy();
        payload[0] = 99;
        positions[0] = 99L;

        assertArrayEquals(new byte[] {1, 2, 3}, artifact.encodedPayloadCopy());
        assertArrayEquals(new long[] {4L, 5L}, artifact.instantiatedBlockEntityPositionsCopy());
        assertTrue(fingerprint.equals(artifact.rawFingerprint()));
    }
}
