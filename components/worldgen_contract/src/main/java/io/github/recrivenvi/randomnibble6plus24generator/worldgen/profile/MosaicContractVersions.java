package io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile;

import java.util.Optional;

/** Persisted version rules shared by the version-specific Minecraft profile codecs. */
public final class MosaicContractVersions {
    public static final int FORMAT_V2 = 2;
    public static final int SEED_V1 = 1;
    public static final int ORDERING_V1 = 1;
    public static final int ORDERING_TARGET_ONLY = 2;
    public static final int PRESENTATION_V1 = 1;

    private MosaicContractVersions() {}

    public static void requirePositive(int format, int seed, int ordering, int presentation) {
        if (format < 1) throw new IllegalArgumentException("formatVersion must be positive");
        if (seed < 1)
            throw new IllegalArgumentException("seedDerivationAlgorithmVersion must be positive");
        if (ordering < 1)
            throw new IllegalArgumentException("featureOrderingAlgorithmVersion must be positive");
        if (presentation < 1)
            throw new IllegalArgumentException("presentationAlgorithmVersion must be positive");
    }

    public static Optional<String> unsupportedVersion(
            int format, int seed, int ordering, int presentation, boolean targetOnlySupported) {
        if (format != FORMAT_V2) return Optional.of("Unsupported Mosaic format version: " + format);
        if (seed != SEED_V1)
            return Optional.of("Unsupported Mosaic seed derivation algorithm version: " + seed);
        if (ordering != ORDERING_V1 && (!targetOnlySupported || ordering != ORDERING_TARGET_ONLY))
            return Optional.of(
                    "Unsupported Mosaic feature ordering algorithm version: " + ordering);
        if (presentation != PRESENTATION_V1)
            return Optional.of(
                    "Unsupported Mosaic presentation algorithm version: " + presentation);
        return Optional.empty();
    }
}
