package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicSeedResolverSpecificationTest {

    private record Query(long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void authoritativeResolverMatchesTheFrozenNativePrimitiveCallChain() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());

        for (Query query : queries()) {
            long expected = deriveFinalSeedV1BySpecification(
                    query.masterSeed(),
                    query.dimension(),
                    query.chunkPos());
            assertEquals(
                    expected,
                    resolver.resolveLocalWorldSeed(query.masterSeed(), query.dimension(), query.chunkPos()));
        }
    }

    @Test
    void presentationResolverMatchesTheFrozenNativePrimitiveCallChain() {
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());

        for (Query query : queries()) {
            for (long previewIndex : new long[] {-1L, 0L, 1L, 17L, 10_000L, Long.MAX_VALUE}) {
                long expected = derivePreviewSeedV1BySpecification(
                        query.masterSeed(),
                        query.dimension(),
                        query.chunkPos(),
                        previewIndex);
                assertEquals(
                        expected,
                        resolver.resolvePreviewSeed(
                                query.masterSeed(),
                                query.dimension(),
                                query.chunkPos(),
                                previewIndex));
            }
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
            long masterSeed,
            ResourceKey<Level> dimension,
            ChunkPos chunkPos) {
        return dimensionFactory(
                masterSeed,
                MosaicSeedResolver.AUTHORITATIVE_FINAL_DOMAIN_V1,
                dimension)
                .at(chunkPos.getWorldPosition())
                .nextLong();
    }

    private static long derivePreviewSeedV1BySpecification(
            long masterSeed,
            ResourceKey<Level> dimension,
            ChunkPos chunkPos,
            long previewIndex) {
        RandomSource chunkRandom = dimensionFactory(
                masterSeed,
                MosaicSeedResolver.PRESENTATION_PREVIEW_DOMAIN_V1,
                dimension)
                .at(chunkPos.getWorldPosition());
        return chunkRandom.forkPositional().fromSeed(previewIndex).nextLong();
    }

    private static PositionalRandomFactory dimensionFactory(
            long masterSeed,
            Identifier domain,
            ResourceKey<Level> dimension) {
        PositionalRandomFactory rootFactory = WorldgenRandom.Algorithm.XOROSHIRO
                .newInstance(masterSeed)
                .forkPositional();
        PositionalRandomFactory domainFactory = rootFactory.fromHashOf(domain).forkPositional();
        return domainFactory.fromHashOf(dimension.identifier()).forkPositional();
    }

    private static List<Query> queries() {
        ResourceKey<Level> customDimension = ResourceKey.create(
                Level.OVERWORLD.registryKey(),
                Identifier.fromNamespaceAndPath("example", "custom_dimension"));
        return List.of(
                new Query(0L, Level.OVERWORLD, new ChunkPos(1, 0)),
                new Query(123456789L, Level.OVERWORLD, new ChunkPos(125, -37)),
                new Query(-987654321L, Level.NETHER, new ChunkPos(-125, 37)),
                new Query(Long.MIN_VALUE, Level.END, new ChunkPos(Integer.MAX_VALUE, Integer.MIN_VALUE)),
                new Query(Long.MAX_VALUE, customDimension, new ChunkPos(-1_000_000, 2_000_000)));
    }
}
