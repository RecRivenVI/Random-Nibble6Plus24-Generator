package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class MosaicSeedResolverGoldenTest {

    private record FinalVector(
            long masterSeed, String dimension, int chunkX, int chunkZ, long expected) {}

    @Test
    void authoritativeV1GoldenVectorsRemainStable() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        List<FinalVector> vectors =
                List.of(
                        new FinalVector(0L, "minecraft:overworld", 0, 0, 0L),
                        new FinalVector(1L, "minecraft:overworld", 0, 0, 1L),
                        new FinalVector(-1L, "minecraft:overworld", 0, 0, -1L),
                        new FinalVector(
                                Long.MIN_VALUE, "minecraft:overworld", 0, 0, Long.MIN_VALUE),
                        new FinalVector(
                                Long.MAX_VALUE, "minecraft:overworld", 0, 0, Long.MAX_VALUE),
                        new FinalVector(123456789L, "minecraft:overworld", 0, 0, 123456789L),
                        new FinalVector(-987654321L, "minecraft:overworld", 0, 0, -987654321L),
                        new FinalVector(0L, "minecraft:overworld", 1, 0, 4728692025433535151L),
                        new FinalVector(1L, "minecraft:overworld", 0, 1, -3821673505261983852L),
                        new FinalVector(-1L, "minecraft:the_nether", -1, 1, -4702794264821873409L),
                        new FinalVector(
                                123456789L, "minecraft:overworld", 125, -37, -5161763991829980711L),
                        new FinalVector(
                                -987654321L, "minecraft:the_end", -125, 37, -7286762380808216340L),
                        new FinalVector(
                                Long.MIN_VALUE,
                                "minecraft:overworld",
                                Integer.MAX_VALUE,
                                Integer.MIN_VALUE,
                                8117146660313452976L),
                        new FinalVector(
                                Long.MAX_VALUE,
                                "minecraft:the_nether",
                                Integer.MIN_VALUE,
                                Integer.MAX_VALUE,
                                -5567729542276810067L),
                        new FinalVector(
                                0L,
                                "example:custom_dimension",
                                12345,
                                67890,
                                -2482565477941862058L),
                        new FinalVector(
                                0x123456789ABCDEFL,
                                "example:custom_dimension",
                                -1_000_000,
                                -2_000_000,
                                -7288201210782287693L),
                        new FinalVector(
                                -0x123456789ABCDEFL,
                                "minecraft:the_end",
                                30_000_000,
                                -30_000_000,
                                4862480437238865705L),
                        new FinalVector(
                                42L,
                                "minecraft:overworld",
                                Integer.MIN_VALUE + 1,
                                Integer.MAX_VALUE - 1,
                                8647248815658803076L));

        for (FinalVector vector : vectors) {
            long actual =
                    resolver.resolveLocalWorldSeed(
                            vector.masterSeed(),
                            dimension(vector.dimension()),
                            new ChunkPos(vector.chunkX(), vector.chunkZ()));
            assertEquals(vector.expected(), actual, vector.toString());
        }
    }

    private static ResourceKey<Level> dimension(String identifier) {
        return ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.parse(identifier));
    }
}
