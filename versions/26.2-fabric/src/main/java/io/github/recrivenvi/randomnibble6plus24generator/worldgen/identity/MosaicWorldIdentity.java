package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import java.util.ArrayList;
import java.util.List;
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
        return runtimeContext(level).isPresent();
    }

    public static boolean isMosaicWorld(MinecraftServer server) {
        return runtimeContext(server).isPresent();
    }

    public static boolean isMosaicDimension(ServerLevel level) {
        return runtimeContext(level).isPresent();
    }

    public static Optional<MosaicRuntimeContext> runtimeContext(ServerLevel level) {
        MosaicWorldRuntimeState state = state(level.getServer());
        Optional<MosaicRuntimeContext> context = state.requireValidated(level.getServer().getWorldGenSettings());
        state.requireOpenDimension(level);
        boolean mosaicDimension = level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator;
        if (context.isPresent() != mosaicDimension
                || (mosaicDimension && !context.orElseThrow().profile().equals(
                        ((MosaicChunkGenerator) level.getChunkSource().getGenerator()).profile()))) {
            state.invalidate();
            throw new MosaicIdentityValidationException(
                    "World/dimension Mosaic identity mismatch for " + level.dimension().identifier());
        }
        return context;
    }

    public static Optional<MosaicRuntimeContext> runtimeContext(MinecraftServer server) {
        return state(server).requireValidated(server.getWorldGenSettings());
    }

    /** Load boundary, after new-save/test profile bootstrap and before any spawn generation. */
    public static void initializeServerIdentity(MinecraftServer server) {
        ((MosaicProfileChangeSource) server.getDataStorage())
                .randomnibble6plus24generator$onIdentityReplacement(state(server)::invalidate);
        revalidateServerIdentity(server);
    }

    /**
     * Synchronous explicit boundary after SavedData profile/settings replacement.
     * Ordinary datapack /reload does not replace 26.2 WorldGenSettings or its profile.
     */
    public static void revalidateServerIdentity(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Mosaic identity validation must run on the owning server thread");
        }
        WorldGenSettings worldGenSettings = server.getWorldGenSettings();
        state(server).validate(worldGenSettings, () -> {
            if (server.getDataStorage().get(WorldGenSettings.TYPE) != worldGenSettings) {
                throw new MosaicIdentityValidationException(
                        "SavedData WorldGenSettings differs from the server authority; refusing Vanilla fallback");
            }
            return readProfileEvidence(server);
        });
    }

    /** Explicitly rereads the profile from disk; never called by Chunk/status queries. */
    public static void reloadProfileFromDisk(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Mosaic profile reload must run on the owning server thread");
        }
        ((MosaicProfileChangeSource) server.getDataStorage())
                .randomnibble6plus24generator$discardProfileForExplicitReload();
        revalidateServerIdentity(server);
    }

    private static MosaicWorldRuntimeState.ProfileEvidence readProfileEvidence(MinecraftServer server) {
        boolean profileFilePresent = MosaicWorldProfileData.fileExists(server);
        Optional<MosaicWorldProfile> persistedProfile;
        try {
            persistedProfile = MosaicWorldProfileData.loadIfPresent(server).map(MosaicWorldProfileData::profile);
        } catch (RuntimeException exception) {
            throw new MosaicIdentityValidationException(
                    "World contains unreadable Mosaic profile metadata; refusing Vanilla fallback",
                    exception);
        }

        return new MosaicWorldRuntimeState.ProfileEvidence(persistedProfile, profileFilePresent);
    }

    /** Called in finally, after the normal save/close path has finished or failed. */
    public static void closeServerIdentity(MinecraftServer server) {
        state(server).close();
        ((MosaicProfileChangeSource) server.getDataStorage())
                .randomnibble6plus24generator$onIdentityReplacement(null);
    }

    private static MosaicWorldRuntimeState state(MinecraftServer server) {
        if (!(server instanceof MosaicRuntimeContextOwner owner)) {
            throw new MosaicIdentityValidationException("Missing server-owned Mosaic identity lifecycle");
        }
        return owner.randomnibble6plus24generator$worldIdentity();
    }

    /**
     * Persists the profile for a brand-new, fully serialized Mosaic world.  An
     * existing save with a missing profile is intentionally not repaired here;
     * the normal validator must fail closed instead of guessing its format.
     */
    public static void bootstrapNewWorldProfileIfNeeded(MinecraftServer server) {
        if (MosaicWorldProfileData.fileExists(server)
                || server.getWorldData().overworldData().isInitialized()) return;

        List<MosaicChunkGenerator> generators = new ArrayList<>();
        for (var entry : server.getWorldGenSettings().dimensions().dimensions().entrySet()) {
            if (entry.getValue().generator() instanceof MosaicChunkGenerator generator) {
                generators.add(generator);
            }
        }
        if (generators.isEmpty() || generators.size() != server.getWorldGenSettings()
                .dimensions().dimensions().size()) return;

        MosaicWorldProfile profile = generators.getFirst().profile();
        if (generators.stream().anyMatch(generator -> !profile.equals(generator.profile()))) {
            throw new MosaicIdentityValidationException(
                    "New Mosaic world contains inconsistent serialized generator profiles");
        }
        profile.requireSupported();
        server.getDataStorage().set(
                MosaicWorldProfileData.TYPE,
                new MosaicWorldProfileData(profile));
        RandomNibble6Plus24Generator.LOGGER.info(
                "Initialized Mosaic world profile for new save: format={}, seedAlgorithm={}, presentationAlgorithm={}, primaryDimension={}",
                profile.formatVersion(),
                profile.seedDerivationAlgorithmVersion(),
                profile.presentationAlgorithmVersion(),
                profile.primaryDimension().identifier());
    }

    public static void validateServerAfterLevelCreation(MinecraftServer server) {
        try {
            Optional<MosaicRuntimeContext> context = runtimeContext(server);
            for (ServerLevel level : server.getAllLevels()) runtimeContext(level);
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
