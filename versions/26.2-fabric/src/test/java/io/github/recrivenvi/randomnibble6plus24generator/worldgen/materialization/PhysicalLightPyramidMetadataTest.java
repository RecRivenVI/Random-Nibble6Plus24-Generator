package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;

class PhysicalLightPyramidMetadataTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void recordsMinecraftTwentySixTwoPhysicalLightDependencies() {
        var initialize = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.INITIALIZE_LIGHT);
        var light = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.LIGHT);

        assertEquals(-1, initialize.blockStateWriteRadius());
        assertEquals(-1, light.blockStateWriteRadius());
        assertEquals(List.of(ChunkStatus.FEATURES), initialize.directDependencies().asList());
        assertEquals(10, initialize.accumulatedDependencies().getRadius());
        assertEquals(List.of(ChunkStatus.INITIALIZE_LIGHT, ChunkStatus.INITIALIZE_LIGHT),
                light.directDependencies().asList());
        assertEquals(11, light.accumulatedDependencies().getRadius());
        assertEquals(ChunkStatus.INITIALIZE_LIGHT, light.accumulatedDependencies().get(0));
        assertEquals(ChunkStatus.INITIALIZE_LIGHT, light.accumulatedDependencies().get(1));
        assertEquals(ChunkStatus.CARVERS, light.accumulatedDependencies().get(2));
        assertEquals(ChunkStatus.BIOMES, light.accumulatedDependencies().get(3));
        for (int radius = 4; radius <= 11; radius++) {
            assertEquals(ChunkStatus.STRUCTURE_STARTS, light.accumulatedDependencies().get(radius));
        }
    }
}
