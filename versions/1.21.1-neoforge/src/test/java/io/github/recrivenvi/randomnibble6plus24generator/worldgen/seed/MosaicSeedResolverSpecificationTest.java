package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.junit.jupiter.api.Test;

class MosaicSeedResolverSpecificationTest {

    private record Query(long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {}

    @Test
    void authoritativeResolverMatchesTheFrozenNativePrimitiveCallChain() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());

        for (Query query : queries()) {
            long expected =
                    deriveFinalSeedV1BySpecification(
                            query.masterSeed(), query.dimension(), query.chunkPos());
            assertEquals(
                    expected,
                    resolver.resolveLocalWorldSeed(
                            query.masterSeed(), query.dimension(), query.chunkPos()));
        }
    }

    @Test
    void chunkPositionSamplingUsesWorldBlockOriginAndFixedZeroY() {
        ChunkPos chunkPos = new ChunkPos(125, -37);
        BlockPos sampledPosition = chunkPos.getWorldPosition();

        assertEquals(125 * 16, sampledPosition.getX());
        assertEquals(0, sampledPosition.getY());
        assertEquals(-37 * 16, sampledPosition.getZ());
    }

    private static long deriveFinalSeedV1BySpecification(
            long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        return dimensionFactory(
                        masterSeed, MosaicSeedResolver.AUTHORITATIVE_FINAL_DOMAIN_V1, dimension)
                .at(chunkPos.getWorldPosition())
                .nextLong();
    }

    private static PositionalRandomFactory dimensionFactory(
            long masterSeed, ResourceLocation domain, ResourceKey<Level> dimension) {
        PositionalRandomFactory rootFactory =
                WorldgenRandom.Algorithm.XOROSHIRO.newInstance(masterSeed).forkPositional();
        PositionalRandomFactory domainFactory = rootFactory.fromHashOf(domain).forkPositional();
        return domainFactory.fromHashOf(dimension.location()).forkPositional();
    }

    private static List<Query> queries() {
        ResourceKey<Level> customDimension =
                ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.fromNamespaceAndPath("example", "custom_dimension"));
        return List.of(
                new Query(0L, Level.OVERWORLD, new ChunkPos(1, 0)),
                new Query(123456789L, Level.OVERWORLD, new ChunkPos(125, -37)),
                new Query(-987654321L, Level.NETHER, new ChunkPos(-125, 37)),
                new Query(
                        Long.MIN_VALUE,
                        Level.END,
                        new ChunkPos(Integer.MAX_VALUE, Integer.MIN_VALUE)),
                new Query(Long.MAX_VALUE, customDimension, new ChunkPos(-1_000_000, 2_000_000)));
    }
}
