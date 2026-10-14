package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FeatureOrderingPlanTest {

    @Test
    void targetOnlyPlanHasExactlyOneWriter() {
        ChunkPos target = new ChunkPos(-125, 37);
        FeatureOrderingPlan plan =
                FeatureOrderingPlan.forProfile(
                        target,
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile
                                .MosaicWorldProfile.targetOnly());
        assertEquals(2, plan.algorithmVersion());
        assertEquals(0, plan.blockStateWriteRadius());
        assertEquals(List.of(target), plan.writers());
        assertEquals(1, plan.packedWriterSet().size());
    }

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
