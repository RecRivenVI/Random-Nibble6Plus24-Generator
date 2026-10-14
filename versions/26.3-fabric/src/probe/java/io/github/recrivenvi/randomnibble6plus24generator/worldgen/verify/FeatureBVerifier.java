package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureBaseline;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Full-state B evidence, not a client visual acceptance or a nine-writer Vanilla parity claim. */
public final class FeatureBVerifier {
    private static final Map<Object, java.util.concurrent.atomic.AtomicInteger> WRITERS =
            new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger PEAK =
            new java.util.concurrent.atomic.AtomicInteger();

    private FeatureBVerifier() {}

    record Fixture(String id, String dimension, long seed, int x, int z) {}

    static final List<Fixture> FIXTURES =
            List.of(
                    new Fixture("origin", "overworld", 123456789, 0, 0),
                    new Fixture("nonorigin", "overworld", -5161763991829980711L, 125, -37),
                    new Fixture("village", "overworld", 0, 17, 59),
                    new Fixture("village-edge", "overworld", 0, 12, 59),
                    new Fixture("mineshaft", "overworld", 0, -13, -7),
                    new Fixture("mineshaft-edge", "overworld", 0, -15, -13),
                    new Fixture("trial", "overworld", 0, 14, 5),
                    new Fixture("monument", "overworld", 0, 86, -12),
                    new Fixture("stronghold", "overworld", 0, -13, -106),
                    new Fixture("nether", "the_nether", 123456789, -11, -6),
                    new Fixture("fortress", "the_nether", 0, 15, 2),
                    new Fixture("end-spike", "the_end", 123456789, 2, 0),
                    new Fixture("end-city", "the_end", 0, -77, -79),
                    new Fixture("spike-outside", "the_end", 123456789, 1, 0),
                    new Fixture("origin-alt", "overworld", 1, 0, 0));

