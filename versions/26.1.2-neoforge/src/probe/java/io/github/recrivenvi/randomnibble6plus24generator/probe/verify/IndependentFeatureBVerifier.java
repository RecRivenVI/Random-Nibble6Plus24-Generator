package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureBControl;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureStableSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeFeatureExecutionTrace;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.fml.ModList;

/** Fresh physical native prerequisites, one guarded native writer, independent detached B. */
public final class IndependentFeatureBVerifier {
    private static State active;

    private IndependentFeatureBVerifier() {}

    public static void run(MinecraftServer server) {
        String variant =
                System.getProperty(
                        "featureb.variant",
                        (Boolean.getBoolean("featureb.handoff")
                                        ? "independent_handoff"
                                        : "independent")
                                + (Boolean.getBoolean("featureb.smoke") ? "_smoke" : ""));
        require(
                variant.matches("[a-z0-9_-]+") && variant.startsWith("independent"),
                "Invalid independent evidence variant");
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve(variant)
                        .resolve(System.getProperty("validation.target"));
        try {
            var oldProfile =
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile
                            .MosaicWorldProfile.current();
            var bProfile =
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile
                            .MosaicWorldProfile.targetOnly();
            require(
                    oldProfile.featureOrderingAlgorithmVersion() == 1
                            && bProfile.featureOrderingAlgorithmVersion() == 2
                            && oldProfile.formatVersion() == bProfile.formatVersion()
                            && oldProfile.seedDerivationAlgorithmVersion()
                                    == bProfile.seedDerivationAlgorithmVersion()
                            && oldProfile.primaryDimension().equals(bProfile.primaryDimension()),
                    "Old default/new explicit profile factories changed unrelated contract");
            require(
                    !MosaicWorldIdentity.isMosaicWorld(server),
                    "Independent hosts must be ordinary Vanilla");
            var allowed =
                    Set.of(
                            "minecraft",
                            "java",
                            "neoforge",
                            "javafml",
                            "mixinextras",
                            ModConstants.MOD_ID,
                            ModConstants.MOD_ID + "_probe");
            for (var mod : ModList.get().getMods())
                require(
                        allowed.contains(mod.getModId()),
                        "Unexpected Oracle mod " + mod.getModId());
            if (Boolean.getBoolean("featureb.export")) {
                Files.createDirectories(base);
                var command = System.getProperty("sun.java.command").split(" ");
                var launch = new LinkedHashMap<String, Object>();
                launch.put("java", ProcessHandle.current().info().command().orElseThrow());
                launch.put("directory", System.getProperty("user.dir"));
                launch.put("classpath", System.getProperty("java.class.path"));
                launch.put("main", command[0]);
                launch.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
                launch.put(
                        "jvmArguments",
                        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                                .filter(a -> !a.startsWith("-Dfeatureb."))
                                .toList());
                write(base.resolve("launcher.json"), launch);
                write(base.resolve("fixtures.json"), IndependentFeatureBFixtures.fixtures());
                return;
            }
            var id = System.getProperty("featureb.fixture");
            var fixture =
                    IndependentFeatureBFixtures.fixtures().stream()
                            .filter(f -> f.id().equals(id))
                            .findFirst()
                            .orElseThrow();
            var role = System.getProperty("featureb.role");
            require(
                    Set.of("native_0", "native_1", "host_17", "host_23").contains(role),
                    "Unknown role");
            var dimension =
                    ResourceKey.create(
                            Registries.DIMENSION,
                            Identifier.withDefaultNamespace(fixture.dimension()));
            var level = Objects.requireNonNull(server.getLevel(dimension));
            long host = server.getWorldGenSettings().options().seed();
            boolean nativeRole = role.startsWith("native_");
            require(nativeRole == (host == fixture.seed()), "Independent seed boundary failed");
            var output = base.resolve(id).resolve(role);
            Files.createDirectories(output);
            active = new State(level, new ChunkPos(fixture.x(), fixture.z()), fixture.seed());
            var result = new LinkedHashMap<String, Object>();
            result.put("fixture", fixture);
            result.put("role", role);
            result.put("pid", ProcessHandle.current().pid());
            result.put(
                    "save",
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .toAbsolutePath()
                            .toString());
            result.put("hostSeed", Long.toString(host));
            result.put("inputHash", System.getProperty("featureb.inputHash"));
            var environment = PreFeaturesVerifier.environment(level);
            result.put("environment", environment);
            var parsedEnvironment = JsonParser.parseString(environment.toString());
            var transport = PreFeaturesVerifier.firstJsonDifference(environment, parsedEnvironment);
            if (transport != null
                    && transport.left().isJsonPrimitive()
                    && transport.left().getAsJsonPrimitive().isNumber()) {
                result.put(
                        "environmentNumberTransport",
                        Map.of(
                                "path", transport.path(),
                                "encodedType", transport.left().getAsNumber().getClass().getName(),
                                "parsedType", transport.right().getAsNumber().getClass().getName(),
                                "exactEncodedText",
                                        PreFeaturesVerifier.canonicalJson(environment)
                                                .toString()
                                                .equals(
                                                        PreFeaturesVerifier.canonicalJson(
                                                                        parsedEnvironment)
                                                                .toString())));
            }
            int loaded = level.getChunkSource().getLoadedChunksCount();
            int blockTicks = level.getBlockTicks().count(),
                    fluidTicks = level.getFluidTicks().count();
            long entities =
                    java.util.stream.StreamSupport.stream(
                                    level.getAllEntities().spliterator(), false)
                            .count();
            try {
                ProtoChunk chunk;
                List<CompoundTag> starts;
                if (nativeRole) {
                    NativeFeatureExecutionTrace.begin(List.of(active.target));
                    var generated =
                            NativeFeatureBControl.generate(
                                    level,
                                    active.target,
                                    () -> NativeFeatureExecutionTrace.beginWriter(active.target));
                    NativeFeatureExecutionTrace.completeWriter(active.target);
                    var trace = NativeFeatureExecutionTrace.finish();
                    chunk = generated.target();
                    starts = nativeStarts(level, chunk);
                    result.put(
                            "trace",
                            trace(
                                    trace.featureSeedInvocationCount(),
                                    trace.featureSeedSequenceHash(),
                                    trace.decorationSeedReads(),
                                    trace.maxConcurrentFeatureWriters(),
                                    trace.requestedWriters(),
                                    trace.completedWriters()));
                    result.put("rejected", new TreeMap<>(generated.rejected()));
                    result.put("nativeStructureReads", generated.nativeStructureReads());
                    result.put("outerReads", outerProofs(level, generated.outerReadInputs()));
                } else {
                    try (var context =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    level,
                                    fixture.seed(),
                                    active.target)) {
                        StrictFeatureBaseline.Result prepared = null;
                        if (Boolean.getBoolean("featureb.handoff")) {
                            var profile =
                                    io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .profile.MosaicWorldProfile.targetOnly();
                            var handoff =
                                    BFeaturePreparation.generate(
                                            context,
                                            profile,
                                            preparedInputs -> {
                                                result.put(
                                                        "physicalLevelEscapes",
                                                        preparedInputs
                                                                .region()
                                                                .levelEscapeCounts());
                                                result.put(
                                                        "outerReads",
                                                        outerProofs(
                                                                level,
                                                                preparedInputs
                                                                        .region()
                                                                        .outerReadInputs()));
                                            });
                            var source = (ProtoChunk) handoff.run().targetChunk();
                            var originalData =
                                    net.minecraft.world.level.chunk.storage.SerializableChunkData
                                            .copyOf(level, source)
                                            .write();
                            var artifact =
                                    io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .artifact.CanonicalChunkArtifact.capture(
                                            handoff.run(),
                                            dimension.identifier().toString(),
                                            fixture.seed(),
                                            profile,
                                            level.registryAccess(),
                                            StructurePieceSerializationContext.fromLevel(level));
                            var restored =
                                    artifact.rehydrate(
                                            level.registryAccess(),
                                            StructurePieceSerializationContext.fromLevel(level));
                            NbtIo.writeCompressed(
                                    originalData, output.resolve("handoff-before.nbt.gz"));
                            NbtIo.writeCompressed(
                                    net.minecraft.world.level.chunk.storage.SerializableChunkData
                                            .copyOf(level, restored)
                                            .write(),
                                    output.resolve("handoff-after.nbt.gz"));
                            var generatedStarts = new HashMap<>(source.getAllStarts());
                            try {
                                // This version's native Jigsaw loader recomputes boxes. Compare at
                                // the real load boundary, retaining the strict generation oracle.
                                source.setAllStarts(
                                        io.github.recrivenvi.randomnibble6plus24generator.mixin
                                                .SerializableChunkDataInvoker
                                                .randomnibble6plus24generator$unpackStructureStarts(
                                                        StructurePieceSerializationContext
                                                                .fromLevel(level),
                                                        originalData.getCompoundOrEmpty(
                                                                "structures"),
                                                        fixture.seed()));
                                var loadControl =
                                        FeatureStableSnapshot.capture(
                                                dimension.identifier().toString(),
                                                source,
                                                level.registryAccess());
                                require(
                                        loadControl
                                                .diff(
                                                        FeatureStableSnapshot.capture(
                                                                dimension.identifier().toString(),
                                                                restored,
                                                                level.registryAccess()))
                                                .equivalent(),
                                        "B artifact differs from native structure-load control");
                                NbtIo.writeCompressed(
                                        net.minecraft.world.level.chunk.storage
                                                .SerializableChunkData.copyOf(level, source)
                                                .write(),
                                        output.resolve("handoff-native-load-control.nbt.gz"));
                            } finally {
                                source.setAllStarts(generatedStarts);
                            }
                            require(
                                    source.getEntities().equals(restored.getEntities()),
                                    "B artifact changed raw entity NBT");
                            handoff.spawnBiomes()
                                    .validate(dimension, active.target, fixture.seed(), profile);
                            require(
                                    handoff.spawnBiomes()
                                            .positions()
                                            .equals(
                                                    java.util.Set.copyOf(
                                                            SpawnBiomeSnapshot.neighbors(
                                                                    active.target))),
                                    "B handoff lost spawn palettes");
                            result.put("artifactRoundtrip", true);
                            result.put("nativeLoadControl", true);
                            result.put("spawnBiomeCells", handoff.spawnBiomes().cellCount());
                            starts = handoff.externalStarts();
                            chunk = source;
                            result.put("rejected", new TreeMap<>(handoff.rejected()));
                        } else {
                            prepared =
                                    StrictFeatureBaseline.generate(
                                            context, (region, terrain) -> {});
                            chunk = prepared.target();
                            starts =
                                    prepared.structures()
                                            .captureStartTags(
                                                    StructurePieceSerializationContext.fromLevel(
                                                            level));
                            result.put("rejected", new TreeMap<>(prepared.rejected()));
                            result.put(
                                    "physicalLevelEscapes", prepared.region().levelEscapeCounts());
                            result.put(
                                    "outerReads",
                                    outerProofs(level, prepared.region().outerReadInputs()));
                        }
                        var trace = context.targetOnlyFeatureTrace();
                        require(
                                trace.observedWorldSeed() == fixture.seed(),
                                "Wrong local decoration seed");
                        result.put(
                                "trace",
                                trace(
                                        trace.featureSeedInvocationCount(),
                                        trace.featureSeedSequenceHash(),
                                        trace.decorationSeedReads(),
                                        trace.maxConcurrentFeatureWriters(),
                                        trace.requestedWriters(),
                                        trace.completedWriters()));
                    }
                    require(
                            loaded == level.getChunkSource().getLoadedChunksCount()
                                    && blockTicks == level.getBlockTicks().count()
                                    && fluidTicks == level.getFluidTicks().count()
                                    && entities
                                            == java.util.stream.StreamSupport.stream(
                                                            level.getAllEntities().spliterator(),
                                                            false)
                                                    .count(),
                            "B changed physical host");
                    result.put("physicalHostUnchanged", true);
                }
                require(
                        active.writers == 1 && active.terrainHash != null,
                        "Missing actual single writer");
                require(
                        chunk.getPersistedStatus() == ChunkStatus.FEATURES,
                        "Wrong B terminal status");
                var snapshot =
                        FeatureStableSnapshot.capture(
                                dimension.identifier().toString(), chunk, level.registryAccess());
                snapshot.write(output.resolve("features.bin.gz"));
                var raw = new ListTag();
                chunk.getEntities().forEach(e -> raw.add(e.copy()));
                var entityRoot = new CompoundTag();
                entityRoot.put("entities", raw);
                NbtIo.writeCompressed(entityRoot, output.resolve("entities-raw.nbt"));
                var projection = new JsonArray();
                starts.forEach(tag -> projection.add(PreFeaturesVerifier.canonicalNbt(tag)));
                write(output.resolve("structures.json"), projection);
                result.put("terrainHash", active.terrainHash);
                result.put("biomes", active.biomes);
                result.put("hash", snapshot.hash());
                result.put("entities", snapshot.entityCount());
                result.put("blockEntities", snapshot.blockEntityCount());
                result.put("blockTicks", snapshot.blockTickCount());
                result.put("fluidTicks", snapshot.fluidTickCount());
                result.put("postProcessing", snapshot.postProcessingCount());
                result.put("structurePlacements", active.structurePlacements);
                result.put("intersectingPieces", active.intersectingPieces);
                result.put(
                        "pendingBlockEntities",
                        chunk.getBlockEntitiesPos().stream()
                                .filter(
                                        p ->
                                                chunk.getBlockEntity(p) == null
                                                        && chunk.getBlockEntityNbt(p) != null)
                                .count());
                result.put("actualWriters", active.writers);
                require(GenerationContextRegistry.bindingCount() == 0, "B context leaked");
                result.put("bindings", 0);
                if (role.equals("native_0")) result.put("status", "PRODUCED");
                else {
                    var reference = base.resolve(id).resolve("native_0");
                    var before = FeatureStableSnapshot.read(reference.resolve("features.bin.gz"));
                    var beforeRaw =
                            NbtIo.readCompressed(
                                            reference.resolve("entities-raw.nbt"),
                                            NbtAccounter.unlimitedHeap())
                                    .getListOrEmpty("entities");
                    var comparison =
                            NativeEntityAcceptance.compare(
                                    before,
                                    beforeRaw,
                                    snapshot,
                                    raw,
                                    id.equals("end-city"),
                                    id.equals("spawn-village-cat"));
                    write(output.resolve("comparison.json"), comparison);
                    require(comparison.accepted(), "Independent B differs: " + comparison);
                    var expected =
                            JsonParser.parseString(
                                            Files.readString(reference.resolve("summary.json")))
                                    .getAsJsonObject();
                    var actual = new Gson().toJsonTree(result).getAsJsonObject();
                    for (var field :
                            List.of(
                                    "fixture",
                                    "inputHash",
                                    "environment",
                                    "trace",
                                    "terrainHash",
                                    "outerReads",
                                    "structurePlacements",
                                    "intersectingPieces",
                                    "biomes"))
                        require(
                                PreFeaturesVerifier.canonicalJson(expected.get(field))
                                        .toString()
                                        .equals(
                                                PreFeaturesVerifier.canonicalJson(actual.get(field))
                                                        .toString()),
                                "Independent prerequisite/trace differs: " + field);
                    require(
                            JsonParser.parseString(
                                            Files.readString(reference.resolve("structures.json")))
                                    .equals(projection),
                            "Referenced start/pieces differ");
                    result.put("status", "PASS");
                }
            } catch (Exception | Error failure) {
                result.put("status", "FAIL");
                result.put("failure", failure.toString());
                throw failure;
            } finally {
                write(output.resolve("summary.json"), result);
                active = null;
                NativeFeatureExecutionTrace.abort();
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Independent Vanilla B verification failed", failure);
        } finally {
            server.halt(false);
        }
    }

