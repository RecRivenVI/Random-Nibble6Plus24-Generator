package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Selected native ore algorithms are the A contribution Oracle, not full Vanilla decoration parity.
 */
public final class FeatureAVerifier {
    private FeatureAVerifier() {}

    private static final ThreadLocal<Boolean> ORACLE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<List<String>> TRACE = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<AtomicInteger> WRITERS =
            ThreadLocal.withInitial(AtomicInteger::new);
    private static final AtomicInteger ACTIVE = new AtomicInteger(), PEAK = new AtomicInteger();

    private record Fixture(String id, String dimension, long seed, int x, int z) {}

    private static final List<Fixture> FIXTURES =
            List.of(
                    new Fixture("origin", "overworld", 123456789, 0, 0),
                    new Fixture("origin-alt", "overworld", 1, 0, 0),
                    new Fixture("nonorigin", "overworld", -5161763991829980711L, 125, -37),
                    new Fixture("village-edge", "overworld", 0, 12, 59),
                    new Fixture("mineshaft", "overworld", 0, -13, -7),
                    new Fixture("trial", "overworld", 0, 14, 5),
                    new Fixture("monument", "overworld", 0, 86, -12),
                    new Fixture("nether", "the_nether", 123456789, -11, -6),
                    new Fixture("fortress", "the_nether", 0, 15, 2),
                    new Fixture("end-spike", "the_end", 123456789, 2, 0));

    public static boolean oracle() {
        return ORACLE.get();
    }

    public static void writer(WorldGenLevel level, ChunkAccess chunk) {
        if (level instanceof StrictFeatureRegion || level instanceof NeighborOreContributions.Query)
            WRITERS.get().incrementAndGet();
    }

    public static boolean filter(PlacedFeature feature, WorldGenLevel level, BlockPos origin) {
        if (!oracle() || !(level instanceof NeighborOreContributions.Query)) return false;
        if (NeighborOreContributions.unsupported(feature) != null) return true;
        String key =
                level.registryAccess()
                        .lookupOrThrow(Registries.PLACED_FEATURE)
                        .getResourceKey(feature)
                        .orElseThrow()
                        .identifier()
                        .toString();
        ChunkPos c = ChunkPos.containing(origin);
        TRACE.get().add(c.x() + "," + c.z() + ":" + key);
        return false;
    }