    public static void run(MinecraftServer server) {
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                Boolean.getBoolean("featureb.nativeCat")
                                        ? "native_cat"
                                        : Boolean.getBoolean("featureb.preparation")
                                                ? "preparation"
                                                : Boolean.getBoolean("featureb.structureReuse")
                                                        ? "structure_reuse"
                                                        : Boolean.getBoolean("featureb.lightweight")
                                                                ? "lightweight"
                                                                : "current")
                        .resolve(System.getProperty("validation.target"));
        Path root =
                Boolean.getBoolean("featureb.export")
                        ? base
                        : base.resolve(System.getProperty("featureb.variant", "host_0"));
        try {
            Files.createDirectories(root);
            if (Boolean.getBoolean("featureb.export")) {
                Map<String, Object> launch = new LinkedHashMap<>();
                launch.put("java", ProcessHandle.current().info().command().orElseThrow());
                if (Boolean.getBoolean("featureb.nativeCat"))
                    launch.put("nativeFixture", NativeFeatureRepeatVerifier.fixture());
                launch.put("directory", System.getProperty("user.dir"));
                launch.put("classpath", System.getProperty("java.class.path"));
                launch.put(
                        "jvmArguments",
                        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                                .filter(s -> !s.startsWith("-Dfeatureb."))
                                .toList());
                String[] command = System.getProperty("sun.java.command").split(" ");
                launch.put("main", command[0]);
                launch.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
                write(root.resolve("launcher.json"), launch);
            } else if (Boolean.getBoolean("featureb.nativeCat")) {
                NativeFeatureRepeatVerifier.run(server, root);
            } else if (Boolean.getBoolean("featureb.preparation")) {
                BPreparationVerifier.run(server, root);
            } else if (Boolean.getBoolean("featureb.lightweight")
                    || Boolean.getBoolean("featureb.structureReuse")) {
                LightweightFeatureBVerifier.run(server, root);
            } else {
                Map<String, Object> evidence = new LinkedHashMap<>();
                Map<String, FeatureStableSnapshot> serial = new LinkedHashMap<>();
                for (Fixture fixture : FIXTURES)
                    serial.put(fixture.id, generate(server, root, fixture, "serial", evidence));
                verifyPhysicalFrontier(server, root, serial, evidence);
                verifyExceptionalCleanup(server);
                List<Fixture> representative =
                        List.of(
                                FIXTURES.get(0),
                                FIXTURES.get(14),
                                FIXTURES.get(1),
                                FIXTURES.get(2),
                                FIXTURES.get(3),
                                FIXTURES.get(4),
                                FIXTURES.get(5),
                                FIXTURES.get(6),
                                FIXTURES.get(9),
                                FIXTURES.get(11));
                for (int i = representative.size() - 1; i >= 0; i--) {
                    Fixture fixture = representative.get(i);
                    compare(
                            serial.get(fixture.id),
                            generate(server, root, fixture, "reverse", evidence),
                            fixture.id);
                }
                var shuffled = new ArrayList<>(representative);
                Collections.shuffle(shuffled, new Random(263));
                for (Fixture fixture : shuffled)
                    compare(
                            serial.get(fixture.id),
                            generate(server, root, fixture, "shuffled", evidence),
                            fixture.id);
                // Includes identical coordinates with different seeds, under the four CPU limit.
                ExecutorService executor = Executors.newFixedThreadPool(5);
                try {
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (Fixture fixture : representative)
                        futures.add(
                                CompletableFuture.runAsync(
                                        () -> {
                                            FeatureStableSnapshot snapshot =
                                                    generate(
                                                            server,
                                                            root,
                                                            fixture,
                                                            "parallel",
                                                            evidence);
                                            compare(serial.get(fixture.id), snapshot, fixture.id);
                                        },
                                        executor));
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                } finally {
                    executor.shutdown();
                }
                if (GenerationContextRegistry.bindingCount() != 0)
                    throw new IllegalStateException("B context leak");
                if (PEAK.get() < 5 || !WRITERS.isEmpty())
                    throw new IllegalStateException("Concurrent coverage or trace cleanup missing");
                write(
                        root.resolve("summary.json"),
                        Map.of(
                                "status",
                                "PASS",
                                "fixtures",
                                FIXTURES.size(),
                                "repeatComparisons",
                                representative.size() * 3,
                                "bindings",
                                GenerationContextRegistry.bindingCount(),
                                "peakTargets",
                                PEAK.get(),
                                "evidence",
                                evidence,
                                "pid",
                                ProcessHandle.current().pid(),
                                "proofVersion",
                                3,
                                "physicalFrontier",
                                "absent/generated/mutated",
                                "exceptionCleanup",
                                true));
            }
        } catch (Exception failure) {
            throw new IllegalStateException("B validation failed", failure);
        }
        server.halt(false);
    }

    private static FeatureStableSnapshot generate(
            MinecraftServer server,
            Path root,
            Fixture fixture,
            String run,
            Map<String, Object> evidence) {
        ServerLevel host =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        Identifier.withDefaultNamespace(fixture.dimension))));
        int loaded = host.getChunkSource().getLoadedChunksCount();
        int blockTicks = host.getBlockTicks().count(), fluidTicks = host.getFluidTicks().count();
        long entities =
                java.util.stream.StreamSupport.stream(host.getAllEntities().spliterator(), false)
                        .count();
        Map<Long, String> neighborBefore = new TreeMap<>();
        Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>
                beforeBlocks = new HashMap<>();
        java.util.concurrent.atomic.AtomicInteger actualWriters =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Object> tracedRegion =
                new java.util.concurrent.atomic.AtomicReference<>();
        PEAK.accumulateAndGet(ACTIVE.incrementAndGet(), Math::max);
        long began = System.nanoTime();
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        host,
                        fixture.seed,
                        new ChunkPos(fixture.x, fixture.z))) {
            var result =
                    StrictFeatureBaseline.generate(
                            context,
                            (region, terrain) -> {
                                WRITERS.put(region, actualWriters);
                                tracedRegion.set(region);
                                terrain.forEach(
                                        (pos, chunk) -> {
                                            if (!chunk.getPos().equals(context.target()))
                                                neighborBefore.put(
                                                        pos,
                                                        FeatureStageSnapshot.capture(
                                                                        "minecraft:"
                                                                                + fixture.dimension,
                                                                        chunk,
                                                                        host.registryAccess())
                                                                .hash());
                                        });
                                var c = terrain.get(context.target().pack());
                                for (int y = c.getMinY(); y < c.getMaxY(); y++)
                                    for (int z = c.getPos().getMinBlockZ();
                                            z <= c.getPos().getMaxBlockZ();
                                            z++)
                                        for (int x = c.getPos().getMinBlockX();
                                                x <= c.getPos().getMaxBlockX();
                                                x++) {
                                            var p = new net.minecraft.core.BlockPos(x, y, z);
                                            beforeBlocks.put(p, c.getBlockState(p));
                                        }
                                if (fixture.id.equals("spike-outside") && run.equals("serial")) {
                                    // World-level tick requests must reject even beyond the finite
                                    // read frontier.
                                    var far =
                                            c.getPos()
                                                    .getWorldPosition()
                                                    .offset(48, c.getMinY() + 100, -48);
                                    if (region.setBlock(
                                            far,
                                            net.minecraft.world.level.block.Blocks.GOLD_BLOCK
                                                    .defaultBlockState(),
                                            3))
                                        throw new IllegalStateException("Far block write accepted");
                                    region.getBlockTicks()
                                            .schedule(
                                                    new net.minecraft.world.ticks.ScheduledTick<>(
                                                            net.minecraft.world.level.block.Blocks
                                                                    .STONE,
                                                            far,
                                                            0,
                                                            0));
                                    region.getFluidTicks()
                                            .schedule(
                                                    new net.minecraft.world.ticks.ScheduledTick<>(
                                                            net.minecraft.world.level.material
                                                                    .Fluids.WATER,
                                                            far,
                                                            0,
                                                            0));
                                }
                            });
            if (actualWriters.get() != 1)
                throw new IllegalStateException("B executed more than one writer");
            result.terrain()
                    .forEach(
                            (pos, chunk) -> {
                                if (chunk.getPos().equals(context.target())) return;
                                String after =
                                        FeatureStageSnapshot.capture(
                                                        "minecraft:" + fixture.dimension,
                                                        chunk,
                                                        host.registryAccess())
                                                .hash();
                                if (!after.equals(neighborBefore.get(pos))
                                        || chunk.getPersistedStatus() != ChunkStatus.TERRAIN)
                                    throw new IllegalStateException(
                                            "B neighbor polluted: "
                                                    + fixture.id
                                                    + " "
                                                    + chunk.getPos());
                            });
            if (result.target().getPersistedStatus() != ChunkStatus.FEATURES)
                throw new IllegalStateException("Wrong B terminal status");
            if (host.getChunkSource().getLoadedChunksCount() != loaded
                    || host.getBlockTicks().count() != blockTicks
                    || host.getFluidTicks().count() != fluidTicks
                    || java.util.stream.StreamSupport.stream(
                                            host.getAllEntities().spliterator(), false)
                                    .count()
                            != entities)
                throw new IllegalStateException("Physical state pollution");
            FeatureStableSnapshot snapshot =
                    FeatureStableSnapshot.capture(
                            "minecraft:" + fixture.dimension,
                            result.target(),
                            host.registryAccess());
            if ((fixture.id.equals("end-spike")
                            || fixture.id.equals("mineshaft")
                            || fixture.id.equals("end-city"))
                    && snapshot.entityCount() == 0)
                throw new IllegalStateException(
                        "Expected real entity fixture did not exercise entity path");
            if ((fixture.id.equals("village")
                            || fixture.id.equals("trial")
                            || fixture.id.equals("end-city"))
                    && snapshot.blockEntityCount() == 0)
                throw new IllegalStateException("Expected real BlockEntity fixture missing");
            if (fixture.id.equals("mineshaft") && snapshot.blockTickCount() == 0)
                throw new IllegalStateException("Missing positive block ticks");
            if (fixture.id.equals("monument") && snapshot.fluidTickCount() == 0)
                throw new IllegalStateException("Missing positive fluid ticks");
            if (fixture.id.equals("village-edge") && snapshot.postProcessingCount() == 0)
                throw new IllegalStateException("Missing positive post-processing");
            Path output = root.resolve(run + "-" + fixture.id);
            Files.createDirectories(output);
            snapshot.write(output.resolve("features.bin.gz"));
            compare(
                    snapshot,
                    FeatureStableSnapshot.read(output.resolve("features.bin.gz")),
                    fixture.id + "-snapshot-roundtrip");
            write(output.resolve("entities-raw.json"), snapshot.rawEntityNbt());
            var structureProjection =
                    result
                            .structures()
                            .captureStartTags(
                                    net.minecraft.world.level.levelgen.structure.pieces
                                            .StructurePieceSerializationContext.fromLevel(host))
                            .stream()
                            .map(PreFeaturesVerifier::canonicalNbt)
                            .toList();
            write(output.resolve("structure-projection.json"), structureProjection);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hash", snapshot.hash());
            entry.put("seed", Long.toString(fixture.seed));
            entry.put("hostSeed", Long.toString(host.getSeed()));
            entry.put("dimension", fixture.dimension);
            entry.put("x", fixture.x);
            entry.put("z", fixture.z);
            entry.put("writerCount", actualWriters.get());
            entry.put("structureProjectionCount", structureProjection.size());
            entry.put(
                    "structureProjectionHash",
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    String.join("\n", structureProjection)
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8))));
            entry.put("terrainNeighbors", result.terrain().size() - 1);
            entry.put("rejected", result.rejected());
            entry.put("blockEntities", snapshot.instantiatedBlockEntityCount());
            Map<String, Long> changed = new TreeMap<>();
            beforeBlocks.forEach(
                    (p, old) -> {
                        var now = result.target().getBlockState(p);
                        if (!old.equals(now))
                            changed.merge(
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                            .getKey(now.getBlock())
                                            .toString(),
                                    1L,
                                    Long::sum);
                    });
            entry.put("changedBlocks", changed);
            entry.put("physicalLevelEscapes", result.region().levelEscapeCounts());
            Map<Long, SortedSet<String>> visibleBiomes = new TreeMap<>();
            result.terrain()
                    .forEach(
                            (position, chunk) -> {
                                var holders =
                                        new HashSet<
                                                net.minecraft.core.Holder<
                                                        net.minecraft.world.level.biome.Biome>>();
                                chunk.collectBiomesInPalette(holders);
                                var names = new TreeSet<String>();
                                holders.forEach(
                                        biome ->
                                                names.add(
                                                        biome.unwrapKey()
                                                                .orElseThrow()
                                                                .identifier()
                                                                .toString()));
                                visibleBiomes.put(position, names);
                            });
            entry.put("visibleBiomes", visibleBiomes);
            entry.put("featureSeedCalls", contextCounter(context, "featureSeedInvocationCount"));
            entry.put(
                    "featureSeedSequence",
                    Long.toString(contextCounter(context, "featureSeedSequenceHash")));
            entry.put("suppressedPoi", contextCounter(context, "suppressedPhysicalPoiUpdates"));
            entry.put("pending", snapshot.pendingBlockEntityNbtCount());
            entry.put("entities", snapshot.entityCount());
            entry.put("blockTicks", snapshot.blockTickCount());
            entry.put("fluidTicks", snapshot.fluidTickCount());
            entry.put("postProcessing", snapshot.postProcessingCount());
            entry.put("decorationMs", result.decorationNanos() / 1_000_000);
            entry.put("totalMs", (System.nanoTime() - began) / 1_000_000);
            synchronized (evidence) {
                evidence.put(run + "-" + fixture.id, entry);
            }
            System.out.println("FEATURE B PASS " + run + " " + fixture.id + " " + entry);
            return snapshot;
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        } finally {
            if (tracedRegion.get() != null) WRITERS.remove(tracedRegion.get());
            ACTIVE.decrementAndGet();
        }
    }

    public static void observeWriter(
            net.minecraft.world.level.WorldGenLevel level,
            net.minecraft.world.level.chunk.ChunkAccess chunk) {
        if (Boolean.getBoolean("featureb.preparation")) {
            BPreparationVerifier.observeWriter(level, chunk);
            return;
        }
        if (!(level
                instanceof
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.features
                                .StrictFeatureRegion
                        region)) return;
        var count = WRITERS.get(region);
        if (count == null
                || !region.getCenter().equals(chunk.getPos())
                || count.incrementAndGet() != 1)
            throw new IllegalStateException("Unregistered or repeated B writer");
    }

    static void registerWriter(Object region, java.util.concurrent.atomic.AtomicInteger count) {
        WRITERS.put(region, count);
    }

    static void removeWriter(Object region) {
        WRITERS.remove(region);
    }

    private static void verifyPhysicalFrontier(
            MinecraftServer server,
            Path root,
            Map<String, FeatureStableSnapshot> serial,
            Map<String, Object> evidence) {
        Fixture fixture = FIXTURES.get(1);
        var host = server.overworld();
        var physical =
                host.getChunkSource().getChunk(fixture.x, fixture.z, ChunkStatus.TERRAIN, true);
        String before =
                FeatureStageSnapshot.capture("minecraft:overworld", physical, host.registryAccess())
                        .hash();
        compare(
                serial.get(fixture.id),
                generate(server, root, fixture, "generated-host", evidence),
                "generated-host");
        if (!before.equals(
                FeatureStageSnapshot.capture("minecraft:overworld", physical, host.registryAccess())
                        .hash()))
            throw new IllegalStateException("Generated physical frontier mutated by B");
        var marker = physical.getPos().getWorldPosition().offset(3, physical.getMinY() + 100, 3);
        physical.setBlockState(
                marker, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(), 0);
        String mutated =
                FeatureStageSnapshot.capture("minecraft:overworld", physical, host.registryAccess())
                        .hash();
        if (before.equals(mutated))
            throw new IllegalStateException("Host marker failed to change state");
        compare(
                serial.get(fixture.id),
                generate(server, root, fixture, "mutated-host", evidence),
                "mutated-host");
        if (!mutated.equals(
                FeatureStageSnapshot.capture("minecraft:overworld", physical, host.registryAccess())
                        .hash()))
            throw new IllegalStateException("Mutated physical frontier altered by B");
    }

    private static void verifyExceptionalCleanup(MinecraftServer server) {
        var sentinel = new IllegalStateException("Injected B validation failure");
        boolean propagated = false;
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        server.overworld(),
                        FIXTURES.get(0).seed,
                        new ChunkPos(0, 0))) {
            StrictFeatureBaseline.generate(
                    context,
                    (region, terrain) -> {
                        throw sentinel;
                    });
        } catch (IllegalStateException failure) {
            if (failure != sentinel) throw failure;
            propagated = true;
        }
        if (!propagated || GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("B failure swallowed or context bindings leaked");
    }

    private static void compare(FeatureStableSnapshot a, FeatureStableSnapshot b, String id) {
        if (!a.hash().equals(b.hash()))
            throw new IllegalStateException(
                    "B order/parallel mismatch "
                            + id
                            + ": "
                            + a.diff(b)
                            + "; "
                            + a.deterministicMetadataDifference(b));
    }

    private static long contextCounter(IsolatedGenerationContext context, String name) {
        try {
            var field = IsolatedGenerationContext.class.getDeclaredField(name);
            field.setAccessible(true);
            return ((java.util.concurrent.atomic.AtomicLong) field.get(context)).get();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        Files.writeString(
                path, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }
}
