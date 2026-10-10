package io.github.recrivenvi.randomnibble6plus24generator.research;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Runs in a real, ordinary server, with no players or physical generation requests. */
public final class LightweightArchitectureProbe {
    private static final ChunkStatus[] STAGES = {
        ChunkStatus.BIOMES, ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS
    };
    private static final MosaicSeedResolver RESOLVER =
            new MosaicSeedResolver(MosaicWorldProfile.current());
    private static final com.sun.management.ThreadMXBean ALLOC =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final JsonArray RESULTS = new JsonArray();

    record Fixture(
            long master,
            net.minecraft.resources.ResourceKey<Level> dimension,
            ChunkPos pos,
            String structure) {}

    record Captures(
            List<CarverStageSnapshot> stages,
            Map<String, CompoundTag> structures,
            Map<String, String> biomeHalo,
            JsonObject metrics) {}

    public static void run(MinecraftServer server) {
        Path output =
                Path.of(System.getProperty("mosaic.research.output", "lightweight-results.json"));
        long started = System.nanoTime();
        JsonObject report = new JsonObject();
        report.add("fixtures", RESULTS);
        try {
            List<Fixture> fixtures = matrix();
            List<Fixture> structures =
                    List.of(
                            new Fixture(
                                    123456789L,
                                    Level.OVERWORLD,
                                    new ChunkPos(-10, 11),
                                    "minecraft:village_desert"),
                            new Fixture(
                                    123456789L,
                                    Level.OVERWORLD,
                                    new ChunkPos(-4, -2),
                                    "minecraft:mineshaft"),
                            new Fixture(
                                    123456789L,
                                    Level.OVERWORLD,
                                    new ChunkPos(-13, -14),
                                    "minecraft:trial_chambers"),
                            new Fixture(
                                    123456789L,
                                    Level.OVERWORLD,
                                    new ChunkPos(52, -247),
                                    "minecraft:monument"),
                            new Fixture(
                                    123456789L,
                                    Level.NETHER,
                                    new ChunkPos(-11, -6),
                                    "minecraft:fortress"),
                            new Fixture(
                                    123456789L,
                                    Level.END,
                                    new ChunkPos(1004, 1006),
                                    "minecraft:end_city"));
            if (Boolean.getBoolean("mosaic.research.smoke"))
                fixtures =
                        List.of(
                                new Fixture(123456789L, Level.OVERWORLD, ChunkPos.ZERO, ""),
                                new Fixture(
                                        123456789L, Level.OVERWORLD, new ChunkPos(125, -37), ""),
                                new Fixture(-1L, Level.NETHER, new ChunkPos(1, -1), ""),
                                new Fixture(Long.MAX_VALUE, Level.END, new ChunkPos(-125, 37), ""));
            // Both implementations are warmed, while results remain excluded from the measured
            // matrix.
            if (!Boolean.getBoolean("mosaic.research.featuresOnly")) {
                for (Fixture f : List.of(fixtures.getFirst())) compare(server, f, false, false);
                int index = 0;
                for (Fixture fixture : fixtures) {
                    compare(server, fixture, true, index++ % 2 == 0);
                    write(output, report);
                }
                for (Fixture fixture : structures) {
                    compare(server, fixture, true, index++ % 2 == 0);
                    write(output, report);
                }
            }
            if (!Boolean.getBoolean("mosaic.research.skipFeatures")) {
                JsonArray features = new JsonArray();
                report.add("targetOnlyFeatures", features);
                List<Fixture> featureFixtures = new ArrayList<>(structures);
                featureFixtures.add(
                        new Fixture(123456789L, Level.OVERWORLD, new ChunkPos(125, -37), ""));
                featureFixtures.add(new Fixture(123456789L, Level.OVERWORLD, ChunkPos.ZERO, ""));
                featureFixtures.add(new Fixture(0L, Level.END, new ChunkPos(-1, -3), ""));
                featureFixtures.add(
                        new Fixture(123456789L, Level.OVERWORLD, new ChunkPos(-8532, -4457), ""));
                String filter = System.getProperty("mosaic.research.structure", "");
                for (Fixture f : featureFixtures)
                    if (filter.isEmpty() || f.structure.equals(filter)) {
                        features.add(featureProbe(server, f));
                        write(output, report);
                    }
                if (filter.isEmpty()) {
                    List<Fixture> parallelFixtures =
                            List.of(
                                    featureFixtures.get(7),
                                    featureFixtures.get(6),
                                    featureFixtures.get(1),
                                    featureFixtures.get(4),
                                    featureFixtures.get(8));
                    report.add("parallelTargetSessions", parallelProbe(server, parallelFixtures));
                    JsonArray readModels = new JsonArray();
                    readModels.add(readModelProbe(server, featureFixtures.get(6)));
                    readModels.add(readModelProbe(server, featureFixtures.get(0)));
                    report.add("featureReadModels", readModels);
                }
            }
            boolean limited =
                    report.has("targetOnlyFeatures")
                            && report.getAsJsonArray("targetOnlyFeatures").asList().stream()
                                    .anyMatch(
                                            v ->
                                                    !v.getAsJsonObject()
                                                            .get("status")
                                                            .getAsString()
                                                            .equals("PASS"));
            report.addProperty("status", limited ? "FEASIBLE_WITH_LIMITATIONS" : "PASS");
            report.addProperty("preFeaturesStatus", RESULTS.isEmpty() ? "NOT_RUN" : "PASS");
            report.addProperty("contextBindings", GenerationContextRegistry.bindingCount());
            if (GenerationContextRegistry.bindingCount() != 0)
                throw new IllegalStateException("Leaked context");
            RandomNibble6Plus24Generator.LOGGER.info(
                    "LIGHTWEIGHT ARCHITECTURE PROBE {} fixtures={}",
                    report.get("status"),
                    RESULTS.size());
        } catch (Throwable e) {
            report.addProperty("status", "FAIL");
            report.addProperty("failure", e.toString());
            RandomNibble6Plus24Generator.LOGGER.error("LIGHTWEIGHT ARCHITECTURE PROBE FAIL", e);
        } finally {
            report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1e9);
            report.addProperty(
                    "heapUsedBytes",
                    ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            report.addProperty(
                    "sumHeapPoolPeaksBytesNotSimultaneous",
                    ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(p -> p.getType() == java.lang.management.MemoryType.HEAP)
                            .mapToLong(p -> p.getPeakUsage().getUsed())
                            .sum());
            write(output, report);
            server.execute(() -> server.halt(false));
        }
    }