    public static void run(MinecraftServer server) {
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                Boolean.getBoolean("featurea.lightweight")
                                        ? "lightweight_ores"
                                        : "current")
                        .resolve(System.getProperty("validation.target"));
        try {
            if (Boolean.getBoolean("featurea.export")) {
                Map<String, Object> launch = new LinkedHashMap<>();
                launch.put("java", ProcessHandle.current().info().command().orElseThrow());
                launch.put("directory", System.getProperty("user.dir"));
                launch.put("classpath", System.getProperty("java.class.path"));
                launch.put(
                        "jvmArguments",
                        ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                                .filter(s -> !s.startsWith("-Dfeaturea."))
                                .toList());
                var command = System.getProperty("sun.java.command").split(" ");
                launch.put("main", command[0]);
                launch.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
                write(base.resolve("launcher.json"), launch);
            } else {
                Path root = base.resolve(System.getProperty("featurea.variant", "host_0"));
                Map<String, Object> evidence = new ConcurrentHashMap<>();
                Map<String, String> serial = new LinkedHashMap<>();
                for (var f : FIXTURES) {
                    String sampled = generate(server, root, f, "serial", false, evidence);
                    String nativeHash = generate(server, root, f, "oracle", true, evidence);
                    if (!sampled.equals(nativeHash)) {
                        var a =
                                FeatureStableSnapshot.read(
                                        root.resolve("serial-" + f.id + "/a.bin.gz"));
                        var n =
                                FeatureStableSnapshot.read(
                                        root.resolve("oracle-" + f.id + "/a.bin.gz"));
                        write(
                                root.resolve("mismatch-" + f.id + ".json"),
                                Map.of(
                                        "fixture",
                                        f,
                                        "diff",
                                        a.diff(n),
                                        "metadata",
                                        Objects.toString(
                                                a.deterministicMetadataDifference(n), "equal")));
                        throw new IllegalStateException(
                                "Native contribution mismatch " + f.id + " " + a.diff(n));
                    }
                    serial.put(f.id, sampled);
                    if (Boolean.getBoolean("featurea.lightweight")
                            && !sampled.equals(
                                    generate(server, root, f, "original", false, evidence)))
                        throw new IllegalStateException("Local/original A differs " + f.id);
                    if (!sampled.equals(generate(server, root, f, "eager", false, evidence)))
                        throw new IllegalStateException(
                                "Exact/light height result differs " + f.id);
                }
                compareFullReference(server, root, FIXTURES.get(0), evidence);
                compareFullReference(server, root, FIXTURES.get(2), evidence);
                verifyContextBoundaries(server);
                evidence.put("independent-height", verifyIndependentHeights(server, root));
                var shuffled = new ArrayList<>(FIXTURES);
                Collections.shuffle(shuffled, new Random(263));
                for (var f : shuffled)
                    if (!serial.get(f.id)
                            .equals(generate(server, root, f, "shuffled", false, evidence)))
                        throw new IllegalStateException("A order dependence");
                ExecutorService pool = Executors.newFixedThreadPool(5);
                try {
                    var futures =
                            FIXTURES.stream()
                                    .map(
                                            f ->
                                                    CompletableFuture.runAsync(
                                                            () -> {
                                                                if (!serial.get(f.id)
                                                                        .equals(
                                                                                generate(
                                                                                        server,
                                                                                        root,
                                                                                        f,
                                                                                        "parallel",
                                                                                        false,
                                                                                        evidence)))
                                                                    throw new IllegalStateException(
                                                                            "A concurrent contamination");
                                                            },
                                                            pool))
                                    .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(futures).join();
                } finally {
                    pool.shutdown();
                }
                if (GenerationContextRegistry.bindingCount() != 0)
                    throw new IllegalStateException("A binding leak");
                if (PEAK.get() < 5) throw new IllegalStateException("No concurrent A coverage");
                write(
                        root.resolve("summary.json"),
                        Map.of(
                                "status",
                                "PASS",
                                "proofVersion",
                                Boolean.getBoolean("featurea.lightweight") ? 4 : 3,
                                "fixtures",
                                FIXTURES.size(),
                                "pid",
                                ProcessHandle.current().pid(),
                                "peakTargets",
                                PEAK.get(),
                                "bindings",
                                GenerationContextRegistry.bindingCount(),
                                "evidence",
                                evidence));
            }
        } catch (Exception e) {
            throw new IllegalStateException("A validation failed", e);
        }
        server.halt(false);
    }

    private static void compareFullReference(
            MinecraftServer server, Path root, Fixture f, Map<String, Object> evidence)
            throws java.io.IOException {
        var host =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        Identifier.withDefaultNamespace(f.dimension))));
        try (var c =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.VANILLA_CONTROL,
                        host,
                        f.seed,
                        new ChunkPos(f.x, f.z))) {
            var run = c.generateFeaturesStable();
            if (run.featureTrace().completedWriters().size() != 9
                    || run.featureTrace().maxConcurrentFeatureWriters() != 1
                    || run.executedStages().contains(ChunkStatus.LIGHT))
                throw new IllegalStateException("Wrong full reference procedure");
            var full =
                    FeatureStableSnapshot.capture(
                            "minecraft:" + f.dimension, run.targetChunk(), host.registryAccess());
            var b = FeatureStableSnapshot.read(root.resolve("serial-" + f.id + "/b.bin.gz"));
            var a = FeatureStableSnapshot.read(root.resolve("serial-" + f.id + "/a.bin.gz"));
            full.write(root.resolve("reference-" + f.id + "/full.bin.gz"));
            write(
                    root.resolve("reference-" + f.id + "/comparison.json"),
                    Map.of(
                            "bToFull",
                            b.diff(full),
                            "aToFull",
                            a.diff(full),
                            "trace",
                            run.featureTrace(),
                            "metrics",
                            run.metrics()));
            evidence.put(
                    "reference-" + f.id,
                    Map.of(
                            "bToFullBlocks",
                            b.diff(full).differingBlocks(),
                            "aToFullBlocks",
                            a.diff(full).differingBlocks(),
                            "writers",
                            9));
        }
    }

    private static void verifyContextBoundaries(MinecraftServer server) {
        var host = server.overworld();
        StrictFeatureBaseline.Result baseline;
        IsolatedGenerationContext closed;
        try (var c =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                host,
                                123456789,
                                new ChunkPos(0, 0));
                var foreign =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                host,
                                1,
                                new ChunkPos(0, 0))) {
            baseline = StrictFeatureBaseline.generate(c, (r, t) -> {});
            closed = c;
            try {
                NeighborOreContributions.apply(foreign, baseline);
                throw new IllegalStateException("Foreign seed/context accepted");
            } catch (IllegalArgumentException expected) {
                /* The incompatible input is rejected before any writes. */
            }
            var query = new NeighborOreContributions.Query(c, baseline);
            try {
                query.heightGate(c.target().getBlockX(64), c.target().getMinBlockZ(), 1, 300);
                throw new IllegalStateException("Height query escaped its finite frontier");
            } catch (IllegalArgumentException expected) {
                /* The request fails before constructing noise inputs. */
            }
            for (Runnable forbidden :
                    List.<Runnable>of(query::getLevel, query::getBlockTicks, query::getFluidTicks))
                try {
                    forbidden.run();
                    throw new IllegalStateException("Unnecessary physical capability exposed");
                } catch (UnsupportedOperationException expected) {
                    /* An ore query does not own lifecycle capabilities. */
                }
            query.close();
            for (Runnable read :
                    List.<Runnable>of(
                            () ->
                                    query.oceanFloor(
                                            c.target().getMinBlockX(), c.target().getMinBlockZ()),
                            () ->
                                    query.heightGate(
                                            c.target().getMinBlockX(),
                                            c.target().getMinBlockZ(),
                                            0,
                                            0),
                            () -> query.hasChunk(c.target().x(), c.target().z())))
                try {
                    read.run();
                    throw new IllegalStateException("Closed A query accepted a read");
                } catch (IllegalStateException expected) {
                    if (!expected.getMessage().contains("Closed A contribution query"))
                        throw expected;
                }
            if (Boolean.getBoolean("featurea.lightweight")) {
                var local =
                        LightweightFeatureBaseline.generate(
                                c, new LocalFeatureInputs.Observer() {}, (r, inputs) -> {});
                if (!local.inputs().isClosed())
                    throw new IllegalStateException("B query survived transfer boundary");
                try {
                    local.contributionInputs(foreign);
                    throw new IllegalStateException("Foreign A transfer accepted");
                } catch (IllegalArgumentException expected) {
                    /* No binding or input reuse across owners. */
                }
                var transferred = local.contributionInputs(c);
                if (transferred.region() == local.region()
                        || transferred.region().getChunk(c.target().x(), c.target().z()) == null)
                    throw new IllegalStateException("A transfer retained closed B query region");
            }
        }
        try {
            NeighborOreContributions.apply(closed, baseline);
            throw new IllegalStateException("Closed context accepted");
        } catch (IllegalArgumentException expected) {
            /* No mutable session survives its owner. */
        }
        if (GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("A exception cleanup failed");
    }

    private static Map<String, Object> verifyIndependentHeights(MinecraftServer server, Path root)
            throws java.io.IOException {
        Path inputs =
                Path.of(System.getProperty("mosaic.repositoryRoot"))
                        .resolve("validations/conformance-prefeatures/result");
        int fixtures = 0, columns = 0;
        long densitySamples = 0;
        for (String suite : List.of("terrain_matrix", "terrain_structures")) {
            Path directory = inputs.resolve(suite).resolve("26.3-fabric");
            var selection =
                    com.google.gson.JsonParser.parseString(
                                    Files.readString(directory.resolve("selection.json")))
                            .getAsJsonObject();
            for (var element : selection.getAsJsonArray("fixtures")) {
                var f = element.getAsJsonObject();
                String id = f.get("id").getAsString(), dimension = f.get("dimension").getAsString();
                long seed = f.get("local").getAsLong();
                int x = f.get("x").getAsInt(), z = f.get("z").getAsInt();
                Path nativePath = directory.resolve(id).resolve("native");
                var summary =
                        com.google.gson.JsonParser.parseString(
                                        Files.readString(nativePath.resolve("summary.json")))
                                .getAsJsonObject();
                if (!summary.get("status").getAsString().equals("PRODUCED")
                        || summary.get("featuresExecuted").getAsInt() != 0
                        || summary.get("pid").getAsLong() == ProcessHandle.current().pid())
                    throw new IllegalStateException("Invalid independent native height input");
                var host =
                        Objects.requireNonNull(
                                server.getLevel(
                                        ResourceKey.create(
                                                Registries.DIMENSION,
                                                Identifier.parse(dimension))));
                if (host.getSeed() == seed)
                    throw new IllegalStateException("Height host seed equals native seed");
                try (var context =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                host,
                                seed,
                                new ChunkPos(x, z))) {
                    Map<Long, ChunkAccess> palettes = new HashMap<>();
                    var query =
                            new OreHeightQuery(
                                    context,
                                    p ->
                                            palettes.computeIfAbsent(
                                                    p.pack(),
                                                    key -> {
                                                        var c =
                                                                new io.github.recrivenvi
                                                                        .randomnibble6plus24generator
                                                                        .worldgen.terrain
                                                                        .BiomePaletteChunk(
                                                                        p,
                                                                        host,
                                                                        host
                                                                                .palettedContainerFactory());
                                                        context.generator()
                                                                .createBiomes(
                                                                        context.randomState(),
                                                                        net.minecraft.world.level
                                                                                .levelgen.blending
                                                                                .Blender.empty(),
                                                                        context.structureManager(),
                                                                        c)
                                                                .join();
                                                        return c;
                                                    }));
                    for (int oz = 0; oz < 16; oz++)
                        for (int ox = 0; ox < 16; ox++)
                            query.definitelyBelow(
                                    context.target().getBlockX(ox),
                                    context.target().getBlockZ(oz),
                                    host.getMaxY() + 1);
                    var noise = FeatureStageSnapshot.read(nativePath.resolve("noise.bin.gz"));
                    var terrain = FeatureStageSnapshot.read(nativePath.resolve("terrain.bin.gz"));
                    byte[] density = Files.readAllBytes(nativePath.resolve("density.bin"));
                    int depth = density.length / (256 * Integer.BYTES);
                    if (query.metrics().columns() != 256)
                        throw new IllegalStateException("Incomplete independent height grid");
                    for (var column : query.metrics().evidence()) {
                        int index = (column.x() & 15) + (column.z() & 15) * 16;
                        var digest = java.security.MessageDigest.getInstance("SHA-256");
                        digest.update(
                                density, index * depth * Integer.BYTES, depth * Integer.BYTES);
                        String hash = java.util.HexFormat.of().formatHex(digest.digest());
                        if (!hash.equals(column.densityHash())
                                || column.noiseHeight()
                                        != noise.heightmapFirstAvailable(
                                                "WORLD_SURFACE_WG", column.x(), column.z())
                                || column.noiseOceanFloor()
                                        != noise.heightmapFirstAvailable(
                                                "OCEAN_FLOOR_WG", column.x(), column.z())
                                || column.upperHeight()
                                        < terrain.heightmapFirstAvailable(
                                                "OCEAN_FLOOR_WG", column.x(), column.z()))
                            throw new IllegalStateException(
                                    "Independent native height mismatch "
                                            + id
                                            + " "
                                            + column.x()
                                            + ","
                                            + column.z());
                        columns++;
                        densitySamples += depth;
                    }
                } catch (java.security.NoSuchAlgorithmException impossible) {
                    throw new AssertionError(impossible);
                }
                fixtures++;
            }
        }
        if (fixtures != 90 || columns != 90 * 256)
            throw new IllegalStateException("Incomplete independent height matrix");
        Map<String, Object> proof =
                Map.of(
                        "status",
                        "PASS",
                        "fixtures",
                        fixtures,
                        "columns",
                        columns,
                        "densitySamples",
                        densitySamples);
        write(root.resolve("independent-height.json"), proof);
        return proof;
    }

    private static String generate(
            MinecraftServer server,
            Path root,
            Fixture fixture,
            String run,
            boolean oracle,
            Map<String, Object> evidence) {
        var host =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        Identifier.withDefaultNamespace(fixture.dimension))));
        int loaded = host.getChunkSource().getLoadedChunksCount(),
                bt = host.getBlockTicks().count(),
                ft = host.getFluidTicks().count();
        long physicalEntities =
                java.util.stream.StreamSupport.stream(host.getAllEntities().spliterator(), false)
                        .count();
        PEAK.accumulateAndGet(ACTIVE.incrementAndGet(), Math::max);
        WRITERS.get().set(0);
        TRACE.get().clear();
        Map<Long, String> neighbors = new TreeMap<>();
        long started = System.nanoTime();
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        host,
                        fixture.seed,
                        new ChunkPos(fixture.x, fixture.z))) {
            boolean local =
                    Boolean.getBoolean("featurea.lightweight")
                            && !oracle
                            && !run.equals("original")
                            && !run.equals("eager");
            LightweightFeatureBaseline.Result preparation = null;
            StrictFeatureBaseline.Result baseline;
            if (local) {
                preparation =
                        LightweightFeatureBaseline.generate(
                                context,
                                new LocalFeatureInputs.Observer() {
                                    @Override
                                    public void fallback(
                                            net.minecraft.world.level.chunk.ProtoChunk chunk,
                                            String reason) {
                                        neighbors.put(
                                                chunk.getPos().pack(),
                                                FeatureStageSnapshot.capture(
                                                                "minecraft:" + fixture.dimension,
                                                                chunk,
                                                                host.registryAccess())
                                                        .hash());
                                    }
                                },
                                (r, inputs) -> {});
                baseline = preparation.contributionInputs(context);
            } else
                baseline =
                        StrictFeatureBaseline.generate(
                                context,
                                (r, terrain) ->
                                        terrain.forEach(
                                                (pos, c) -> {
                                                    if (!c.getPos().equals(context.target()))
                                                        neighbors.put(
                                                                pos,
                                                                FeatureStageSnapshot.capture(
                                                                                "minecraft:"
                                                                                        + fixture.dimension,
                                                                                c,
                                                                                host
                                                                                        .registryAccess())
                                                                        .hash());
                                                }));
            long baselineNanos = System.nanoTime() - started;
            var before =
                    FeatureStableSnapshot.capture(
                            "minecraft:" + fixture.dimension,
                            baseline.target(),
                            host.registryAccess());
            NeighborOreContributions.Metrics metrics = null;
            Map<String, Object> heightProof = Map.of();
            if (oracle) {
                ORACLE.set(true);
                try (var query = new NeighborOreContributions.Query(context, baseline, true)) {
                    var serialEntry = (Map<?, ?>) evidence.get("serial-" + fixture.id);
                    var serialMetrics =
                            (NeighborOreContributions.Metrics) serialEntry.get("metrics");
                    try (var proof = new OreHeightOracle(serialMetrics.heightQuery().evidence())) {
                        if (Boolean.getBoolean("featurea.lightweight"))
                            prepareHeightOracle(context, serialMetrics.heightQuery().evidence());
                        for (var column : serialMetrics.heightQuery().evidence()) {
                            int height =
                                    query.getHeight(
                                            net.minecraft.world.level.levelgen.Heightmap.Types
                                                    .OCEAN_FLOOR_WG,
                                            column.x(),
                                            column.z());
                            if (height > column.upperHeight())
                                throw new IllegalStateException(
                                        "Unsound height envelope " + column + " native=" + height);
                        }
                        heightProof = proof.finish();
                    }
                    if (serialMetrics.localHeightQuery() != null)
                        for (var column : serialMetrics.localHeightQuery().evidence()) {
                            var chunk =
                                    query.getChunk(
                                            Math.floorDiv(column.x(), 16),
                                            Math.floorDiv(column.z(), 16),
                                            ChunkStatus.TERRAIN,
                                            true);
                            for (int y = 0; y < column.states().size(); y++) {
                                var p = new BlockPos(column.x(), column.minY() + y, column.z());
                                if (!column.states()
                                        .get(y)
                                        .equals(chunk.getBlockState(p).toString()))
                                    throw new IllegalStateException(
                                            "A exact local column differs " + p);
                            }
                            if (column.floorWg()
                                    != chunk.getHeight(
                                                    net.minecraft.world.level.levelgen.Heightmap
                                                            .Types.OCEAN_FLOOR_WG,
                                                    column.x(),
                                                    column.z())
                                            + 1)
                                throw new IllegalStateException(
                                        "A exact local height differs " + column);
                        }
                    var verified = new LinkedHashMap<String, Object>(heightProof);
                    verified.put(
                            "localHeightColumns",
                            serialMetrics.localHeightQuery() == null
                                    ? 0
                                    : serialMetrics.localHeightQuery().evidence().size());
                    verified.put(
                            "localHeightBlocks",
                            serialMetrics.localHeightQuery() == null
                                    ? 0
                                    : serialMetrics.localHeightQuery().evidence().stream()
                                            .mapToInt(c -> c.states().size())
                                            .sum());
                    heightProof = Map.copyOf(verified);
                    for (int z = context.target().z() - 1; z <= context.target().z() + 1; z++)
                        for (int x = context.target().x() - 1; x <= context.target().x() + 1; x++) {
                            if (x == context.target().x() && z == context.target().z()) continue;
                            // Test-only native loop: unsupported features and structures are scoped
                            // out.
                            // A implementation itself never calls decoration for these source
                            // chunks.
                            context.generator()
                                    .applyBiomeDecoration(
                                            query,
                                            baseline.terrain().get(ChunkPos.pack(x, z)),
                                            baseline.structures().manager());
                        }
                } finally {
                    ORACLE.set(false);
                }
            } else
                metrics =
                        NeighborOreContributions.apply(
                                context, baseline, run.equals("eager"), local);
            if (WRITERS.get().get() != (oracle ? 9 : 1))
                throw new IllegalStateException("A concealed neighbor decoration");
            for (var entry : baseline.terrain().entrySet())
                if (!entry.getValue().getPos().equals(context.target())) {
                    String after =
                            FeatureStageSnapshot.capture(
                                            "minecraft:" + fixture.dimension,
                                            entry.getValue(),
                                            host.registryAccess())
                                    .hash();
                    if (!after.equals(neighbors.get(entry.getKey()))
                            || entry.getValue().getPersistedStatus() != ChunkStatus.TERRAIN)
                        throw new IllegalStateException("A neighbor mutated");
                }
            var snapshot =
                    FeatureStableSnapshot.capture(
                            "minecraft:" + fixture.dimension,
                            baseline.target(),
                            host.registryAccess());
            String metadata = before.deterministicMetadataDifference(snapshot);
            if (metadata != null)
                throw new IllegalStateException("Ore changed non-block state: " + metadata);
            if (snapshot.blockEntityCount() != before.blockEntityCount()
                    || snapshot.entityCount() != before.entityCount()
                    || snapshot.blockTickCount() != before.blockTickCount()
                    || snapshot.fluidTickCount() != before.fluidTickCount()
                    || snapshot.postProcessingCount() != before.postProcessingCount())
                throw new IllegalStateException("Ore changed ancillary data");
            Path output = root.resolve(run + "-" + fixture.id);
            Files.createDirectories(output);
            before.write(output.resolve("b.bin.gz"));
            snapshot.write(output.resolve("a.bin.gz"));
            write(output.resolve("b-to-a-diff.json"), before.diff(snapshot));
            write(output.resolve("entities-raw.json"), snapshot.rawEntityNbt());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("hash", snapshot.hash());
            entry.put("bHash", before.hash());
            entry.put("changed", !snapshot.hash().equals(before.hash()));
            entry.put("dimension", fixture.dimension);
            entry.put("differingBlocks", before.diff(snapshot).differingBlocks());
            entry.put("seed", Long.toString(fixture.seed));
            entry.put("hostSeed", Long.toString(host.getSeed()));
            entry.put("writerCount", WRITERS.get().get());
            entry.put("metrics", metrics);
            entry.put(
                    "localPreparation",
                    preparation == null ? null : preparation.inputs().metrics());
            entry.put("baselineFullNeighbors", baseline.terrain().size() - 1);
            entry.put("heightProof", heightProof);
            entry.put("nativeTrace", List.copyOf(TRACE.get()));
            entry.put("elapsedMs", (System.nanoTime() - started) / 1_000_000);
            entry.put("bPreparationMs", baselineNanos / 1_000_000);
            entry.put("blockEntities", snapshot.blockEntityCount());
            entry.put("entities", snapshot.entityCount());
            entry.put("blockTicks", snapshot.blockTickCount());
            entry.put("fluidTicks", snapshot.fluidTickCount());
            entry.put("postProcessing", snapshot.postProcessingCount());
            synchronized (evidence) {
                evidence.put(run + "-" + fixture.id, entry);
            }
            if (host.getChunkSource().getLoadedChunksCount() != loaded
                    || host.getBlockTicks().count() != bt
                    || host.getFluidTicks().count() != ft
                    || java.util.stream.StreamSupport.stream(
                                            host.getAllEntities().spliterator(), false)
                                    .count()
                            != physicalEntities)
                throw new IllegalStateException("A physical pollution");
            return snapshot.hash();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            ACTIVE.decrementAndGet();
            WRITERS.remove();
            TRACE.remove();
            ORACLE.remove();
        }
    }

    private static void write(Path file, Object object) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, evidenceCodec().toJson(object) + "\n");
    }

    static com.google.gson.Gson evidenceCodec() {
        var builder = new GsonBuilder().setPrettyPrinting();
        var compact = new com.google.gson.Gson();
        if (Boolean.getBoolean("featurea.lightweight"))
            builder.registerTypeAdapter(
                    LocalTerrainColumns.ColumnProof.class,
                    (com.google.gson.JsonSerializer<LocalTerrainColumns.ColumnProof>)
                            (column, type, serialization) -> {
                                var row = new LinkedHashMap<String, Object>();
                                row.put("x", column.x());
                                row.put("z", column.z());
                                row.put("minY", column.minY());
                                row.put("stateCount", column.states().size());
                                try {
                                    row.put(
                                            "statesSha256",
                                            java.util.HexFormat.of()
                                                    .formatHex(
                                                            java.security.MessageDigest.getInstance(
                                                                            "SHA-256")
                                                                    .digest(
                                                                            compact.toJson(
                                                                                            column
                                                                                                    .states())
                                                                                    .getBytes(
                                                                                            java.nio
                                                                                                    .charset
                                                                                                    .StandardCharsets
                                                                                                    .UTF_8))));
                                } catch (java.security.NoSuchAlgorithmException impossible) {
                                    throw new AssertionError(impossible);
                                }
                                row.put("surfaceWg", column.surfaceWg());
                                row.put("floorWg", column.floorWg());
                                row.put("noiseSurface", column.noiseSurface());
                                row.put("noiseFloor", column.noiseFloor());
                                row.put("densityHash", column.densityHash());
                                return serialization.serialize(row);
                            });
        return builder.create();
    }

    // Local B no longer preloads R1 terrain. Recapture real native buffers within the proof
    // scope, rather than weakening the oracle when its earlier R1 chunks are already cached.
    static void prepareHeightOracle(
            IsolatedGenerationContext context, List<OreHeightQuery.ColumnBound> columns) {
        var positions = new TreeSet<Long>();
        columns.forEach(
                c -> positions.add(ChunkPos.containing(new BlockPos(c.x(), 0, c.z())).pack()));
        for (long p : positions)
            try (var child =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC,
                            context.hostLevel(),
                            context.worldSeed(),
                            ChunkPos.unpack(p))) {
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain
                        .LightweightTerrainGenerator.generate(
                        child,
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                .CompactStructureInputs.resolve(child),
                        (s, c) -> {});
            }
    }
}
