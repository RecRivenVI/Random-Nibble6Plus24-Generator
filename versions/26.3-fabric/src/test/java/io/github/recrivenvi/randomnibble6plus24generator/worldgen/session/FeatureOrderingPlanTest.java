package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FeatureOrderingPlanTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void versionOneIsTheDynamicRadiusOneAbsoluteZThenXContract() {
        FeatureOrderingPlan plan =
                FeatureOrderingPlan.targetLocalZxRowMajorV1(new ChunkPos(10, -4));

        assertEquals(FeatureOrderingPlan.TARGET_LOCAL_ZX_ROW_MAJOR_V1, plan.algorithmVersion());
        assertEquals(1, plan.blockStateWriteRadius());
        assertEquals(
                List.of(
                        new ChunkPos(9, -5),
                        new ChunkPos(10, -5),
                        new ChunkPos(11, -5),
                        new ChunkPos(9, -4),
                        new ChunkPos(10, -4),
                        new ChunkPos(11, -4),
                        new ChunkPos(9, -3),
                        new ChunkPos(10, -3),
                        new ChunkPos(11, -3)),
                plan.writers());
        assertEquals(9, plan.packedWriterSet().size());
    }
}
