package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import java.util.Optional;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldGenSettings;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;

public final class MosaicWorldIdentity {

    private MosaicWorldIdentity() {
    }

    public static boolean isMosaic(ServerLevel level) {
        return isMosaicWorld(level);
    }

    public static boolean isMosaicWorld(ServerLevel level) {
        return isMosaicWorld(level.getServer());
    }

    public static boolean isMosaicWorld(MinecraftServer server) {
        return runtimeContext(server).isPresent();
    }

    public static boolean isMosaicDimension(ServerLevel level) {
        boolean mosaicWorld = isMosaicWorld(level);
        boolean mosaicDimension = level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator;
        if (mosaicWorld != mosaicDimension) {
            throw new MosaicIdentityValidationException(
                    "World/dimension Mosaic identity mismatch for " + level.dimension().identifier());
        }
        return mosaicDimension;
    }

    public static Optional<MosaicRuntimeContext> runtimeContext(ServerLevel level) {
        return runtimeContext(level.getServer());
    }

    public static Optional<MosaicRuntimeContext> runtimeContext(MinecraftServer server) {
        WorldGenSettings worldGenSettings = server.getWorldGenSettings();
        boolean profileFilePresent = MosaicWorldProfileData.fileExists(server);
        Optional<MosaicWorldProfile> persistedProfile;
        try {
            persistedProfile = MosaicWorldProfileData.loadIfPresent(server).map(MosaicWorldProfileData::profile);
        } catch (RuntimeException exception) {
            throw new MosaicIdentityValidationException(
                    "World contains unreadable Mosaic profile metadata; refusing Vanilla fallback",
                    exception);
        }

        Optional<MosaicWorldProfile> validatedProfile = MosaicIdentityValidator.validate(
                worldGenSettings.dimensions(),
                persistedProfile,
                profileFilePresent);
        return validatedProfile.map(profile -> new MosaicRuntimeContext(worldGenSettings, profile));
    }

    public static void validateServerAfterLevelCreation(MinecraftServer server) {
        try {
            Optional<MosaicRuntimeContext> context = runtimeContext(server);
            context.ifPresent(value -> RandomNibble6Plus24Generator.LOGGER.info(
                    "Validated Mosaic world identity: format={}, seedAlgorithm={}, presentationAlgorithm={}, primaryDimension={}",
                    value.profile().formatVersion(),
                    value.profile().seedDerivationAlgorithmVersion(),
                    value.profile().presentationAlgorithmVersion(),
                    value.profile().primaryDimension().identifier()));
        } catch (MosaicIdentityValidationException exception) {
            RandomNibble6Plus24Generator.LOGGER.error(
                    "Mosaic world identity validation failed; refusing to prepare levels or generate chunks: {}",
                    exception.getMessage(),
                    exception);
            throw exception;
        }
    }
}
