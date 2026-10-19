package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** 1.21.1 owns WorldOptions in WorldData and custom SavedData in the Overworld. */
public final class MosaicWorldIdentity {
    private MosaicWorldIdentity() {}

    public static Optional<MosaicRuntimeContext> runtimeContext(MinecraftServer server) {
        return state(server).requireValidated(server.getWorldData().worldGenOptions());
    }

    public static Optional<MosaicRuntimeContext> runtimeContext(ServerLevel level) {
        var server = level.getServer();
        var context = runtimeContext(server);
        if (server.getLevel(level.dimension()) != level
                || ((MosaicLevelLifecycle) level)
                        .randomnibble6plus24generator$identityLevelClosed())
            throw new MosaicIdentityValidationException("Query belongs to another world instance");
        var generator = level.getChunkSource().getGenerator();
        if (context.isPresent() != (generator instanceof MosaicChunkGenerator)
                || (generator instanceof MosaicChunkGenerator mosaic
                        && !context.orElseThrow().profile().equals(mosaic.profile()))) {
            state(server).invalidate();
            throw new MosaicIdentityValidationException("World/dimension Mosaic identity mismatch");
        }
        return context;
    }

    /** After the Overworld exists, before its load event or any spawn generation. */
    public static void initializeServerIdentity(MinecraftServer server) {
        if (!server.isSameThread() || server.overworld() == null)
            throw new MosaicIdentityValidationException(
                    "Identity validation outside load boundary");
        var owner = state(server);
        owner.invalidate();
        try {
            ((MosaicProfileChangeSource) server.overworld().getDataStorage())
                    .randomnibble6plus24generator$onIdentityReplacement(owner::invalidate);
            var dimensions = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            var profiles =
                    dimensions.stream()
                            .filter(stem -> stem.generator() instanceof MosaicChunkGenerator)
                            .map(stem -> ((MosaicChunkGenerator) stem.generator()).profile())
                            .toList();
            var path = MosaicWorldProfileData.path(server);
            boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
            if (exists && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                throw new MosaicIdentityValidationException("Invalid profile file");
            var storage = server.overworld().getDataStorage();
            if (!exists
                    && !profiles.isEmpty()
                    && !server.getWorldData().overworldData().isInitialized()) {
                requireAgreement(dimensions.size(), profiles, Optional.of(profiles.getFirst()));
                BDevelopmentWorld.validate(server.getWorldPath(LevelResource.ROOT), true);
                Files.createDirectories(path.getParent());
                var data = new MosaicWorldProfileData(profiles.getFirst());
                data.setDirty();
                storage.set(MosaicWorldProfileData.ID, data);
            }
            var data = storage.get(MosaicWorldProfileData.FACTORY, MosaicWorldProfileData.ID);
            if (exists && data == null)
                throw new MosaicIdentityValidationException("Unreadable saved Mosaic profile");
            var profile = Optional.ofNullable(data).map(MosaicWorldProfileData::profile);
            requireAgreement(dimensions.size(), profiles, profile);
            BDevelopmentWorld.validate(
                    server.getWorldPath(LevelResource.ROOT), profile.isPresent());
            var options = server.getWorldData().worldGenOptions();
            owner.publish(options, profile.map(value -> owner.newContext(options, value)));
            if (profile.isPresent())
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                        .MosaicStructureOverlayStore.validateAtLoad(server);
        } catch (IOException | RuntimeException failure) {
            owner.invalidate();
            throw new MosaicIdentityValidationException(
                    "World identity validation failed", failure);
        }
    }

    static void requireAgreement(
            int dimensions,
            List<MosaicWorldProfile> generators,
            Optional<MosaicWorldProfile> saved) {
        if (saved.isEmpty() && generators.isEmpty()) return;
        if (saved.isEmpty()
                || dimensions == 0
                || generators.size() != dimensions
                || generators.stream().anyMatch(value -> !value.equals(saved.orElseThrow())))
            throw new MosaicIdentityValidationException("Missing or mixed Mosaic world authority");
        saved.orElseThrow().requireSupported();
        if (saved.orElseThrow().featureOrderingAlgorithmVersion()
                != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)
            throw new MosaicIdentityValidationException(
                    "This distinct B identity requires Ordering 2");
    }

    public static void closeServerIdentity(MinecraftServer server) {
        if (server.overworld() != null
                && server.overworld().getDataStorage() instanceof MosaicProfileChangeSource source)
            source.randomnibble6plus24generator$onIdentityReplacement(null);
        state(server).close();
    }

    public static void reloadProfileFromDisk(MinecraftServer server) {
        if (!server.isSameThread() || server.overworld() == null)
            throw new MosaicIdentityValidationException("Off-thread profile reload");
        ((MosaicProfileChangeSource) server.overworld().getDataStorage())
                .randomnibble6plus24generator$discardProfileForExplicitReload();
        initializeServerIdentity(server);
    }

    private static MosaicWorldRuntimeState state(MinecraftServer server) {
        if (!(server instanceof MosaicRuntimeContextOwner owner))
            throw new MosaicIdentityValidationException("Missing server-owned runtime context");
        return owner.randomnibble6plus24generator$worldIdentity();
    }
}
