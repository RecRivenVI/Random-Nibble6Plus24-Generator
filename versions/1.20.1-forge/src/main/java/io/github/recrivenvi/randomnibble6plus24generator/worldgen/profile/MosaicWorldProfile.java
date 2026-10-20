package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Seed-core format information that is fixed for one Mosaic world.
 *
 * <p>The authoritative master seed is deliberately not stored here. Runtime integration must supply
 * {@code WorldOptions.seed()} to the seed resolver.
 */
public record MosaicWorldProfile(
        int formatVersion,
        int seedDerivationAlgorithmVersion,
        int featureOrderingAlgorithmVersion,
        int presentationAlgorithmVersion,
        ResourceKey<Level> primaryDimension) {

    public static final int FORMAT_VERSION_V2 = MosaicContractVersions.FORMAT_V2;
    public static final int SEED_DERIVATION_ALGORITHM_V1 = MosaicContractVersions.SEED_V1;
    public static final int FEATURE_ORDERING_ALGORITHM_V1 = MosaicContractVersions.ORDERING_V1;
    public static final int FEATURE_ORDERING_TARGET_ONLY =
            MosaicContractVersions.ORDERING_TARGET_ONLY;
    public static final int PRESENTATION_ALGORITHM_V1 = MosaicContractVersions.PRESENTATION_V1;

    private static final Codec<MosaicWorldProfile> RAW_CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.INT
                                                    .fieldOf("format_version")
                                                    .forGetter(MosaicWorldProfile::formatVersion),
                                            Codec.INT
                                                    .fieldOf("seed_derivation_algorithm_version")
                                                    .forGetter(
                                                            MosaicWorldProfile
                                                                    ::seedDerivationAlgorithmVersion),
                                            Codec.INT
                                                    .fieldOf("feature_ordering_algorithm_version")
                                                    .forGetter(
                                                            MosaicWorldProfile
                                                                    ::featureOrderingAlgorithmVersion),
                                            Codec.INT
                                                    .fieldOf("presentation_algorithm_version")
                                                    .forGetter(
                                                            MosaicWorldProfile
                                                                    ::presentationAlgorithmVersion),
                                            ResourceKey.codec(Registries.DIMENSION)
                                                    .fieldOf("primary_dimension")
                                                    .forGetter(
                                                            MosaicWorldProfile::primaryDimension))
                                    .apply(instance, MosaicWorldProfile::new));

    public static final Codec<MosaicWorldProfile> CODEC =
            RAW_CODEC.flatXmap(
                    MosaicWorldProfile::validateSupported, MosaicWorldProfile::validateSupported);

    public MosaicWorldProfile {
        MosaicContractVersions.requirePositive(
                formatVersion,
                seedDerivationAlgorithmVersion,
                featureOrderingAlgorithmVersion,
                presentationAlgorithmVersion);
        Objects.requireNonNull(primaryDimension, "primaryDimension");
    }

    public static MosaicWorldProfile current() {
        return new MosaicWorldProfile(
                FORMAT_VERSION_V2,
                SEED_DERIVATION_ALGORITHM_V1,
                FEATURE_ORDERING_ALGORITHM_V1,
                PRESENTATION_ALGORITHM_V1,
                Level.OVERWORLD);
    }

    /** Explicit opt-in for new worlds; existing serialized V1 worlds never change behavior. */
    public static MosaicWorldProfile targetOnly() {
        return new MosaicWorldProfile(
                FORMAT_VERSION_V2,
                SEED_DERIVATION_ALGORITHM_V1,
                FEATURE_ORDERING_TARGET_ONLY,
                PRESENTATION_ALGORITHM_V1,
                Level.OVERWORLD);
    }

    public void requireSupported() {
        DataResult<MosaicWorldProfile> result = validateSupported(this);
        result.getOrThrow(
                false,
                message -> {
                    throw new IllegalArgumentException(message);
                });
    }

    private static DataResult<MosaicWorldProfile> validateSupported(MosaicWorldProfile profile) {
        return MosaicContractVersions.unsupportedVersion(
                        profile.formatVersion(),
                        profile.seedDerivationAlgorithmVersion(),
                        profile.featureOrderingAlgorithmVersion(),
                        profile.presentationAlgorithmVersion(),
                        true)
                .<DataResult<MosaicWorldProfile>>map(message -> DataResult.error(() -> message))
                .orElseGet(() -> DataResult.success(profile));
    }
}