    private static void compare(
            MinecraftServer server, Fixture f, boolean record, boolean prototypeFirst) {
        ServerLevel host = Objects.requireNonNull(server.getLevel(f.dimension));
        long seed = RESOLVER.resolveLocalWorldSeed(f.master, f.dimension, f.pos);
        Captures ref, candidate;
        if (prototypeFirst) {
            candidate = prototype(host, seed, f.pos);
            ref = reference(host, seed, f.pos);
        } else {
            ref = reference(host, seed, f.pos);
            candidate = prototype(host, seed, f.pos);
        }
        for (int i = 0; i < STAGES.length; i++) {
            try {
                candidate.stages.get(i).assertEquivalentTo(ref.stages.get(i));
            } catch (RuntimeException e) {
                throw new IllegalStateException(f + " stage=" + STAGES[i], e);
            }
        }
        if (!candidate.structures.equals(ref.structures)) {
            TreeSet<String> keys = new TreeSet<>(ref.structures.keySet());
            keys.addAll(candidate.structures.keySet());
            for (String key : keys)
                if (!Objects.equals(candidate.structures.get(key), ref.structures.get(key)))
                    throw new IllegalStateException(
                            "Full structure NBT mismatch "
                                    + f
                                    + " "
                                    + key
                                    + " reference="
                                    + ref.structures.get(key)
                                    + " prototype="
                                    + candidate.structures.get(key));
        }
        if (!candidate.biomeHalo.equals(ref.biomeHalo))
            throw new IllegalStateException("Stored biome halo/palette mismatch " + f);
        if (!f.structure.isEmpty()
                && ref.structures.keySet().stream().noneMatch(s -> s.contains(f.structure)))
            throw new IllegalStateException("Required actual structure absent " + f);
        if (!f.structure.isEmpty()
                && !candidate.metrics.get("targetProjections").toString().contains(f.structure))
            throw new IllegalStateException("Required structure does not project into target " + f);
        if (f.master == 123456789L
                && f.dimension == Level.OVERWORLD
                && f.pos.equals(ChunkPos.ZERO)) {
            if (!ref.stages
                    .get(2)
                    .base()
                    .hash()
                    .equals("cf8a46342321d8ec0baa089631debd3c38572b3dd8968fbbb66c76dd2e7d5b3f"))
                throw new IllegalStateException("SURFACE golden");
            if (!ref.stages
                    .get(3)
                    .hash()
                    .equals("8f0eb2a9d708ade9fba2dbfe3e21f1a6d3aa318d7c4052db5ea134bd42f0fa0a"))
                throw new IllegalStateException("CARVERS golden");
        }
        JsonObject row = fixtureJson(f, seed);
        row.add("reference", ref.metrics);
        row.add("prototype", candidate.metrics);
        JsonObject hashes = new JsonObject();
        for (int i = 0; i < STAGES.length; i++)
            hashes.addProperty(STAGES[i].toString(), ref.stages.get(i).hash());
        row.add("hashes", hashes);
        row.addProperty("structureNbtEntries", ref.structures.size());
        row.addProperty("exactStoredBiomeHaloChunks", ref.biomeHalo.size());
        row.addProperty(
                "externalStructureStarts",
                ref.structures.keySet().stream().filter(k -> !k.startsWith(f.pos + "/")).count());
        row.addProperty("status", "PASS");
        if (record) RESULTS.add(row);
        RandomNibble6Plus24Generator.LOGGER.info(
                "LIGHTWEIGHT parity PASS {} seed={} refMs={} prototypeMs={}",
                f,
                seed,
                ref.metrics.get("wallMs"),
                candidate.metrics.get("wallMs"));
    }

