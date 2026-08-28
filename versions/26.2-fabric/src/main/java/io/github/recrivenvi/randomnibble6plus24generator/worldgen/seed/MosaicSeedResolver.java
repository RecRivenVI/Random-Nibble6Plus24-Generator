package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import java.util.Objects;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

/**
 * Pure, order-independent derivation of authoritative and presentation seeds.
 *
 * <p>Every mutable {@link RandomSource} used by a derivation is created inside
 * that call. No RNG stream is shared or cached between queries.
 */
public final class MosaicSeedResolver {

    public static final Identifier AUTHORITATIVE_FINAL_DOMAIN_V1 = Identifier.fromNamespaceAndPath(
            "randomnibble6plus24generator",
            "seed/authoritative_final/v1");

    public static final Identifier PRESENTATION_PREVIEW_DOMAIN_V1 = Identifier.fromNamespaceAndPath(
            "randomnibble6plus24generator",
            "seed/presentation_preview/v1");

    private final MosaicWorldProfile profile;

    public MosaicSeedResolver(MosaicWorldProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        profile.requireSupported();
    }

    /**
     * Resolves the Vanilla world seed that authoritatively owns one physical
     * Mosaic chunk.
     */
    public long resolveLocalWorldSeed(long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(chunkPos, "chunkPos");

        if (dimension.equals(profile.primaryDimension()) && ChunkPos.ZERO.equals(chunkPos)) {
            return masterSeed;
        }

        return switch (profile.seedDerivationAlgorithmVersion()) {
            case MosaicWorldProfile.SEED_DERIVATION_ALGORITHM_V1 -> deriveFinalSeedV1(
                    masterSeed,
                    dimension,
                    chunkPos);
            default -> throw new IllegalArgumentException(
                    "Unsupported seed derivation algorithm version: "
                            + profile.seedDerivationAlgorithmVersion());
        };
    }

    /**
     * Resolves one non-authoritative preview seed. Preview queries cannot
     * consume or otherwise affect the authoritative derivation domain.
     */
    public long resolvePreviewSeed(
            long masterSeed,
            ResourceKey<Level> dimension,
            ChunkPos chunkPos,
            long previewIndex) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(chunkPos, "chunkPos");

        return switch (profile.presentationAlgorithmVersion()) {
            case MosaicWorldProfile.PRESENTATION_ALGORITHM_V1 -> derivePreviewSeedV1(
                    masterSeed,
                    dimension,
                    chunkPos,
                    previewIndex);
            default -> throw new IllegalArgumentException(
                    "Unsupported presentation algorithm version: "
                            + profile.presentationAlgorithmVersion());
        };
    }

    private static long deriveFinalSeedV1(
            long masterSeed,
            ResourceKey<Level> dimension,
            ChunkPos chunkPos) {
        PositionalRandomFactory dimensionFactory = createDimensionFactoryV1(
                masterSeed,
                AUTHORITATIVE_FINAL_DOMAIN_V1,
                dimension);
        return dimensionFactory.at(chunkPos.getWorldPosition()).nextLong();
    }

    private static long derivePreviewSeedV1(
            long masterSeed,
            ResourceKey<Level> dimension,
            ChunkPos chunkPos,
            long previewIndex) {
        PositionalRandomFactory dimensionFactory = createDimensionFactoryV1(
                masterSeed,
                PRESENTATION_PREVIEW_DOMAIN_V1,
                dimension);
        RandomSource chunkRandom = dimensionFactory.at(chunkPos.getWorldPosition());
        PositionalRandomFactory previewIndexFactory = chunkRandom.forkPositional();
        return previewIndexFactory.fromSeed(previewIndex).nextLong();
    }

    private static PositionalRandomFactory createDimensionFactoryV1(
            long masterSeed,
            Identifier domain,
            ResourceKey<Level> dimension) {
        PositionalRandomFactory rootFactory = WorldgenRandom.Algorithm.XOROSHIRO
                .newInstance(masterSeed)
                .forkPositional();
        PositionalRandomFactory domainFactory = rootFactory
                .fromHashOf(domain)
                .forkPositional();
        return domainFactory
                .fromHashOf(dimension.identifier())
                .forkPositional();
    }
}
