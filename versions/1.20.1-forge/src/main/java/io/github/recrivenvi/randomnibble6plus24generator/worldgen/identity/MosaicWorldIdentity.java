package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Server-owned identity adapter; filesystem access is confined to load/replacement boundaries. */
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

    public static void initializeServerIdentity(MinecraftServer server) {
        if (!server.isSameThread() || server.overworld() == null)
            throw new MosaicIdentityValidationException(
                    "Identity validation outside load boundary");
        var owner = state(server);
        owner.invalidate();
        try {
            var storage = server.overworld().getDataStorage();
            ((MosaicProfileChangeSource) storage)
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
            if (!exists
                    && !profiles.isEmpty()
                    && !server.getWorldData().overworldData().isInitialized()) {
                requireAgreement(dimensions.size(), profiles, Optional.of(profiles.get(0)));
                validateDevelopmentMarker(server.getWorldPath(LevelResource.ROOT), true);
                Files.createDirectories(path.getParent());
                var data = new MosaicWorldProfileData(profiles.get(0));
                data.setDirty();
                storage.set(MosaicWorldProfileData.ID, data);
            }
            var data = storage.get(MosaicWorldProfileData::load, MosaicWorldProfileData.ID);
            if (exists && data == null)
                throw new MosaicIdentityValidationException("Unreadable saved Mosaic profile");
            var profile = Optional.ofNullable(data).map(MosaicWorldProfileData::profile);
            requireAgreement(dimensions.size(), profiles, profile);
            validateDevelopmentMarker(server.getWorldPath(LevelResource.ROOT), profile.isPresent());
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

    private static void requireAgreement(
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
                    "This Target's distinct B identity requires Ordering 2");
    }

    private static void validateDevelopmentMarker(Path root, boolean mosaic) throws IOException {
        var marker = root.resolve("mosaic-development-b.json");
        boolean present = Files.exists(marker, LinkOption.NOFOLLOW_LINKS);
        if (!mosaic && !present) return;
        if (!mosaic
                || !present
                || !Boolean.getBoolean(ModConstants.MOD_ID + ".developmentB")
                || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) > 1024)
            throw new MosaicIdentityValidationException(
                    "B requires matching generators, profile, marker and explicit opt-in");
        try (var reader = new JsonReader(new StringReader(Files.readString(marker)))) {
            reader.setLenient(false);
            reader.beginObject();
            var seen = new HashSet<String>();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!seen.add(key)) throw new IllegalArgumentException("Duplicate B marker field");
                if (key.equals("schema")) {
                    if (reader.peek() != JsonToken.NUMBER || !reader.nextString().equals("1"))
                        throw new IllegalArgumentException("Unsupported B marker schema");
                } else if (key.equals("pipeline")) {
                    if (reader.peek() != JsonToken.STRING
                            || !reader.nextString()
                                    .equals(ModConstants.MINECRAFT_VERSION + "-lightweight-b-1"))
                        throw new IllegalArgumentException("Foreign B pipeline identity");
                } else throw new IllegalArgumentException("Unknown B marker field");
            }
            reader.endObject();
            if (!seen.equals(Set.of("schema", "pipeline"))
                    || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Incomplete B marker");
        }
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
