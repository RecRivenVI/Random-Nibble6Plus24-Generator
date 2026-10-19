package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;

/**
 * Native, order-independent authoritative Seed V1 bridge for this target.
 *
 * <p>Every mutable {@link RandomSource} used by a derivation is created inside that call. No RNG
 * stream is shared or cached between queries.
 */
public final class MosaicSeedResolver {

    public static final ResourceLocation AUTHORITATIVE_FINAL_DOMAIN_V1 =
            new ResourceLocation(
                    io.github.recrivenvi.randomnibble6plus24generator.ModConstants.MOD_ID,
                    "seed/authoritative_final/v1");

    private final MosaicWorldProfile profile;

    public MosaicSeedResolver(MosaicWorldProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        profile.requireSupported();
    }

    /** Resolves the Vanilla world seed that authoritatively owns one physical Mosaic chunk. */
    public long resolveLocalWorldSeed(
            long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(chunkPos, "chunkPos");

        if (dimension.equals(profile.primaryDimension()) && ChunkPos.ZERO.equals(chunkPos)) {
            return masterSeed;
        }

        return switch (profile.seedDerivationAlgorithmVersion()) {
            case MosaicWorldProfile.SEED_DERIVATION_ALGORITHM_V1 ->
                    deriveFinalSeedV1(masterSeed, dimension, chunkPos);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported seed derivation algorithm version: "
                                    + profile.seedDerivationAlgorithmVersion());
        };
    }

    private static long deriveFinalSeedV1(
            long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
        PositionalRandomFactory dimensionFactory =
                createDimensionFactoryV1(masterSeed, AUTHORITATIVE_FINAL_DOMAIN_V1, dimension);
        return dimensionFactory.at(chunkPos.getWorldPosition()).nextLong();
    }

    private static PositionalRandomFactory createDimensionFactoryV1(
            long masterSeed, ResourceLocation domain, ResourceKey<Level> dimension) {
        PositionalRandomFactory rootFactory =
                WorldgenRandom.Algorithm.XOROSHIRO.newInstance(masterSeed).forkPositional();
        PositionalRandomFactory domainFactory = rootFactory.fromHashOf(domain).forkPositional();
        return domainFactory.fromHashOf(dimension.location()).forkPositional();
    }
}
