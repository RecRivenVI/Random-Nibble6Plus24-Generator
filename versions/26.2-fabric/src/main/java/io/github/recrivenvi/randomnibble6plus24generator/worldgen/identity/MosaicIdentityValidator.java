package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

public final class MosaicIdentityValidator {

    private MosaicIdentityValidator() {
    }

    public static Optional<MosaicWorldProfile> validate(
            WorldDimensions dimensions,
            Optional<MosaicWorldProfile> persistedProfile,
            boolean profileFilePresent) {
        List<Map.Entry<ResourceKey<LevelStem>, MosaicChunkGenerator>> mosaicGenerators = new ArrayList<>();
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : dimensions.dimensions().entrySet()) {
            if (entry.getValue().generator() instanceof MosaicChunkGenerator generator) {
                mosaicGenerators.add(Map.entry(entry.getKey(), generator));
            }
        }

        if (mosaicGenerators.isEmpty()) {
            if (persistedProfile.isPresent() || profileFilePresent) {
                throw failure("Mosaic profile metadata exists but no serialized Mosaic generator is present");
            }
            return Optional.empty();
        }

        if (mosaicGenerators.size() != dimensions.dimensions().size()) {
            List<String> nonMosaicDimensions = dimensions.dimensions().entrySet().stream()
                    .filter(entry -> !(entry.getValue().generator() instanceof MosaicChunkGenerator))
                    .map(entry -> entry.getKey().identifier().toString())
                    .toList();
            throw failure("ALL_DIMENSIONS_MOSAIC violation; non-Mosaic dimensions=" + nonMosaicDimensions);
        }

        MosaicWorldProfile generatorProfile = mosaicGenerators.getFirst().getValue().profile();
        generatorProfile.requireSupported();

        for (Map.Entry<ResourceKey<LevelStem>, MosaicChunkGenerator> entry : mosaicGenerators) {
            if (!generatorProfile.equals(entry.getValue().profile())) {
                throw failure("Serialized Mosaic generators disagree on MosaicWorldProfile; expected "
                        + generatorProfile
                        + ", found "
                        + entry.getValue().profile()
                        + " at dimension "
                        + entry.getKey().identifier());
            }
        }

        MosaicWorldProfile savedProfile = persistedProfile.orElseThrow(() -> failure(
                "Serialized Mosaic generator is present but MosaicWorldProfile metadata is missing"));
        savedProfile.requireSupported();

        if (!generatorProfile.equals(savedProfile)) {
            throw failure("Mosaic generator/profile mismatch; generator="
                    + generatorProfile
                    + ", persisted="
                    + savedProfile);
        }

        boolean primaryGeneratorPresent = mosaicGenerators.stream().anyMatch(entry -> entry.getKey()
                .identifier()
                .equals(savedProfile.primaryDimension().identifier()));
        if (!primaryGeneratorPresent) {
            throw failure("Mosaic profile primary dimension "
                    + savedProfile.primaryDimension().identifier()
                    + " does not contain a Mosaic generator");
        }

        return Optional.of(savedProfile);
    }

    private static MosaicIdentityValidationException failure(String detail) {
        return new MosaicIdentityValidationException(
                "Invalid Mosaic world identity; refusing Vanilla fallback. " + detail);
    }
}
