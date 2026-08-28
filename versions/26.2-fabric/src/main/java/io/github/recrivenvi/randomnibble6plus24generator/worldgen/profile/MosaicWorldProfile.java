package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Seed-core format information that is fixed for one Mosaic world.
 *
 * <p>The authoritative master seed is deliberately not stored here. Runtime
 * integration must supply {@code WorldOptions.seed()} to the seed resolver.
 */
public record MosaicWorldProfile(
        int formatVersion,
        int seedDerivationAlgorithmVersion,
        int presentationAlgorithmVersion,
        ResourceKey<Level> primaryDimension) {

    public static final int FORMAT_VERSION_V1 = 1;
    public static final int SEED_DERIVATION_ALGORITHM_V1 = 1;
    public static final int PRESENTATION_ALGORITHM_V1 = 1;

    private static final Codec<MosaicWorldProfile> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("format_version").forGetter(MosaicWorldProfile::formatVersion),
            Codec.INT.fieldOf("seed_derivation_algorithm_version")
                    .forGetter(MosaicWorldProfile::seedDerivationAlgorithmVersion),
            Codec.INT.fieldOf("presentation_algorithm_version")
                    .forGetter(MosaicWorldProfile::presentationAlgorithmVersion),
            ResourceKey.codec(Registries.DIMENSION)
                    .fieldOf("primary_dimension")
                    .forGetter(MosaicWorldProfile::primaryDimension))
            .apply(instance, MosaicWorldProfile::new));

    public static final Codec<MosaicWorldProfile> CODEC = RAW_CODEC.flatXmap(
            MosaicWorldProfile::validateSupported,
            MosaicWorldProfile::validateSupported);

    public MosaicWorldProfile {
        if (formatVersion < 1) {
            throw new IllegalArgumentException("formatVersion must be positive");
        }
        if (seedDerivationAlgorithmVersion < 1) {
            throw new IllegalArgumentException("seedDerivationAlgorithmVersion must be positive");
        }
        if (presentationAlgorithmVersion < 1) {
            throw new IllegalArgumentException("presentationAlgorithmVersion must be positive");
        }
        Objects.requireNonNull(primaryDimension, "primaryDimension");
    }

    public static MosaicWorldProfile version1() {
        return new MosaicWorldProfile(
                FORMAT_VERSION_V1,
                SEED_DERIVATION_ALGORITHM_V1,
                PRESENTATION_ALGORITHM_V1,
                Level.OVERWORLD);
    }

    public void requireSupported() {
        DataResult<MosaicWorldProfile> result = validateSupported(this);
        result.getOrThrow(IllegalArgumentException::new);
    }

    private static DataResult<MosaicWorldProfile> validateSupported(MosaicWorldProfile profile) {
        if (profile.formatVersion() != FORMAT_VERSION_V1) {
            return DataResult.error(() -> "Unsupported Mosaic format version: " + profile.formatVersion());
        }
        if (profile.seedDerivationAlgorithmVersion() != SEED_DERIVATION_ALGORITHM_V1) {
            return DataResult.error(() -> "Unsupported Mosaic seed derivation algorithm version: "
                    + profile.seedDerivationAlgorithmVersion());
        }
        if (profile.presentationAlgorithmVersion() != PRESENTATION_ALGORITHM_V1) {
            return DataResult.error(() -> "Unsupported Mosaic presentation algorithm version: "
                    + profile.presentationAlgorithmVersion());
        }
        return DataResult.success(profile);
    }
}
