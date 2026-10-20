package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
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

/** Fresh physical native prerequisites, one guarded native writer, independent detached B. */
public final class IndependentFeatureBVerifier {
    private static State active;

    private IndependentFeatureBVerifier() {}

    private static List<FeatureBVerifier.Fixture> fixtures() {
        var fixtures = new ArrayList<>(BPreparationVerifier.fixtures());
        if (Boolean.getBoolean("featureb.naturalNether")) {
            var pos = new ChunkPos(-10, -10);
            long seed =
                    new io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed
                                    .MosaicSeedResolver(MosaicWorldProfile.targetOnly())
                            .resolveLocalWorldSeed(123456789L, Level.NETHER, pos);
            fixtures.add(
                    new FeatureBVerifier.Fixture(
                            "nether-chests", "the_nether", seed, pos.x(), pos.z()));
        }
        return List.copyOf(fixtures);
    }

    static void run(MinecraftServer server) {
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                Boolean.getBoolean("featureb.smoke")
                                        ? "independent_smoke"
                                        : "independent")
                        .resolve(System.getProperty("validation.target"));
        try {
            require(
                    !MosaicWorldIdentity.isMosaicWorld(server),
                    "Independent hosts must be ordinary Vanilla");
            var allowed =
                    Set.of(
                            "minecraft",
                            "java",
                            "fabricloader",
                            "mixinextras",
                            "fabric-api-base",
                            "fabric-networking-api-v1",
                            "fabric-registry-sync-v0",
                            ModConstants.MOD_ID,
                            ModConstants.MOD_ID + "_probe");
            for (var mod : FabricLoader.getInstance().getAllMods())
                require(
                        allowed.contains(mod.getMetadata().getId()),
                        "Unexpected Oracle mod " + mod.getMetadata().getId());
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
                write(base.resolve("fixtures.json"), fixtures());
                return;
            }
            var id = System.getProperty("featureb.fixture");
            var fixture =
                    fixtures().stream().filter(f -> f.id().equals(id)).findFirst().orElseThrow();
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
                PiglinAttributeOrderTrace.begin();
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
                } else {
                    ProtoChunk retained = null;
                    FeatureStableSnapshot retainedBefore = null;
                    if (Boolean.getBoolean("featureb.retainedTerrain")) {
                        try (var sourceContext =
                                        IsolatedGenerationContext.create(
                                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                                level,
                                                fixture.seed(),
                                                active.target,
                                                MosaicWorldProfile.targetOnly());
                                var sources =
                                        io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                                .structure.ScopedStructureSources.create(
                                                sourceContext)) {
                            var structures =
                                    io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .structure.CompactStructureInputs.resolve(
                                            sourceContext, sources);
                            retained =
                                    io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .materialization.DetachedTerrainInputs.generate(
                                                    sourceContext, structures, (stage, input) -> {})
                                            .target();
                        }
                        if (Boolean.getBoolean("featureb.retainedTerrainReload")) {
                            var serialized =
                                    net.minecraft.world.level.chunk.storage.SerializableChunkData
                                            .copyOf(level, retained)
                                            .write();
                            var path = output.resolve("terrain-disk-input.nbt.gz");
                            net.minecraft.nbt.NbtIo.writeCompressed(serialized, path);
                            var disk =
                                    net.minecraft.nbt.NbtIo.readCompressed(
                                            path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                            var parsed =
                                    net.minecraft.world.level.chunk.storage.SerializableChunkData
                                            .parse(level, level.palettedContainerFactory(), disk);
                            require(parsed != null, "Native TERRAIN parser rejected disk input");
                            retained =
                                    parsed.read(
                                            level,
                                            level.getPoiManager(),
                                            new net.minecraft.world.level.chunk.storage
                                                    .RegionStorageInfo(
                                                    "retained-terrain-control", dimension, "chunk"),
                                            active.target);
                            retained.setAllStarts(
                                    io.github.recrivenvi.randomnibble6plus24generator.mixin
                                            .SerializableChunkDataInvoker
                                            .randomnibble6plus24generator$unpackStructureStarts(
                                                    StructurePieceSerializationContext.fromLevel(
                                                            level),
                                                    disk.getCompoundOrEmpty("structures"),
                                                    fixture.seed()));
                        }
                        retainedBefore =
                                FeatureStableSnapshot.capture(
                                        dimension.identifier().toString(),
                                        retained,
                                        level.registryAccess());
                        retainedBefore.write(output.resolve("terrain-input.bin.gz"));
                    }
                    try (var context =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    level,
                                    fixture.seed(),
                                    active.target,
                                    MosaicWorldProfile.targetOnly())) {
                        var prepared =
                                retained == null
                                        ? BFeaturePreparation.generate(
                                                context, MosaicWorldProfile.targetOnly())
                                        : BFeaturePreparation.generateFromTerrain(
                                                context, MosaicWorldProfile.targetOnly(), retained);
                        if (retained != null) {
                            require(
                                    prepared.retainedTerrain(),
                                    "Detached TERRAIN unexpectedly fell back instead of exercising the copy");
                            require(
                                    retainedBefore
                                            .diff(
                                                    FeatureStableSnapshot.capture(
                                                            dimension.identifier().toString(),
                                                            retained,
                                                            level.registryAccess()))
                                            .equivalent(),
                                    "Decoration mutated the original physical TERRAIN input");
                            result.put(
                                    "retainedTerrain",
                                    Map.of(
                                            "nativeDiskReload",
                                            Boolean.getBoolean("featureb.retainedTerrainReload"),
                                            "copied",
                                            true,
                                            "sourceUnchanged",
                                            true,
                                            "sourceHash",
                                            retainedBefore.hash()));
                        }
                        chunk = (ProtoChunk) prepared.run().targetChunk();
                        starts = prepared.externalStarts();
                        var trace = prepared.run().featureTrace();
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
                        require(
                                prepared.structureReuse().closed()
                                        && prepared.structureReuse().resident() == 0,
                                "Detached structure scope leaked");
                        result.put("readCounters", prepared.reads());
                        result.put("physicalLevelEscapes", prepared.levelEscapes());
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
                if (fixture.id().equals("nether-chests"))
                    require(
                            snapshot.blockEntityCount() > 0
                                    && active.intersectingPieces > 0
                                    && !active.structurePlacements.isEmpty(),
                            "Real Nether structure/BlockEntity positive was not hit");
                snapshot.write(output.resolve("features.bin.gz"));
                var raw = new ListTag();
                chunk.getEntities().forEach(e -> raw.add(e.copy()));
                var entityRoot = new CompoundTag();
                entityRoot.put("entities", raw);
                NbtIo.writeCompressed(entityRoot, output.resolve("entities-raw.nbt"));
                PiglinAttributeOrderTrace.finish(output, raw, snapshot);
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
                                    id.equals("spawn-village-cat"),
                                    id.equals("nether-chests"));
                    if (id.equals("nether-chests"))
                        result.put("finiteEntityRule", NativeEntityAcceptance.PIGLIN_ORDER_RULE);
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
                PiglinAttributeOrderTrace.abort();
                NativeFeatureExecutionTrace.abort();
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Independent Vanilla B verification failed", failure);
        } finally {
            server.halt(false);
        }
    }

    static void observeWriter(WorldGenLevel region, ChunkAccess chunk) {
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
                region.getChunk(x, z, ChunkStatus.BIOMES, true).collectBiomesInPalette(holders);
        var registry = state.level.registryAccess().lookupOrThrow(Registries.BIOME);
        var names = new TreeSet<String>();
        holders.forEach(b -> names.add(registry.getKey(b.value()).toString()));
        state.biomes = List.copyOf(names);
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