    private static Captures reference(ServerLevel host, long seed, ChunkPos pos) {
        Meter meter = new Meter();
        List<CarverStageSnapshot> snapshots = new ArrayList<>();
        try (IsolatedGenerationContext ctx =
                meter.time(
                        () ->
                                IsolatedGenerationContext.create(
                                        IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, pos))) {
            ProtoChunk target = null;
            for (ChunkStatus stage : STAGES) {
                Object result =
                        meter.time(
                                () ->
                                        LightweightUniverse.invoke(
                                                IsolatedGenerationContext.class,
                                                ctx,
                                                "generateTo",
                                                new Class<?>[] {ChunkStatus.class},
                                                stage));
                target =
                        LightweightUniverse.invoke(
                                result.getClass(), result, "targetChunk", new Class<?>[] {});
                snapshots.add(CarverStageSnapshot.capture(target, host.registryAccess()));
            }
            Object map = field(ctx, "chunkMap");
            Map<?, ?> holders = (Map<?, ?>) field(map, "holders");
            int futureCount = 0;
            for (Object holder : holders.values()) {
                AtomicReferenceArray<?> futures =
                        (AtomicReferenceArray<?>)
                                field(GenerationChunkHolder.class, holder, "futures");
                for (int i = 0; i < futures.length(); i++)
                    if (futures.get(i) != null) futureCount++;
            }
            JsonObject metrics = meter.json();
            metrics.addProperty("protoChunks", holders.size());
            metrics.addProperty("holders", holders.size());
            metrics.addProperty("populatedStatusFutures", futureCount);
            metrics.addProperty("driverTasks", STAGES.length);
            Map<String, CompoundTag> structureData = new TreeMap<>();
            for (int x = pos.x() - 9; x <= pos.x() + 9; x++)
                for (int z = pos.z() - 9; z <= pos.z() + 9; z++) {
                    ChunkAccess chunk =
                            LightweightUniverse.invoke(
                                    map.getClass(),
                                    map,
                                    "chunkAt",
                                    new Class<?>[] {ChunkPos.class, ChunkStatus.class},
                                    new ChunkPos(x, z),
                                    ChunkStatus.STRUCTURE_STARTS);
                    serializeStarts(host, chunk.getPos(), chunk.getAllStarts(), structureData);
                }
            Map<String, String> halo = new TreeMap<>();
            for (int x = pos.x() - 1; x <= pos.x() + 1; x++)
                for (int z = pos.z() - 1; z <= pos.z() + 1; z++) {
                    ChunkPos p = new ChunkPos(x, z);
                    ChunkAccess chunk =
                            LightweightUniverse.invoke(
                                    map.getClass(),
                                    map,
                                    "chunkAt",
                                    new Class<?>[] {ChunkPos.class, ChunkStatus.class},
                                    p,
                                    ChunkStatus.BIOMES);
                    List<
                                    net.minecraft.world.level.chunk.PalettedContainerRO<
                                            Holder<net.minecraft.world.level.biome.Biome>>>
                            palettes = new ArrayList<>();
                    for (var section : chunk.getSections()) palettes.add(section.getBiomes());
                    halo.put(p.toString(), biomeSignature(p, host, palettes));
                }
            return new Captures(snapshots, structureData, halo, metrics);
        }
    }

