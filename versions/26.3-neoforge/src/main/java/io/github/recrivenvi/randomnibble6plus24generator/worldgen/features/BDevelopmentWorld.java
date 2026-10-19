package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.google.gson.*;
import com.google.gson.stream.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicIdentityValidationException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Explicit test-save opt-in, validated once per world load. Not a new Mosaic profile version. */
public final class BDevelopmentWorld {
    public static final String MARKER = "mosaic-development-b.json";
    public static final String PROPERTY = ModConstants.MOD_ID + ".developmentB";
    private static final Map<MinecraftServer, State> WORLDS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private BDevelopmentWorld() {}

    public static void validate(MinecraftServer server, Optional<MosaicRuntimeContext> runtime) {
        WORLDS.remove(server);
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(MARKER);
        var dimensions = server.getWorldGenSettings().dimensions().dimensions();
        long developmentDimensions =
                dimensions.values().stream()
                        .filter(
                                stem ->
                                        stem.generator() instanceof MosaicChunkGenerator generator
                                                && generator.developmentB())
                        .count();
        if (!requireIdentity(
                developmentDimensions,
                dimensions.size(),
                Files.exists(path, LinkOption.NOFOLLOW_LINKS),
                Boolean.getBoolean(PROPERTY),
                runtime.isPresent()
                        && runtime.orElseThrow().profile().featureOrderingAlgorithmVersion()
                                == MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)) return;
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 1024)
                throw new IllegalArgumentException("Invalid B development marker");
            requireMarker(Files.readString(path));
        } catch (IOException | RuntimeException failure) {
            throw new MosaicIdentityValidationException(
                    "Unreadable/unsupported B development save marker", failure);
        }
        WORLDS.put(server, new State(runtime.orElseThrow()));
        io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator.LOGGER.info(
                "Validated B development save: target-only FEATURES, exact on-demand same-seed reads, strict boundary writes");
    }

    static boolean requireIdentity(
            long developmentDimensions,
            int dimensions,
            boolean marker,
            boolean optIn,
            boolean validProfile) {
        if (developmentDimensions == 0 && !marker) return false;
        if (developmentDimensions != dimensions
                || dimensions == 0
                || !marker
                || !optIn
                || !validProfile)
            throw new MosaicIdentityValidationException(
                    "B save requires all serialized development_b dimensions, marker, explicit developmentB opt-in and validated Ordering 2 identity; refusing pipeline fallback");
        return true;
    }

    static void requireMarker(String text) {
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginObject();
            var names = new HashSet<String>();
            while (reader.hasNext()) {
                var name = reader.nextName();
                if (!names.add(name))
                    throw new IllegalArgumentException("Duplicate B marker field");
                if (name.equals("schema")) {
                    if (reader.peek() != JsonToken.NUMBER || !reader.nextString().equals("1"))
                        throw new IllegalArgumentException("Unsupported B marker schema");
                } else if (name.equals("pipeline")) {
                    if (reader.peek() != JsonToken.STRING
                            || !reader.nextString().equals("26.3-lightweight-b-1"))
                        throw new IllegalArgumentException("Unsupported B marker pipeline");
                } else throw new IllegalArgumentException("Unknown B marker field");
            }
            reader.endObject();
            if (!names.equals(Set.of("schema", "pipeline"))
                    || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Incomplete B marker");
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid B marker JSON", failure);
        }
    }

    public static boolean active(ServerLevel level) {
        var state = WORLDS.get(level.getServer());
        if (state == null) return false;
        if (MosaicWorldIdentity.runtimeContext(level).orElse(null) != state.runtime)
            throw new MosaicIdentityValidationException(
                    "B development world identity changed without revalidation");
        return true;
    }

    public static void record(ServerLevel level, BFeaturePreparation.Result result) {
        if (!active(level))
            throw new IllegalStateException("B handoff outside its validated development save");
        var state = WORLDS.get(level.getServer());
        state.generations.increment();
        state.preparation.add(result.preparationNanos());
        state.decoration.add(result.decorationNanos());
        state.fallbacks.add(result.reads().fullNeighbors());
        if (result.structureReuse() != null) {
            state.sourceQueries.add(result.structureReuse().queries());
            state.sourceHits.add(result.structureReuse().hits());
            state.sourceEvaluations.add(result.structureReuse().evaluations());
        }
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
                        state.decoration.sum(),
                        "fullNeighborFallbacks",
                        state.fallbacks.sum(),
                        "structureSourceQueries",
                        state.sourceQueries.sum(),
                        "structureSourceHits",
                        state.sourceHits.sum(),
                        "structureSourceEvaluations",
                        state.sourceEvaluations.sum());
    }

    public static void close(MinecraftServer server) {
        var state = WORLDS.remove(server);
        if (state != null)
            io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator.LOGGER
                    .info(
                            "B development world closed: generations={}, preparationMs={}, decorationMs={}, exactFullNeighborFallbacks={}, structureSourceQueries={}, structureSourceHits={}, structureSourceEvaluations={}",
                            state.generations.sum(),
                            state.preparation.sum() / 1_000_000L,
                            state.decoration.sum() / 1_000_000L,
                            state.fallbacks.sum(),
                            state.sourceQueries.sum(),
                            state.sourceHits.sum(),
                            state.sourceEvaluations.sum());
    }

    private static final class State {
        private final MosaicRuntimeContext runtime;
        private final LongAdder generations = new LongAdder(),
                preparation = new LongAdder(),
                decoration = new LongAdder(),
                fallbacks = new LongAdder(),
                sourceQueries = new LongAdder(),
                sourceHits = new LongAdder(),
                sourceEvaluations = new LongAdder();

        State(MosaicRuntimeContext runtime) {
            this.runtime = runtime;
        }
    }
}
