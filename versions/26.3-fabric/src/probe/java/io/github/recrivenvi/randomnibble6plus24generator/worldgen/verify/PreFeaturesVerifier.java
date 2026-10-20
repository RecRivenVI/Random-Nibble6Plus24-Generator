package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Independent physical TERRAIN producer and unrelated-host reference consumer; probe-only. */
public final class PreFeaturesVerifier {
    private static final String PREFIX = RandomNibble6Plus24Generator.MOD_ID + ".prefeatures.";
    private static final List<ChunkStatus> STAGES =
            List.of(
                    ChunkStatus.STRUCTURE_STARTS, ChunkStatus.STRUCTURE_REFERENCES,
                    ChunkStatus.BIOMES, ChunkStatus.TERRAIN);
    private static volatile Run active;

    private PreFeaturesVerifier() {}

    private static List<ChunkStatus> expectedStages() {
        return Boolean.getBoolean(PREFIX + "biomeOnly") ? STAGES.subList(0, 3) : STAGES;
    }

    private static void assertDetachedBiomeData(ChunkAccess chunk) {
        if (chunk.getClass() != net.minecraft.world.level.chunk.ProtoChunk.class
                || chunk.getPersistedStatus() != ChunkStatus.BIOMES)
            throw new IllegalStateException("Detached BIOMES is not a real native ProtoChunk");
        for (Class<?> type = chunk.getClass(); type != null; type = type.getSuperclass())
            for (var field : type.getDeclaredFields())
                if (field.getType() == net.minecraft.world.level.levelgen.NoiseChunk.class
                        || field.getType()
                                == net.minecraft.world.level.levelgen.densityfunction.SamplerContext
                                        .class
                        || field.getType() == IsolatedGenerationContext.class)
                    throw new IllegalStateException("Unaudited native BIOMES ownership layout");
    }