    private static Captures prototype(ServerLevel host, long seed, ChunkPos pos) {
        Meter meter = new Meter();
        List<CarverStageSnapshot> snapshots = new ArrayList<>();
        try (LightweightUniverse u = meter.time(() -> new LightweightUniverse(host, seed, pos))) {
            ProtoChunk target = meter.time(() -> u.begin(pos));
            meter.time(
                    () -> {
                        u.biomes(target);
                        return null;
                    });
            snapshots.add(CarverStageSnapshot.capture(target, host.registryAccess()));
            meter.time(
                    () -> {
                        u.noise(target);
                        return null;
                    });
            snapshots.add(CarverStageSnapshot.capture(target, host.registryAccess()));
            meter.time(
                    () -> {
                        u.surface(target);
                        return null;
                    });
            snapshots.add(CarverStageSnapshot.capture(target, host.registryAccess()));
            meter.time(
                    () -> {
                        u.carvers(target);
                        return null;
                    });
            snapshots.add(CarverStageSnapshot.capture(target, host.registryAccess()));
            JsonObject metrics = meter.json();
            metrics.addProperty("protoChunks", u.terrain.size());
            metrics.addProperty("holders", 0);
            metrics.addProperty("metadataCarriers", 3);
            metrics.addProperty("biomeOnlyChunks", u.biomes.size());
            metrics.addProperty("structureSourceEvaluations", u.nativeStructureEvaluations);
            metrics.addProperty("carverSourceQueries", u.carverSourceQueries);
            metrics.addProperty("nativeBiomeNoiseFutures", 2);
            metrics.addProperty("driverTasks", 0);
            JsonArray projections = new JsonArray();
            var box =
                    new BoundingBox(
                            pos.getMinBlockX(),
                            host.getMinY(),
                            pos.getMinBlockZ(),
                            pos.getMaxBlockX(),
                            host.getMaxY() - 1,
                            pos.getMaxBlockZ());
            for (StructureStart start : u.structures.startsForStructure(pos, structure -> true)) {
                long intersections =
                        start.getPieces().stream()
                                .filter(p -> p.getBoundingBox().intersects(box))
                                .count();
                JsonObject projection = new JsonObject();
                projection.addProperty(
                        "id",
                        host.registryAccess()
                                .lookupOrThrow(Registries.STRUCTURE)
                                .getKey(start.getStructure())
                                .toString());
                projection.addProperty("start", start.getChunkPos().toString());
                projection.addProperty("pieces", start.getPieces().size());
                projection.addProperty("intersectingPieces", intersections);
                projection.addProperty(
                        "terrainAdaptation", start.getStructure().terrainAdaptation().toString());
                projection.addProperty("external", !pos.equals(start.getChunkPos()));
                if (intersections > 0) projections.add(projection);
            }
            metrics.add("targetProjections", projections);
            Map<String, CompoundTag> structureData = new TreeMap<>();
            for (int x = pos.x() - 9; x <= pos.x() + 9; x++)
                for (int z = pos.z() - 9; z <= pos.z() + 9; z++) {
                    ChunkPos source = new ChunkPos(x, z);
                    serializeStarts(host, source, u.startsAt(source), structureData);
                }
            Map<String, String> halo = new TreeMap<>();
            for (int x = pos.x() - 1; x <= pos.x() + 1; x++)
                for (int z = pos.z() - 1; z <= pos.z() + 1; z++) {
                    ChunkPos p = new ChunkPos(x, z);
                    List<
                                    net.minecraft.world.level.chunk.PalettedContainerRO<
                                            Holder<net.minecraft.world.level.biome.Biome>>>
                            palettes = new ArrayList<>();
                    if (p.equals(pos))
                        for (var section : target.getSections()) palettes.add(section.getBiomes());
                    else palettes.addAll(u.biomes.get(p));
                    halo.put(p.toString(), biomeSignature(p, host, palettes));
                }
            return new Captures(snapshots, structureData, halo, metrics);
        }
    }