    public static void observeWriter(WorldGenLevel region, ChunkAccess chunk) {
        var state = Objects.requireNonNull(active, "Unexpected writer outside independent fixture");
        require(
                chunk.getPos().equals(state.target) && ++state.writers == 1,
                "Unexpected FEATURES writer");
        require(region.getSeed() == state.seed, "Actual writer seed differs");
        state.terrainHash =
                FeatureStableSnapshot.capture(
                                state.level.dimension().identifier().toString(),
                                chunk,
                                state.level.registryAccess())
                        .hash();
        var holders =
                new HashSet<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>();
        for (int z = state.target.z() - 1; z <= state.target.z() + 1; z++)
            for (int x = state.target.x() - 1; x <= state.target.x() + 1; x++)
                for (var section : region.getChunk(x, z, ChunkStatus.BIOMES, true).getSections())
                    section.getBiomes().getAll(holders::add);
        var registry = state.level.registryAccess().lookupOrThrow(Registries.BIOME);
        var names = new TreeSet<String>();
        holders.forEach(b -> names.add(registry.getKey(b.value()).toString()));
        state.biomes = List.copyOf(names);
    }

    private static Map<String, Object> outerProofs(
            ServerLevel level, Map<Long, ProtoChunk> inputs) {
        Map<String, Object> proof = new TreeMap<>();
        inputs.forEach(
                (key, chunk) ->
                        proof.put(
                                chunk.getPos().toString(),
                                Map.of(
                                        "status", chunk.getPersistedStatus().toString(),
                                        "hash",
                                                FeatureStableSnapshot.capture(
                                                                level.dimension()
                                                                        .identifier()
                                                                        .toString(),
                                                                chunk,
                                                                level.registryAccess())
                                                        .hash())));
        return proof;
    }