    public static void run(MinecraftServer server) {
        if (Boolean.getBoolean(BopCausalTrace.PROPERTY)) {
            BopCausalVerifier.run(server);
            return;
        }
        if (BopContextVerifier.enabled() && Boolean.getBoolean(PREFIX + "featureB")) {
            BopFeaturesVerifier.run(server);
            return;
        }
        if (MosaicWorldIdentity.isMosaicWorld(server))
            throw new IllegalStateException(
                    "Oracle/reference hosts must be ordinary Vanilla worlds");
        Path root =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                Boolean.getBoolean(PREFIX + "lightweight")
                                                || Boolean.getBoolean(PREFIX + "biomeOnly")
                                                || BopContextVerifier.enabled()
                                        ? System.getProperty(PREFIX + "variant", "terrain")
                                        : Boolean.getBoolean(PREFIX + "structures")
                                                ? "compact"
                                                : "current")
                        .resolve(System.getProperty("validation.target"));
        if (Boolean.getBoolean(PREFIX + "export")) {
            if (Boolean.getBoolean(PREFIX + "climateAudit")) {
                try {
                    var audit =
                            Boolean.getBoolean(PREFIX + "ringAudit")
                                    ? io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .structure.RingContinuationProbe.run(
                                            server, required("inputHash"))
                                    : io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .structure.ClimateContinuationProbe.run(
                                            server, required("inputHash"));
                    audit.add("environment", environment(server.overworld()));
                    write(root.resolve(required("auditRole")).resolve("summary.json"), audit);
                } catch (Exception failure) {
                    throw new IllegalStateException(
                            "Native climate component audit failed", failure);
                }
                server.halt(false);
                return;
            }
            if (Boolean.getBoolean(PREFIX + "structures"))
                CompactStructureFixtures.export(server, root);
            else exportPlan(server, root);
            exportLauncher(root);
            server.halt(false);
            return;
        }
        String role = required("role");
        if (!role.equals("native")
                && !role.equals("reference")
                && !role.equals("compact")
                && !role.equals("lightweight")
                && !role.equals("biome-only"))
            throw new IllegalArgumentException("Unknown pre-FEATURES role " + role);
        String id = required("fixture");
        if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Unsafe fixture id");
        ResourceKey<Level> dimension =
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(required("dimension")));
        ServerLevel level = Objects.requireNonNull(server.getLevel(dimension));
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator))
            throw new IllegalStateException("Native Vanilla NoiseBasedChunkGenerator required");
        ChunkPos pos =
                new ChunkPos(Integer.parseInt(required("x")), Integer.parseInt(required("z")));
        long master = Long.parseLong(required("master"));
        long local =
                System.getProperty(PREFIX + "local") != null
                        ? Long.parseLong(required("local"))
                        : new MosaicSeedResolver(MosaicWorldProfile.current())
                                .resolveLocalWorldSeed(master, dimension, pos);
        long host = server.getWorldGenSettings().options().seed();
        if ((role.equals("native") && host != local) || (!role.equals("native") && host == local))
            throw new IllegalStateException(
                    "Invalid independent world seed: host=" + host + " local=" + local);
        Run run = new Run(role, level, pos, root.resolve(id).resolve(role));
        active = run;
        if (Boolean.getBoolean(PREFIX + "lightweight"))
            TerrainProbeEvidence.begin(run.output, pos, local, level);
        JsonObject result = new JsonObject();
        result.addProperty("schemaVersion", 1);
        result.addProperty("fixture", id);
        result.addProperty("role", role);
        result.addProperty("masterSeed", Long.toString(master));
        result.addProperty("localSeed", Long.toString(local));
        result.addProperty("hostSeed", Long.toString(host));
        result.addProperty("dimension", dimension.identifier().toString());
        result.addProperty("chunkX", pos.x());
        result.addProperty("chunkZ", pos.z());
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .toString());
        result.add("environment", environment(level));
        long started = System.nanoTime();
        try {
            if (BopContextVerifier.enabled()) {
                var observations =
                        BopContextVerifier.observe(level, local, pos, role.equals("native"));
                result.add("bopContext", observations);
                if (!role.equals("native")) {
                    JsonObject oracle;
                    try {
                        oracle =
                                JsonParser.parseString(
                                                Files.readString(
                                                        root.resolve(id)
                                                                .resolve("native/summary.json")))
                                        .getAsJsonObject()
                                        .getAsJsonObject("bopContext");
                    } catch (IOException failure) {
                        throw new IllegalStateException(
                                "Missing BOP native seed evidence", failure);
                    }
                    if (!oracle.get("seedGrid").equals(observations.get("seedGrid")))
                        throw new IllegalStateException(
                                "BOP local-seed biome grid differs from independent native");
                }
            }
            if (role.equals("compact")) {
                try (var context =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC, level, local, pos)) {
                    run.compact = CompactStructureInputs.resolve(context);
                }
                result.add(
                        "compactInputs",
                        new GsonBuilder().create().toJsonTree(run.compact.metrics()));
            }
            if (role.equals("biome-only")) {
                int beforeChunks = level.getChunkSource().getLoadedChunksCount();
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .DetachedBiomeInputs.Result
                        prepared;
                try (var context =
                                IsolatedGenerationContext.create(
                                        IsolatedGenerationMode.ISOLATED_MOSAIC, level, local, pos);
                        var sources =
                                io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                        .ScopedStructureSources.create(context)) {
                    run.compact = CompactStructureInputs.resolve(context, sources);
                    result.add(
                            "compactInputs",
                            new GsonBuilder().create().toJsonTree(run.compact.metrics()));
                    prepared =
                            io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                    .materialization.DetachedBiomeInputs.generate(
                                    context,
                                    run.compact,
                                    (stage, chunk) ->
                                            capture(
                                                    context.worldGenContext(),
                                                    net.minecraft.world.level.chunk.status
                                                            .ChunkPyramid.GENERATION_PYRAMID
                                                            .getStepTo(stage),
                                                    null,
                                                    chunk));
                    var mapField = IsolatedGenerationContext.class.getDeclaredField("chunkMap");
                    if (!mapField.trySetAccessible())
                        throw new IllegalStateException("Cannot inspect isolated holder ownership");
                    var map = mapField.get(context);
                    var countMethod = map.getClass().getDeclaredMethod("virtualChunkCount");
                    if (!countMethod.trySetAccessible())
                        throw new IllegalStateException("Cannot inspect isolated holder count");
                    int count = (Integer) countMethod.invoke(map);
                    if (count != 0)
                        throw new IllegalStateException("Biome-only input created virtual chunks");
                    result.addProperty("virtualChunks", count);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
                assertDetachedBiomeData(prepared.target());
                // Re-read every saved field after owner close, not only the in-scope checkpoint.
                var afterClose =
                        FeatureStageSnapshot.capture(
                                level.dimension().identifier().toString(),
                                prepared.target(),
                                level.registryAccess());
                if (!afterClose
                        .diff(FeatureStageSnapshot.read(run.output.resolve("biomes.bin.gz")))
                        .equivalent())
                    throw new IllegalStateException(
                            "Detached BIOMES changed after closing its owner");
                try {
                    var persisted =
                            net.minecraft.nbt.NbtIo.readCompressed(
                                    run.output.resolve("biomes-native.nbt.gz"),
                                    net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                    if (!persisted.equals(
                            net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(
                                            level, prepared.target())
                                    .write()))
                        throw new IllegalStateException(
                                "Detached BIOMES serialization changed after owner close");
                } catch (IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
                if (beforeChunks != level.getChunkSource().getLoadedChunksCount())
                    throw new IllegalStateException(
                            "Biome-only input touched physical host chunks");
                result.addProperty("detachedAfterClose", true);
                result.addProperty("serializationAfterClose", true);
                result.add(
                        "detachedBiomes",
                        new GsonBuilder().create().toJsonTree(prepared.metrics()));
            } else if (role.equals("lightweight")) {
                try (var context =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC, level, local, pos)) {
                    if (Boolean.getBoolean(PREFIX + "structureReuse")) {
                        var reuse =
                                new io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                        .structure.ScopedStructureSources(context);
                        try (reuse) {
                            CompactStructureInputs.resolve(context, reuse);
                            for (int z = -1; z <= 1; z++)
                                for (int x = -1; x <= 1; x++) {
                                    if (x == 0 && z == 0) continue;
                                    try (var child =
                                            context.createRelated(
                                                    new ChunkPos(pos.x() + x, pos.z() + z))) {
                                        CompactStructureInputs.resolve(child, reuse);
                                    }
                                }
                            run.compact = CompactStructureInputs.resolve(context, reuse);
                        }
                        result.add(
                                "structureReuse",
                                new GsonBuilder().create().toJsonTree(reuse.metrics()));
                    } else run.compact = CompactStructureInputs.resolve(context);
                    result.add(
                            "compactInputs",
                            new GsonBuilder().create().toJsonTree(run.compact.metrics()));
                    try (var biomes =
                            Boolean.getBoolean(PREFIX + "structureReuse")
                                    ? new io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .terrain.TerrainBiomeInputs(context)
                                    : null) {
                        // Exercise the same completed-palette copy used by a B neighbor fallback.
                        if (biomes != null) biomes.prepare(context, pos);
                        java.util.function.BiConsumer<ChunkStatus, ChunkAccess> captureStage =
                                (stage, chunk) ->
                                        capture(
                                                context.worldGenContext(),
                                                net.minecraft.world.level.chunk.status.ChunkPyramid
                                                        .GENERATION_PYRAMID
                                                        .getStepTo(stage),
                                                null,
                                                chunk);
                        var generated =
                                Boolean.getBoolean(PREFIX + "nativeTerrain")
                                        ? io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                                .materialization.DetachedTerrainInputs.generate(
                                                context, run.compact, captureStage)
                                        : io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                                .terrain.LightweightTerrainGenerator.generate(
                                                context,
                                                run.compact,
                                                (stage, chunk) ->
                                                        capture(
                                                                context.worldGenContext(),
                                                                net.minecraft.world.level.chunk
                                                                        .status.ChunkPyramid
                                                                        .GENERATION_PYRAMID
                                                                        .getStepTo(stage),
                                                                null,
                                                                chunk),
                                                biomes);
                        if (Boolean.getBoolean(PREFIX + "nativeTerrain")) {
                            if (generated.target().getPersistedStatus() != ChunkStatus.TERRAIN)
                                throw new IllegalStateException(
                                        "Detached terrain escaped its native stage");
                            run.detachedTerrain = generated.target();
                        }
                        result.add(
                                "lightweightTerrain",
                                new GsonBuilder().create().toJsonTree(generated.metrics()));
                        if (biomes != null) {
                            biomes.close();
                            result.add(
                                    "biomeReuse",
                                    new GsonBuilder().create().toJsonTree(biomes.counters()));
                        }
                    }
                    if (TerrainProbeEvidence.virtualHolders() != 0)
                        throw new IllegalStateException("Lightweight path created holders");
                    result.addProperty("virtualChunks", 0);
                }
                if (Boolean.getBoolean(PREFIX + "nativeTerrain")) {
                    var chunk = Objects.requireNonNull(run.detachedTerrain);
                    if (!FeatureStageSnapshot.capture(
                                    level.dimension().identifier().toString(),
                                    chunk,
                                    level.registryAccess())
                            .diff(FeatureStageSnapshot.read(run.output.resolve("terrain.bin.gz")))
                            .equivalent())
                        throw new IllegalStateException(
                                "Detached terrain changed after owner close");
                    try {
                        var raw =
                                net.minecraft.nbt.NbtIo.readCompressed(
                                        run.output.resolve("terrain-native.nbt.gz"),
                                        net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                        if (!raw.equals(
                                net.minecraft.world.level.chunk.storage.SerializableChunkData
                                        .copyOf(level, chunk)
                                        .write()))
                            throw new IllegalStateException(
                                    "Detached terrain serialization changed after owner close");
                    } catch (IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                    result.addProperty("detachedAfterClose", true);
                    result.addProperty("serializationAfterClose", true);
                    run.detachedTerrain = null;
                }
            } else if (role.equals("native")) {
                ChunkStatus boundary =
                        Boolean.getBoolean(PREFIX + "biomeOnly")
                                ? ChunkStatus.BIOMES
                                : ChunkStatus.TERRAIN;
                ChunkAccess target = level.getChunk(pos.x(), pos.z(), boundary, true);
                if (target.getPersistedStatus() != boundary)
                    throw new IllegalStateException("Native stage boundary escaped " + boundary);
            } else {
                // Explicit seed fixtures test the structure algorithm independently of seed
                // derivation.
                var generated =
                        System.getProperty(PREFIX + "local") == null
                                ? new IsolatedGenerationSession(MosaicWorldProfile.current())
                                        .generateCarvers(level, master, pos)
                                : generateAtSeed(level, local, pos);
                if (generated.executedStages().contains(ChunkStatus.FEATURES))
                    throw new IllegalStateException("Reference ran FEATURES");
                if (generated.carverTrace().observedWorldSeed() != local)
                    throw new IllegalStateException("TERRAIN received the wrong world seed");
                result.addProperty("virtualChunks", generated.metrics().virtualChunkCount());
            }
            if (GenerationContextRegistry.bindingCount() != 0)
                throw new IllegalStateException("Generation context leaked");
            if (!run.completed.equals(expectedStages()))
                throw new IllegalStateException(
                        "Missing/out-of-order checkpoints " + run.completed);
            result.addProperty("featuresExecuted", 0);
            if (Boolean.getBoolean(PREFIX + "lightweight")) TerrainProbeEvidence.finish();
            result.add("checkpoints", run.checkpoints);
            if (!role.equals("native")) compare(root.resolve(id).resolve("native"), run, result);
            else result.addProperty("status", "PRODUCED");
        } catch (RuntimeException | Error failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("failure", failure.toString());
            throw failure;
        } finally {
            TerrainProbeEvidence.close();
            active = null;
            result.addProperty("elapsedMs", (System.nanoTime() - started) / 1_000_000);
            write(run.output.resolve("summary.json"), result);
            server.halt(false);
        }
        if (!result.get("status").getAsString().equals("PASS") && !role.equals("native"))
            throw new IllegalStateException(
                    "Pre-FEATURES parity failed: " + result.get("differences"));
        System.out.println("PRE-FEATURES " + id + " " + role + " " + result.get("status"));
    }

    private static io.github.recrivenvi.randomnibble6plus24generator.worldgen.session
                    .CarverGenerationRun
            generateAtSeed(ServerLevel level, long seed, ChunkPos pos) {
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, level, seed, pos)) {
            return context.generateCarvers();
        }
    }

    public static CompactStructureInputs compactInputs(ChunkAccess chunk) {
        Run run = active;
        return run != null && run.role.equals("compact") && run.target.equals(chunk.getPos())
                ? run.compact
                : null;
    }

    public static void requirePreFeatures(ChunkStatus status) {
        if (status.isOrAfter(ChunkStatus.FEATURES))
            throw new IllegalStateException("Pre-FEATURES Oracle contaminated by " + status);
    }

    public static boolean shouldCapture(WorldGenContext context, ChunkAccess chunk) {
        Run run = active;
        return run != null && context.level() == run.level && chunk.getPos().equals(run.target);
    }

    public static void capture(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        Run run = Objects.requireNonNull(active);
        ChunkStatus status = step.targetStatus();
        if (!STAGES.contains(status)) return;
        if (BopContextVerifier.enabled() && status == ChunkStatus.BIOMES) {
            long count = BopContextVerifier.storedBop(chunk);
            write(
                    run.output.resolve("bop-palette.json"),
                    new GsonBuilder().create().toJsonTree(Map.of("bopQuartEntries", count)));
            if (required("fixture").startsWith("bop-") && count == 0)
                throw new IllegalStateException(
                        "BOP positive fixture has no generated BOP palette");
        }
        boolean isolated = GenerationContextRegistry.find(context).isPresent();
        if (isolated != !run.role.equals("native") || chunk.getPersistedStatus() != status)
            throw new IllegalStateException("Incorrect stage/context at " + status);
        String name = stageName(status);
        FeatureStageSnapshot snapshot =
                status.isBefore(ChunkStatus.BIOMES)
                        ? null
                        : FeatureStageSnapshot.capture(
                                run.level.dimension().identifier().toString(),
                                chunk,
                                run.level.registryAccess());
        JsonObject structures = structureInputs(run.level, chunk, cache);
        if ((Boolean.getBoolean(PREFIX + "biomeOnly") && status == ChunkStatus.BIOMES)
                || (Boolean.getBoolean(PREFIX + "nativeTerrain")
                        && status == ChunkStatus.TERRAIN)) {
            var raw =
                    net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(
                                    run.level, chunk)
                            .write();
            var parsed =
                    net.minecraft.world.level.chunk.storage.SerializableChunkData.parse(
                            run.level, run.level.palettedContainerFactory(), raw.copy());
            if (parsed == null || parsed.chunkStatus() != status || !raw.equals(parsed.write()))
                throw new IllegalStateException(
                        "Native partial BIOMES codec did not roundtrip exactly");
            try {
                net.minecraft.nbt.NbtIo.writeCompressed(
                        raw, run.output.resolve(name + "-native.nbt.gz"));
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
        if (Boolean.getBoolean(PREFIX + "structures")
                && status == ChunkStatus.STRUCTURE_REFERENCES) {
            // Detached persistence input includes every referenced start, not a writer-ring subset.
            JsonArray projection = new JsonArray();
            var serialization = StructurePieceSerializationContext.fromLevel(run.level);
            if (run.compact != null) {
                for (CompoundTag tag : run.compact.captureStartTags(serialization))
                    projection.add(canonicalNbt(tag));
            } else {
                for (String key : new TreeSet<>(structures.keySet())) {
                    if (!key.startsWith("#")
                            && !structures
                                    .getAsJsonObject(key)
                                    .get("terrainAdaptation")
                                    .getAsString()
                                    .equals("invalid"))
                        projection.add(structures.getAsJsonObject(key).get("nbt"));
                }
            }
            write(run.output.resolve("projection.json"), projection);
            // Mutating caller-owned persistence data must not mutate the live compact starts.
            if (run.compact != null) {
                var copies = run.compact.captureStartTags(serialization);
                if (!copies.isEmpty())
                    copies.getFirst().putString("probeMutation", "not-authority");
                JsonArray replay = new JsonArray();
                run.compact
                        .captureStartTags(serialization)
                        .forEach(tag -> replay.add(canonicalNbt(tag)));
                if (!projection.equals(replay))
                    throw new IllegalStateException("Mutable compact persistence alias");
            }
        }
        if (Boolean.getBoolean(PREFIX + "structures") && status == ChunkStatus.BIOMES) {
            // REFERENCES regions permit starts, not completed references. The BIOMES boundary
            // permits both and is still before terrain/decoration writes.
            WorldGenRegion region =
                    run.compact == null ? new WorldGenRegion(run.level, cache, step, chunk) : null;
            var localContext = GenerationContextRegistry.find(context);
            var manager =
                    run.compact != null
                            ? run.compact.manager()
                            : localContext
                                    .map(
                                            value ->
                                                    value.structureManager()
                                                            .forWorldGenRegion(region))
                                    .orElseGet(
                                            () ->
                                                    run.level
                                                            .structureManager()
                                                            .forWorldGenRegion(region));
            captureBeardifier(run, Beardifier.forStructuresInChunk(manager, chunk.getPos()));
        }
        synchronized (run) {
            if (run.completed.contains(status))
                throw new IllegalStateException("Duplicate " + status);
            if (snapshot != null) snapshot.write(run.output.resolve(name + ".bin.gz"));
            write(run.output.resolve(name + "-structures.json"), structures);
            JsonObject checkpoint = new JsonObject();
            checkpoint.addProperty("stage", name);
            checkpoint.addProperty(
                    "snapshotSha256", snapshot == null ? "not-yet-biomes" : snapshot.hash());
            checkpoint.addProperty(
                    "structureInputSha256", sha256(canonicalJson(structures).toString()));
            checkpoint.addProperty("structureInputCount", structures.size() - 1);
            checkpoint.addProperty(
                    "dependencyRadius",
                    step.accumulatedDependencies().getRadiusOf(ChunkStatus.EMPTY));
            run.checkpoints.add(checkpoint);
            run.completed.add(status);
        }
    }

    private static JsonObject structureInputs(
            ServerLevel level, ChunkAccess chunk, StaticCache2D<GenerationChunkHolder> cache) {
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Map<String, StructureStart> starts = new TreeMap<>();
        chunk.getAllStarts()
                .forEach(
                        (structure, start) ->
                                starts.put(
                                        registry.getKey(structure) + "@" + start.getChunkPos(),
                                        start));
        chunk.getAllReferences()
                .forEach(
                        (structure, references) -> {
                            for (long packed : references) {
                                ChunkPos pos = ChunkPos.unpack(packed);
                                if (active.compact == null && !cache.contains(pos.x(), pos.z()))
                                    throw new IllegalStateException(
                                            "Reference outside Vanilla dependency cache " + pos);
                                ChunkAccess source =
                                        active.compact != null
                                                ? null
                                                : cache.get(pos.x(), pos.z())
                                                        .getChunkIfPresentUnchecked(
                                                                ChunkStatus.STRUCTURE_STARTS);
                                StructureStart start =
                                        active.compact != null
                                                ? active.compact.referencedStart(structure, pos)
                                                : source == null
                                                        ? null
                                                        : source.getStartForStructure(structure);
                                if (start == null || !start.isValid())
                                    throw new IllegalStateException(
                                            "Missing referenced start "
                                                    + registry.getKey(structure)
                                                    + "@"
                                                    + pos);
                                starts.put(registry.getKey(structure) + "@" + pos, start);
                            }
                        });
        JsonObject result = new JsonObject();
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        starts.forEach(
                (key, start) -> {
                    JsonObject input = new JsonObject();
                    input.addProperty(
                            "nbt",
                            canonicalNbt(start.createTag(serialization, start.getChunkPos())));
                    input.addProperty(
                            "terrainAdaptation",
                            start.isValid()
                                    ? start.getStructure().terrainAdaptation().toString()
                                    : "invalid");
                    input.addProperty(
                            "boundingBox",
                            start.isValid() ? start.getBoundingBox().toString() : "invalid");
                    input.addProperty("pieces", start.getPieces().size());
                    result.add(key, input);
                });
        JsonObject references = new JsonObject();
        chunk.getAllReferences()
                .forEach(
                        (structure, positions) -> {
                            JsonArray values = new JsonArray();
                            java.util.Arrays.stream(positions.toLongArray())
                                    .sorted()
                                    .forEach(value -> values.add(Long.toString(value)));
                            references.add(registry.getKey(structure).toString(), values);
                        });
        result.add("#references", references);
        return result;
    }

    private static void captureBeardifier(Run run, Beardifier beardifier) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream output = new java.io.DataOutputStream(bytes);
            int nonzero = 0;
            int samples = 0;
            var sampler = SamplerContext.builder().build();
            for (int z = run.target.getMinBlockZ(); z <= run.target.getMaxBlockZ(); z++) {
                for (int x = run.target.getMinBlockX(); x <= run.target.getMaxBlockX(); x++) {
                    for (int y = run.level.getMinY(); y <= run.level.getMaxY(); y++) {
                        float density = beardifier.sampleValue(sampler, x, y, z);
                        output.writeInt(Float.floatToRawIntBits(density));
                        samples++;
                        if (density != 0) nonzero++;
                    }
                }
            }
            Files.createDirectories(run.output);
            if (samples != 16 * 16 * run.level.getHeight())
                throw new IllegalStateException("Native Beardifier height coverage incomplete");
            byte[] payload = bytes.toByteArray();
            Files.write(run.output.resolve("beardifier.bin"), payload);
            JsonObject summary = new JsonObject();
            summary.addProperty("coverageVersion", 2);
            summary.addProperty("sampleBytes", 4);
            summary.addProperty("minY", run.level.getMinY());
            summary.addProperty("maxYInclusive", run.level.getMaxY());
            summary.addProperty("height", run.level.getHeight());
            summary.addProperty("samples", samples);
            summary.addProperty("nonzero", nonzero);
            summary.addProperty("sha256", sha256(payload));
            write(run.output.resolve("beardifier.json"), summary);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static void compare(Path nativeRoot, Run run, JsonObject result) {
        JsonObject producer = read(nativeRoot.resolve("summary.json"));
        for (String field :
                List.of(
                        "schemaVersion",
                        "fixture",
                        "masterSeed",
                        "localSeed",
                        "dimension",
                        "chunkX",
                        "chunkZ",
                        "environment")) {
            if (!canonicalJson(producer.get(field))
                    .toString()
                    .equals(canonicalJson(result.get(field)).toString()))
                throw new IllegalStateException("Oracle manifest mismatch: " + field);
        }
        if (!producer.get("status").getAsString().equals("PRODUCED")
                || producer.get("pid").equals(result.get("pid"))
                || producer.get("save").equals(result.get("save")))
            throw new IllegalStateException(
                    "Control evidence is not an independent completed producer");
        JsonArray differences = new JsonArray();
        if (Boolean.getBoolean(PREFIX + "biomeOnly")
                || Boolean.getBoolean(PREFIX + "nativeTerrain")) {
            String nativeName =
                    Boolean.getBoolean(PREFIX + "biomeOnly")
                            ? "biomes-native.nbt.gz"
                            : "terrain-native.nbt.gz";
            try {
                var before =
                        net.minecraft.nbt.NbtIo.readCompressed(
                                nativeRoot.resolve(nativeName),
                                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                var after =
                        net.minecraft.nbt.NbtIo.readCompressed(
                                run.output.resolve(nativeName),
                                net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                if (!before.equals(after)) {
                    var difference = new JsonObject();
                    difference.addProperty("stage", "full-native-biomes-nbt");
                    differences.add(difference);
                }
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
        if (Boolean.getBoolean(PREFIX + "lightweight"))
            TerrainProbeEvidence.compare(nativeRoot, run.output, differences);
        if (Boolean.getBoolean(PREFIX + "structures")) {
            try {
                byte[] left = Files.readAllBytes(nativeRoot.resolve("beardifier.bin"));
                byte[] right = Files.readAllBytes(run.output.resolve("beardifier.bin"));
                int first = java.util.Arrays.mismatch(left, right);
                if (first != -1) {
                    JsonObject diff = new JsonObject();
                    diff.addProperty("stage", "beardifier");
                    diff.addProperty("firstSample", first / Integer.BYTES);
                    differences.add(diff);
                }
                if (!Files.readString(nativeRoot.resolve("projection.json"))
                        .equals(Files.readString(run.output.resolve("projection.json")))) {
                    JsonObject diff = new JsonObject();
                    diff.addProperty("stage", "persisted-projection");
                    differences.add(diff);
                }
            } catch (IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
        }
        for (ChunkStatus stage : expectedStages()) {
            String name = stageName(stage);
            var diff =
                    stage.isBefore(ChunkStatus.BIOMES)
                            ? new FeatureStageSnapshot.Diff(true, 0, null, Map.of())
                            : FeatureStageSnapshot.read(nativeRoot.resolve(name + ".bin.gz"))
                                    .diff(
                                            FeatureStageSnapshot.read(
                                                    run.output.resolve(name + ".bin.gz")));
            JsonObject left = read(nativeRoot.resolve(name + "-structures.json"));
            JsonObject right = read(run.output.resolve(name + "-structures.json"));
            if (!diff.equivalent() || !left.equals(right)) {
                JsonObject difference = new JsonObject();
                difference.addProperty("stage", name);
                difference.addProperty("differingBlocks", diff.differingBlocks());
                difference.addProperty("firstDifference", diff.firstDifference());
                difference.add(
                        "blockCategories",
                        new GsonBuilder().create().toJsonTree(diff.blockCategories()));
                for (String key : unionKeys(left, right)) {
                    if (!Objects.equals(left.get(key), right.get(key))) {
                        difference.addProperty("firstStructure", key);
                        difference.add("nativeStructure", left.get(key));
                        difference.add("referenceStructure", right.get(key));
                        JsonDifference field = firstJsonDifference(left.get(key), right.get(key));
                        difference.addProperty("firstStructureField", field.path());
                        difference.add("nativeField", field.left());
                        difference.add("referenceField", field.right());
                        break;
                    }
                }
                differences.add(difference);
            }
        }
        result.add("differences", differences);
        result.addProperty("status", differences.isEmpty() ? "PASS" : "DIVERGED");
    }

    static JsonObject environment(ServerLevel level) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        JsonObject environment = new JsonObject();
        environment.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
        environment.addProperty("inputSha256", required("inputHash"));
        environment.addProperty(
                "generateStructures",
                level.getServer().getWorldGenSettings().options().generateStructures());
        environment.add(
                "generator",
                ChunkGenerator.CODEC
                        .encodeStart(ops, level.getChunkSource().getGenerator())
                        .getOrThrow());
        environment.add(
                "dimensionType",
                DimensionType.DIRECT_CODEC.encodeStart(ops, level.dimensionType()).getOrThrow());
        JsonObject registries = new JsonObject();
        for (var definition : RegistryDataLoader.WORLD_REGISTRIES) {
            registries.addProperty(
                    definition.key().identifier().toString(),
                    sha256(canonicalJson(registryEvidence(definition, level, ops)).toString()));
        }
        environment.add("registryHashes", registries);
        JsonObject mods = new JsonObject();
        FabricLoader.getInstance()
                .getAllMods()
                .forEach(
                        mod ->
                                mods.addProperty(
                                        mod.getMetadata().getId(),
                                        mod.getMetadata().getVersion().getFriendlyString()));
        environment.add("mods", mods);
        return environment;
    }

    private static <T> JsonObject registryEvidence(
            RegistryDataLoader.RegistryData<T> definition,
            ServerLevel level,
            RegistryOps<JsonElement> ops) {
        var registry = level.registryAccess().lookupOrThrow(definition.key());
        JsonObject entries = new JsonObject();
        registry.entrySet()
                .forEach(
                        entry ->
                                entries.add(
                                        entry.getKey().identifier().toString(),
                                        definition
                                                .elementCodec()
                                                .encodeStart(ops, entry.getValue())
                                                .getOrThrow()));
        JsonObject tags = new JsonObject();
        registry.getTags()
                .forEach(
                        tag -> {
                            JsonArray values = new JsonArray();
                            tag.stream()
                                    .map(
                                            holder ->
                                                    holder.unwrapKey()
                                                            .orElseThrow()
                                                            .identifier()
                                                            .toString())
                                    .sorted()
                                    .forEach(values::add);
                            tags.add(tag.key().location().toString(), values);
                        });
        entries.add("#tags", tags);
        return entries;
    }

    private static void exportPlan(MinecraftServer server, Path root) {
        JsonArray plan = new JsonArray();
        var fixtures = Phase2C1Verification.matrixFixtures();
        var resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        for (int index = 0; index < fixtures.size(); index++) {
            var fixture = fixtures.get(index);
            JsonObject value = new JsonObject();
            value.addProperty("id", String.format(java.util.Locale.ROOT, "fixture-%02d", index));
            value.addProperty("master", Long.toString(fixture.masterSeed()));
            value.addProperty(
                    "local",
                    Long.toString(
                            resolver.resolveLocalWorldSeed(
                                    fixture.masterSeed(), fixture.dimension(), fixture.target())));
            value.addProperty("dimension", fixture.dimension().identifier().toString());
            value.addProperty("x", fixture.target().x());
            value.addProperty("z", fixture.target().z());
            plan.add(value);
        }
        write(
                root.resolve("fixtures.json"),
                BopContextVerifier.enabled() ? BopContextVerifier.fixtures(server, plan) : plan);
    }

    private static void exportLauncher(Path root) {
        JsonObject launch = new JsonObject();
        launch.addProperty("java", ProcessHandle.current().info().command().orElseThrow());
        launch.addProperty("directory", System.getProperty("user.dir"));
        launch.addProperty("classpath", System.getProperty("java.class.path"));
        JsonArray arguments = new JsonArray();
        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> !argument.startsWith("-D" + PREFIX))
                .forEach(arguments::add);
        launch.add("jvmArguments", arguments);
        String[] command = System.getProperty("sun.java.command").split(" ");
        launch.addProperty("main", command[0]);
        JsonArray gameArguments = new JsonArray();
        for (int index = 1; index < command.length; index++) gameArguments.add(command[index]);
        launch.add("gameArguments", gameArguments);
        write(root.resolve("launcher.json"), launch);
    }

    public record JsonDifference(String path, JsonElement left, JsonElement right) {}

    public static JsonDifference firstJsonDifference(JsonElement left, JsonElement right) {
        return firstJsonDifference("$", left, right);
    }

    private static JsonDifference firstJsonDifference(
            String path, JsonElement left, JsonElement right) {
        if (Objects.equals(left, right)) return null;
        if (left == null || right == null) return new JsonDifference(path, left, right);
        if (left.isJsonObject() && right.isJsonObject()) {
            for (String key : unionKeys(left.getAsJsonObject(), right.getAsJsonObject())) {
                JsonDifference difference =
                        firstJsonDifference(
                                path + "." + key,
                                left.getAsJsonObject().get(key),
                                right.getAsJsonObject().get(key));
                if (difference != null) return difference;
            }
        } else if (left.isJsonArray() && right.isJsonArray()) {
            var a = left.getAsJsonArray();
            var b = right.getAsJsonArray();
            for (int index = 0; index < Math.max(a.size(), b.size()); index++) {
                JsonDifference difference =
                        firstJsonDifference(
                                path + "[" + index + "]",
                                index < a.size() ? a.get(index) : null,
                                index < b.size() ? b.get(index) : null);
                if (difference != null) return difference;
            }
        } else if (left.isJsonPrimitive()
                && right.isJsonPrimitive()
                && left.getAsJsonPrimitive().isString()
                && right.getAsJsonPrimitive().isString()) {
            String a = left.getAsString();
            String b = right.getAsString();
            if ((a.startsWith("{") && b.startsWith("{"))
                    || (a.startsWith("[") && b.startsWith("[")))
                return firstJsonDifference(
                        path, JsonParser.parseString(a), JsonParser.parseString(b));
        }
        return new JsonDifference(path, left, right);
    }

    public static JsonElement canonicalJson(JsonElement input) {
        if (input.isJsonObject()) {
            JsonObject output = new JsonObject();
            for (String key : new TreeSet<>(input.getAsJsonObject().keySet()))
                output.add(key, canonicalJson(input.getAsJsonObject().get(key)));
            return output;
        }
        if (input.isJsonArray()) {
            JsonArray output = new JsonArray();
            for (JsonElement value : input.getAsJsonArray()) output.add(canonicalJson(value));
            return output;
        }
        return input.deepCopy();
    }

    public static String canonicalNbt(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            JsonObject output = new JsonObject();
            for (String key : new TreeSet<>(compound.keySet()))
                output.addProperty(key, canonicalNbt(Objects.requireNonNull(compound.get(key))));
            return output.toString();
        }
        if (tag instanceof ListTag list) {
            JsonArray output = new JsonArray();
            for (Tag value : list) output.add(canonicalNbt(value));
            return output.toString();
        }
        return tag.getId() + ":" + tag;
    }

    private static TreeSet<String> unionKeys(JsonObject left, JsonObject right) {
        TreeSet<String> keys = new TreeSet<>(left.keySet());
        keys.addAll(right.keySet());
        return keys;
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String required(String key) {
        return Objects.requireNonNull(
                System.getProperty(PREFIX + key), "Missing pre-FEATURES property " + key);
    }

    private static String stageName(ChunkStatus status) {
        return status.getName().substring(status.getName().indexOf(':') + 1);
    }

    private static JsonObject read(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read " + path, failure);
        }
    }

    static void write(Path path, JsonElement value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(
                    path,
                    new GsonBuilder().setPrettyPrinting().create().toJson(canonicalJson(value))
                            + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot write " + path, failure);
        }
    }

    private static final class Run {
        final String role;
        CompactStructureInputs compact;
        ChunkAccess detachedTerrain;
        final ServerLevel level;
        final ChunkPos target;
        final Path output;
        final java.util.ArrayList<ChunkStatus> completed = new java.util.ArrayList<>();
        final JsonArray checkpoints = new JsonArray();

        Run(String role, ServerLevel level, ChunkPos target, Path output) {
            this.role = role;
            this.level = level;
            this.target = target;
            this.output = output;
        }
    }
}
