package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;

class FeaturePyramidMetadataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void recordsMinecraftTwentySixTwoFeatureDependencyMetadata() {
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);

        assertEquals(1, step.blockStateWriteRadius());
        assertEquals(10, step.accumulatedDependencies().getRadius());
        System.out.println("FEATURES directRadius=" + step.directDependencies().getRadius());
        for (int radius = 0; radius <= step.directDependencies().getRadius(); radius++) {
            System.out.println("FEATURES direct[" + radius + "]=" + step.directDependencies().get(radius));
        }
        for (int radius = 0; radius <= step.accumulatedDependencies().getRadius(); radius++) {
            System.out.println("FEATURES accumulated[" + radius + "]=" + step.accumulatedDependencies().get(radius));
        }
    }
}