    public static void observeStructure(StructureStart start, WorldGenLevel world, ChunkPos pos) {
        if (!Boolean.getBoolean("featureb.independent")) return;
        var state = Objects.requireNonNull(active, "Structure placement outside fixture");
        require(
                state.target.equals(pos) && world instanceof StrictFeatureRegion,
                "Structure escaped B writer");
        var type =
                state.level
                        .registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getKey(start.getStructure())
                        .toString();
        state.structurePlacements.merge(type, 1, Integer::sum);
        state.intersectingPieces +=
                (int)
                        start.getPieces().stream()
                                .filter(
                                        piece ->
                                                piece.getBoundingBox()
                                                        .intersects(
                                                                pos.getMinBlockX(),
                                                                pos.getMinBlockZ(),
                                                                pos.getMaxBlockX(),
                                                                pos.getMaxBlockZ()))
                                .count();
    }

    private static Map<String, Object> trace(
            long calls,
            long hash,
            long seedReads,
            int concurrency,
            List<ChunkPos> requested,
            List<ChunkPos> completed) {
        require(
                calls > 0
                        && seedReads == 1
                        && concurrency == 1
                        && requested.equals(completed)
                        && requested.size() == 1,
                "Incomplete native/isolated writer trace");
        return Map.of(
                "featureSeedCalls",
                calls,
                "featureSeedSequence",
                Long.toString(hash),
                "decorationSeedReads",
                seedReads,
                "maxConcurrentWriters",
                concurrency,
                "requested",
                requested,
                "completed",
                completed);
    }

