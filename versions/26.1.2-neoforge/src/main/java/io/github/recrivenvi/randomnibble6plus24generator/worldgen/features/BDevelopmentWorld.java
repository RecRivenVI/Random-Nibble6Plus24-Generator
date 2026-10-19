package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.google.gson.Strictness;
import com.google.gson.stream.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicIdentityValidationException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Explicit new-save identity, validated at load/replacement, never by chunk hot-path disk polling.
 */
public final class BDevelopmentWorld {
    public static final String MARKER = "mosaic-development-b.json";
    public static final String PIPELINE = ModConstants.MINECRAFT_VERSION + "-lightweight-b-1";
    public static final String PROPERTY = ModConstants.MOD_ID + ".developmentB";
    private static final Map<MinecraftServer, State> WORLDS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private BDevelopmentWorld() {}

    public static void validate(MinecraftServer server, Optional<MosaicRuntimeContext> runtime) {
        WORLDS.remove(server);
        var dimensions = server.getWorldGenSettings().dimensions().dimensions();
        long marked =
                dimensions.values().stream()
                        .filter(
                                stem ->
                                        stem.generator() instanceof MosaicChunkGenerator generator
                                                && generator.developmentB())
                        .count();
        var path = server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
        boolean present = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        boolean targetOnly =
                runtime.isPresent()
                        && runtime.orElseThrow().profile().featureOrderingAlgorithmVersion()
                                == MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY;
        if (!requireIdentity(
                marked, dimensions.size(), present, Boolean.getBoolean(PROPERTY), targetOnly))
            return;
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1024)
                throw new IllegalArgumentException("Invalid B save marker");
            requireMarker(Files.readString(path));
        } catch (IOException | RuntimeException failure) {
            throw new MosaicIdentityValidationException(
                    "Unreadable/unsupported B save identity", failure);
        }
        WORLDS.put(server, new State(runtime.orElseThrow()));
    }

    static boolean requireIdentity(
            long marked, int dimensions, boolean marker, boolean optIn, boolean targetOnly) {
        if (marked == 0 && !marker && !targetOnly) return false;
        if (dimensions == 0 || marked != dimensions || !marker || !optIn || !targetOnly)
            throw new MosaicIdentityValidationException(
                    "B requires all development_b generators, Ordering 2, marker and explicit opt-in; refusing legacy fallback");
        return true;
    }

    static void requireMarker(String text) {
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginObject();
            var seen = new HashSet<String>();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!seen.add(name))
                    throw new IllegalArgumentException("Duplicate B identity field");
                if (name.equals("schema")) {
                    if (reader.peek() != JsonToken.NUMBER || !reader.nextString().equals("1"))
                        throw new IllegalArgumentException("Unsupported B identity schema");
                } else if (name.equals("pipeline")) {
                    if (reader.peek() != JsonToken.STRING || !reader.nextString().equals(PIPELINE))
                        throw new IllegalArgumentException("Foreign B pipeline identity");
                } else throw new IllegalArgumentException("Unknown B identity field");
            }
            reader.endObject();
            if (!seen.equals(Set.of("schema", "pipeline"))
                    || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Incomplete B identity");
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid B identity JSON", failure);
        }
    }

    public static boolean active(ServerLevel level) {
        var state = WORLDS.get(level.getServer());
        if (state == null) return false;
        if (MosaicWorldIdentity.runtimeContext(level).orElse(null) != state.runtime)
            throw new MosaicIdentityValidationException(
                    "B identity changed without lifecycle validation");
        return true;
    }

    public static void record(ServerLevel level, BFeaturePreparation.Result result) {
        if (!active(level))
            throw new IllegalStateException("B handoff outside its validated world");
        var state = WORLDS.get(level.getServer());
        state.generations.increment();
        state.preparation.add(result.preparationNanos());
        state.decoration.add(result.decorationNanos());
    }

    public static Map<String, Long> metrics(MinecraftServer server) {
        var state = WORLDS.get(server);
        return state == null
                ? Map.of("generations", 0L)
                : Map.of(
                        "generations",
                        state.generations.sum(),
                        "preparationNanos",
                        state.preparation.sum(),
                        "decorationNanos",
                        state.decoration.sum());
    }

    public static void close(MinecraftServer server) {
        WORLDS.remove(server);
    }

    private static final class State {
        final MosaicRuntimeContext runtime;
        final LongAdder generations = new LongAdder(),
                preparation = new LongAdder(),
                decoration = new LongAdder();

        State(MosaicRuntimeContext runtime) {
            this.runtime = runtime;
        }
    }
}