    private static JsonObject featureProbe(MinecraftServer server, Fixture f) {
        ServerLevel host = Objects.requireNonNull(server.getLevel(f.dimension));
        long seed = RESOLVER.resolveLocalWorldSeed(f.master, f.dimension, f.pos);
        String hash = null;
        FeatureStableSnapshot first = null;
        JsonObject row = fixtureJson(f, seed);
        FeatureStableSnapshot oldSnapshot;
        Meter referenceMeter = new Meter();
        try (IsolatedGenerationContext ctx =
                referenceMeter.time(
                        () ->
                                IsolatedGenerationContext.create(
                                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                                        host,
                                        seed,
                                        f.pos))) {
            var run = referenceMeter.time(ctx::generateFeaturesStable);
            var snapshot =
                    FeatureStableSnapshot.capture(
                            f.dimension.identifier().toString(),
                            run.targetChunk(),
                            host.registryAccess());
            oldSnapshot = snapshot;
            row.addProperty("oldNineWriterHash", snapshot.hash());
            row.add("oldNineWriterMetrics", referenceMeter.json());
            row.addProperty("oldNineWriterProtoChunks", run.metrics().virtualChunkCount());
            if (f.master == 123456789L && f.dimension == Level.OVERWORLD) {
                if (f.pos.equals(ChunkPos.ZERO)
                        && !snapshot.hash().equals(Phase2C1Verification.FEATURES_ORIGIN_GOLDEN))
                    throw new IllegalStateException("Old FEATURES origin golden changed");
                if (f.pos.equals(new ChunkPos(125, -37))
                        && !snapshot.hash().equals(Phase2C1Verification.FEATURES_NON_ORIGIN_GOLDEN))
                    throw new IllegalStateException("Old FEATURES non-origin golden changed");
            }
        }
        if (f.structure.equals("minecraft:end_city")) {
            try (IsolatedGenerationContext ctx =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, f.pos)) {
                var run = ctx.generateFeaturesStable();
                var repeated =
                        FeatureStableSnapshot.capture(
                                f.dimension.identifier().toString(),
                                run.targetChunk(),
                                host.registryAccess());
                row.addProperty("oldNineWriterRepeatHash", repeated.hash());
                row.addProperty(
                        "oldNineWriterRepeatable",
                        repeated.hash().equals(row.get("oldNineWriterHash").getAsString()));
                row.addProperty(
                        "oldNineWriterRepeatEntities", repeated.canonicalEntityNbt().toString());
                row.addProperty(
                        "oldNineWriterFirstEntities", oldSnapshot.canonicalEntityNbt().toString());
                row.addProperty("oldNineWriterRepeatDiff", oldSnapshot.diff(repeated).toString());
            }
        }
        boolean reproducible = true;
        for (int repeat = 0; repeat < 2; repeat++) {
            int loaded = host.getChunkSource().getLoadedChunksCount();
            int blockTicks = host.getBlockTicks().count(),
                    fluidTicks = host.getFluidTicks().count();
            Meter meter = new Meter();
            try (LightweightUniverse u =
                    meter.time(() -> new LightweightUniverse(host, seed, f.pos))) {
                ProtoChunk target = meter.time(() -> u.preFeatures(f.pos));
                List<ChunkPos> neighbors = new ArrayList<>();
                for (int z = f.pos.z() - 1; z <= f.pos.z() + 1; z++)
                    for (int x = f.pos.x() - 1; x <= f.pos.x() + 1; x++)
                        if (!f.pos.equals(new ChunkPos(x, z))) neighbors.add(new ChunkPos(x, z));
                if (repeat != 0) Collections.reverse(neighbors);
                Map<ChunkPos, FeatureStableSnapshot> before = new HashMap<>();
                for (ChunkPos p : neighbors) {
                    ProtoChunk n = meter.time(() -> u.preFeatures(p));
                    before.put(
                            p,
                            FeatureStableSnapshot.capture(
                                    f.dimension.identifier().toString(), n, host.registryAccess()));
                }
                meter.time(
                        () -> {
                            u.targetFeatures(target);
                            return null;
                        });
                FeatureStableSnapshot after =
                        FeatureStableSnapshot.capture(
                                f.dimension.identifier().toString(), target, host.registryAccess());
                for (ChunkPos p : neighbors) {
                    var n =
                            FeatureStableSnapshot.capture(
                                    f.dimension.identifier().toString(),
                                    u.terrain.get(p),
                                    host.registryAccess());
                    var diff = before.get(p).diff(n);
                    if (!diff.equivalent())
                        throw new IllegalStateException(
                                "Target-only feature mutated neighbor " + p + " " + diff);
                }
                if (hash != null && !hash.equals(after.hash())) {
                    JsonObject failure = fixtureJson(f, seed);
                    failure.addProperty("diff", first.diff(after).toString());
                    failure.addProperty(
                            "metadataDifference", first.deterministicMetadataDifference(after));
                    failure.addProperty("entitiesA", first.canonicalEntityNbt().toString());
                    failure.addProperty("entitiesB", after.canonicalEntityNbt().toString());
                    failure.addProperty("rawEntitiesA", first.rawEntityNbt().toString());
                    failure.addProperty("rawEntitiesB", after.rawEntityNbt().toString());
                    write(
                            Path.of(
                                    System.getProperty("mosaic.research.output")
                                            + ".difference.json"),
                            failure);
                    if (!f.structure.equals("minecraft:end_city"))
                        throw new IllegalStateException(
                                "Target-only repeat/order mismatch " + f + " " + first.diff(after));
                    reproducible = false;
                    row.add("repeatabilityBlocker", failure);
                }
                first = after;
                hash = after.hash();
                if (u.featuresInvoked != 1
                        || loaded != host.getChunkSource().getLoadedChunksCount()
                        || blockTicks != host.getBlockTicks().count()
                        || fluidTicks != host.getFluidTicks().count())
                    throw new IllegalStateException("FEATURES side effect or invocation mismatch");
                row.addProperty("hash", hash);
                row.addProperty("blockEntities", after.blockEntityCount());
                row.addProperty("pendingBlockEntities", after.pendingBlockEntityNbtCount());
                row.addProperty("blockTicks", after.blockTickCount());
                row.addProperty("fluidTicks", after.fluidTickCount());
                row.addProperty("postProcessing", after.postProcessingCount());
                row.addProperty("entities", after.entityCount());
                row.addProperty("discardedBlocks", u.discardedBlocks);
                row.addProperty("discardedEntities", u.discardedEntities);
                row.addProperty("discardedBlockTicks", u.discardedBlockTicks);
                row.addProperty("discardedFluidTicks", u.discardedFluidTicks);
                row.addProperty("discardedPostProcessing", u.discardedPostProcessing);
                row.addProperty("protoChunks", u.terrain.size());
                row.addProperty("structureSources", u.starts.size());
                row.add("prototypeMetrics" + repeat, meter.json());
            }
        }
        row.addProperty("status", reproducible ? "PASS" : "BLOCKED_ENTITY_NONDETERMINISM");
        RandomNibble6Plus24Generator.LOGGER.info(
                "TARGET-ONLY FEATURES {} {}", row.get("status"), f);
        return row;
    }

    private static void serializeStarts(
            ServerLevel host,
            ChunkPos pos,
            Map<Structure, StructureStart> starts,
            Map<String, CompoundTag> result) {
        var serialization = StructurePieceSerializationContext.fromLevel(host);
        starts.forEach(
                (structure, start) ->
                        result.put(
                                pos
                                        + "/"
                                        + host.registryAccess()
                                                .lookupOrThrow(Registries.STRUCTURE)
                                                .getKey(structure),
                                start.createTag(serialization, pos)));
    }

    private static String biomeSignature(
            ChunkPos pos,
            ServerLevel host,
            List<PalettedContainerRO<Holder<net.minecraft.world.level.biome.Biome>>> palettes) {
        StringBuilder data = new StringBuilder();
        for (var palette : palettes) {
            TreeSet<String> entries = new TreeSet<>();
            palette.getAll(b -> entries.add(b.unwrapKey().orElseThrow().identifier().toString()));
            data.append(entries);
            for (int x = 0; x < 4; x++)
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 4; z++)
                        data.append(palette.get(x, y, z).unwrapKey().orElseThrow().identifier())
                                .append(';');
        }
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            data.toString()
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FeatureStableSnapshot singleTarget(
            ServerLevel host, Fixture fixture, boolean mosaicReadModel, boolean reverse) {
        long seed = RESOLVER.resolveLocalWorldSeed(fixture.master, fixture.dimension, fixture.pos);
        try (LightweightUniverse u = new LightweightUniverse(host, seed, fixture.pos)) {
            ProtoChunk target = u.preFeatures(fixture.pos);
            List<ChunkPos> neighbors = new ArrayList<>();
            for (int z = fixture.pos.z() - 1; z <= fixture.pos.z() + 1; z++)
                for (int x = fixture.pos.x() - 1; x <= fixture.pos.x() + 1; x++)
                    if (!fixture.pos.equals(new ChunkPos(x, z))) neighbors.add(new ChunkPos(x, z));
            if (reverse) Collections.reverse(neighbors);
            for (ChunkPos p : neighbors) {
                if (!mosaicReadModel) u.preFeatures(p);
                else {
                    long neighborSeed =
                            RESOLVER.resolveLocalWorldSeed(fixture.master, fixture.dimension, p);
                    try (LightweightUniverse neighbor =
                            new LightweightUniverse(host, neighborSeed, p)) {
                        u.terrain.put(p, neighbor.preFeatures(p));
                    }
                }
            }
            u.targetFeatures(target);
            return FeatureStableSnapshot.capture(
                    fixture.dimension.identifier().toString(), target, host.registryAccess());
        }
    }

    private static JsonObject parallelProbe(MinecraftServer server, List<Fixture> fixtures) {
        Map<Fixture, String> serial = new HashMap<>();
        for (Fixture f : fixtures)
            serial.put(f, singleTarget(server.getLevel(f.dimension), f, false, false).hash());
        long started = System.nanoTime();
        try (ExecutorService workers = Executors.newFixedThreadPool(5)) {
            List<Future<String>> results = new ArrayList<>();
            for (Fixture f : fixtures)
                results.add(
                        workers.submit(
                                () ->
                                        singleTarget(server.getLevel(f.dimension), f, false, true)
                                                .hash()));
            for (int i = 0; i < results.size(); i++) {
                if (!serial.get(fixtures.get(i)).equals(results.get(i).get()))
                    throw new IllegalStateException("Parallel target mismatch " + fixtures.get(i));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Parallel context leakage");
        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.addProperty("workers", 5);
        result.addProperty("targets", fixtures.size());
        result.addProperty("wallMs", (System.nanoTime() - started) / 1e6);
        result.addProperty("contextBindings", GenerationContextRegistry.bindingCount());
        return result;
    }

    private static JsonObject readModelProbe(MinecraftServer server, Fixture f) {
        ServerLevel host = server.getLevel(f.dimension);
        JsonObject result =
                fixtureJson(f, RESOLVER.resolveLocalWorldSeed(f.master, f.dimension, f.pos));
        Meter aMeter = new Meter(), bMeter = new Meter();
        FeatureStableSnapshot a = aMeter.time(() -> singleTarget(host, f, false, false));
        FeatureStableSnapshot b = bMeter.time(() -> singleTarget(host, f, true, false));
        FeatureStableSnapshot bReverse = singleTarget(host, f, true, true);
        if (!b.diff(bReverse).equivalent())
            throw new IllegalStateException(
                    "Frozen Mosaic read model order mismatch " + f + " " + b.diff(bReverse));
        result.addProperty(
                "testedModel",
                "B uses detached, immutable Pre-FEATURES states with each neighbor's S(N); no physical integration implemented");
        result.addProperty("aHash", a.hash());
        result.addProperty("bHash", b.hash());
        result.addProperty("aVersusB", a.diff(b).toString());
        result.addProperty("bReverseEqual", true);
        result.add("aWallAndAllocationIncludingSnapshot", aMeter.json());
        result.add("bWallAndAllocationIncludingSnapshot", bMeter.json());
        return result;
    }

    private static Object field(Object instance, String name) {
        return field(instance.getClass(), instance, name);
    }

    private static Object field(Class<?> cls, Object instance, String name) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonObject fixtureJson(Fixture f, long seed) {
        JsonObject row = new JsonObject();
        row.addProperty("masterSeed", Long.toString(f.master));
        row.addProperty("localSeed", Long.toString(seed));
        row.addProperty("dimension", f.dimension.identifier().toString());
        row.addProperty("chunkX", f.pos.x());
        row.addProperty("chunkZ", f.pos.z());
        row.addProperty("requiredStructure", f.structure);
        return row;
    }

    private static void write(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Fixture> matrix() {
        List<Fixture> fixtures = new ArrayList<>();
        long[] seeds = {
            0, 1, -1, 123456789L, -987654321L, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEFL
        };
        for (long seed : seeds) fixtures.add(new Fixture(seed, Level.OVERWORLD, ChunkPos.ZERO, ""));
        for (long seed : seeds)
            for (ChunkPos p :
                    List.of(
                            new ChunkPos(1, 0),
                            new ChunkPos(-1, 1),
                            new ChunkPos(125, -37),
                            new ChunkPos(-20000, 30000)))
                fixtures.add(new Fixture(seed, Level.OVERWORLD, p, ""));
        for (var dim : List.of(Level.NETHER, Level.END))
            for (long seed : new long[] {0, -1, Long.MIN_VALUE, Long.MAX_VALUE})
                for (ChunkPos p :
                        List.of(
                                ChunkPos.ZERO,
                                new ChunkPos(1, -1),
                                new ChunkPos(-125, 37),
                                new ChunkPos(10000, -20000)))
                    fixtures.add(new Fixture(seed, dim, p, ""));
        return fixtures;
    }

    static final class Meter {
        long nanos, bytes;

        <T> T time(Supplier<T> work) {
            long allocation = allocated(), started = System.nanoTime();
            try {
                return work.get();
            } finally {
                nanos += System.nanoTime() - started;
                bytes += allocated() - allocation;
            }
        }

        JsonObject json() {
            JsonObject j = new JsonObject();
            j.addProperty("wallMs", nanos / 1e6);
            j.addProperty("jvmWideAllocatedBytes", bytes);
            return j;
        }

        private static long allocated() {
            long sum = 0;
            for (long value : ALLOC.getThreadAllocatedBytes(ALLOC.getAllThreadIds()))
                if (value > 0) sum += value;
            return sum;
        }
    }
}