    private static List<CompoundTag> nativeStarts(ServerLevel level, ChunkAccess chunk) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var starts = new TreeMap<String, StructureStart>();
        chunk.getAllStarts()
                .forEach(
                        (type, start) -> {
                            if (start.isValid())
                                starts.put(
                                        registry.getKey(type) + "@" + start.getChunkPos(), start);
                        });
        chunk.getAllReferences()
                .forEach(
                        (type, references) -> {
                            for (long packed : references) {
                                var pos = ChunkPos.unpack(packed);
                                var start =
                                        level.getChunk(
                                                        pos.x(),
                                                        pos.z(),
                                                        ChunkStatus.STRUCTURE_STARTS,
                                                        true)
                                                .getStartForStructure(type);
                                require(
                                        start != null && start.isValid(),
                                        "Missing native referenced start");
                                starts.put(registry.getKey(type) + "@" + pos, start);
                            }
                        });
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        return starts.values().stream()
                .map(s -> s.createTag(serialization, s.getChunkPos()))
                .toList();
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        PreFeaturesVerifier.write(path, new Gson().toJsonTree(value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class State {
        final ServerLevel level;
        final ChunkPos target;
        final long seed;
        int writers;
        String terrainHash;
        List<String> biomes;
        final Map<String, Integer> structurePlacements = new TreeMap<>();
        int intersectingPieces;

        State(ServerLevel level, ChunkPos target, long seed) {
            this.level = level;
            this.target = target;
            this.seed = seed;
        }
    }
}
