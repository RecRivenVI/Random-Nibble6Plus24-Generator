package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.BiomePaletteChunk;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Selective tree Oracle, dense mutable chunks vs sparse state; not full Vanilla FEATURES parity.
 */
public final class FeatureATreeVerifier {
    private FeatureATreeVerifier() {}

    private static final ThreadLocal<Boolean> NATIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<List<String>> TRACE = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<AtomicInteger> WRITERS =
            ThreadLocal.withInitial(AtomicInteger::new);
    private static final AtomicInteger ACTIVE = new AtomicInteger(), PEAK = new AtomicInteger();

    private record Fixture(String id, long seed, int x, int z) {}

    public static boolean nativeControl() {
        return NATIVE.get();
    }

    public static void writer(WorldGenLevel level) {
        if (level instanceof StrictFeatureRegion || level instanceof TreeProjectionRegion)
            WRITERS.get().incrementAndGet();
    }

    public static boolean filter(PlacedFeature feature, WorldGenLevel level) {
        return nativeControl()
                && level instanceof TreeProjectionRegion
                && TreeContributionSupport.unsupportedNeighbor(feature, level.registryAccess())
                        != null;
    }

    public static void checkpoint(PlacedFeature placed, RandomSource random, WorldGenLevel level) {
        if (!nativeControl()
                || !(level instanceof TreeProjectionRegion region)
                || filter(placed, level)) return;
        String key =
                level.registryAccess()
                        .lookupOrThrow(Registries.PLACED_FEATURE)
                        .getResourceKey(placed)
                        .orElseThrow()
                        .identifier()
                        .toString();
        var pos = region.getCenter();
        TRACE.get()
                .add(
                        pos.x()
                                + ","
                                + pos.z()
                                + ":"
                                + key
                                + ":"
                                + ((WorldgenRandom) random).getCount()
                                + ":"
                                + random.nextLong());
    }

    private static void write(Path path, Object value) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, FeatureAVerifier.evidenceCodec().toJson(value) + "\n");
    }

    public static void run(MinecraftServer server) {
        Path base =
                Path.of(System.getProperty("validation.result"))
                        .resolve(
                                Boolean.getBoolean("featurea.conifers")
                                        ? "conifers"
                                        : Boolean.getBoolean("featurea.lightweight")
                                                ? "lightweight_trees"
                                                : "trees")
                        .resolve(System.getProperty("validation.target"));
        try {
            if (Boolean.getBoolean("featurea.export")) {
                var launch = new LinkedHashMap<String, Object>();
                launch.put("java", ProcessHandle.current().info().command().orElseThrow());
                launch.put("directory", System.getProperty("user.dir"));
                launch.put("classpath", System.getProperty("java.class.path"));
                launch.put(
                        "jvmArguments",
                        java.lang.management.ManagementFactory.getRuntimeMXBean()
                                .getInputArguments()
                                .stream()
                                .filter(s -> !s.startsWith("-Dfeaturea."))
                                .toList());
                var command = System.getProperty("sun.java.command").split(" ");
                launch.put("main", command[0]);
                launch.put("gameArguments", Arrays.copyOfRange(command, 1, command.length));
                write(base.resolve("launcher.json"), launch);
            } else {
                Path root = base.resolve(System.getProperty("featurea.variant", "host_0"));
                ServerLevel host = server.overworld();
                var fixtures = discover(host);
                write(root.resolve("selection.json"), fixtures);
                Map<String, Object> evidence = new ConcurrentHashMap<>();
                Map<String, String> expected = new HashMap<>();
                for (var f : fixtures) {
                    String a = generate(host, root, f, "serial", false, evidence),
                            n = generate(host, root, f, "native", true, evidence);
                    if (!a.equals(n)) {
                        var first =
                                FeatureStableSnapshot.read(
                                        root.resolve("serial-" + f.id + "/a.bin.gz"));
                        var second =
                                FeatureStableSnapshot.read(
                                        root.resolve("native-" + f.id + "/a.bin.gz"));
                        write(
                                root.resolve("mismatch-" + f.id + ".json"),
                                Map.of(
                                        "diff",
                                        first.diff(second),
                                        "metadata",
                                        Objects.toString(
                                                first.deterministicMetadataDifference(second),
                                                "equal")));
                        throw new IllegalStateException("Sparse/native tree mismatch " + f.id);
                    }
                    expected.put(f.id, a);
                    if (Boolean.getBoolean("featurea.lightweight")
                            && !a.equals(generate(host, root, f, "original", false, evidence)))
                        throw new IllegalStateException("Local/original tree A differs " + f.id);
                }
                var reverse = new ArrayList<>(fixtures);
                Collections.reverse(reverse);
                for (var f : reverse)
                    if (!expected.get(f.id)
                            .equals(generate(host, root, f, "reverse", false, evidence)))
                        throw new IllegalStateException("Tree request order dependence");
                var pool = Executors.newFixedThreadPool(5);
                try {
                    var jobs =
                            fixtures.stream()
                                    .map(
                                            f ->
                                                    CompletableFuture.runAsync(
                                                            () -> {
                                                                if (!expected.get(f.id)
                                                                        .equals(
                                                                                generate(
                                                                                        host,
                                                                                        root,
                                                                                        f,
                                                                                        "parallel",
                                                                                        false,
                                                                                        evidence)))
                                                                    throw new IllegalStateException(
                                                                            "Tree concurrent contamination");
                                                            },
                                                            pool))
                                    .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(jobs).join();
                } finally {
                    pool.shutdown();
                }
                var kernel = kernel(host, root, fixtures.get(2));
                if (Boolean.getBoolean("featurea.conifers")) {
                    var coniferKernel = kernel(host, root, fixtures.get(6), true);
                    write(root.resolve("conifer-kernel.json"), coniferKernel);
                    for (var f : fixtures.subList(6, fixtures.size())) {
                        var sample = (Map<?, ?>) evidence.get("serial-" + f.id);
                        var tree = (NeighborTreeContributions.Metrics) sample.get("metrics");
                        if (!tree.supported().contains("minecraft:trees_taiga")
                                || tree.state().targetBlockWrites() == 0)
                            throw new IllegalStateException(
                                    "Missing natural conifer contribution " + f.id);
                    }
                }
                var columnProof = verifyIndependentColumns(server, root);
                verifyFailureCleanup(host, fixtures.get(2));
                if (GenerationContextRegistry.bindingCount() != 0)
                    throw new IllegalStateException("Tree context leak");
                long positives =
                        fixtures.stream()
                                .filter(
                                        f ->
                                                ((Number)
                                                                        ((Map<?, ?>)
                                                                                        evidence
                                                                                                .get(
                                                                                                        "serial-"
                                                                                                                + f.id))
                                                                                .get(
                                                                                        "differingBlocks"))
                                                                .intValue()
                                                        > 0)
                                .count();
                if (positives < 2 || PEAK.get() < 5)
                    throw new IllegalStateException(
                            "No real tree contribution / parallel positive");
                write(
                        root.resolve("summary.json"),
                        Map.of(
                                "status",
                                "PASS",
                                "proofVersion",
                                Boolean.getBoolean("featurea.conifers")
                                        ? 4
                                        : Boolean.getBoolean("featurea.lightweight") ? 3 : 2,
                                "pid",
                                ProcessHandle.current().pid(),
                                "fixtures",
                                fixtures.size(),
                                "positives",
                                positives,
                                "peakTargets",
                                PEAK.get(),
                                "bindings",
                                GenerationContextRegistry.bindingCount(),
                                "kernel",
                                kernel,
                                "independentColumns",
                                columnProof,
                                "evidence",
                                evidence));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Tree A validation failed", e);
        }
        server.halt(false);
    }

    private static List<Fixture> discover(ServerLevel host) {
        var fixtures = new ArrayList<Fixture>();
        fixtures.add(new Fixture("origin", 123456789, 0, 0));
        fixtures.add(new Fixture("nonorigin", -5161763991829980711L, 125, -37));
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, host, 0, new ChunkPos(0, 0))) {
            for (int ring = 1; ring <= 16 && fixtures.size() < 6; ring++)
                for (int z = -ring; z <= ring && fixtures.size() < 6; z++)
                    for (int x = -ring; x <= ring && fixtures.size() < 6; x++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                        var p = new ChunkPos(x * 4, z * 4);
                        var carrier =
                                new BiomePaletteChunk(p, host, host.palettedContainerFactory());
                        context.generator()
                                .createBiomes(
                                        context.randomState(),
                                        Blender.empty(),
                                        context.structureManager(),
                                        carrier)
                                .join();
                        Set<Holder<Biome>> biomes = new HashSet<>();
                        carrier.collectBiomesInPalette(biomes);
                        if (biomes.stream()
                                .anyMatch(
                                        b ->
                                                b.is(Biomes.BIRCH_FOREST)
                                                        || b.is(Biomes.OLD_GROWTH_BIRCH_FOREST)))
                            fixtures.add(
                                    new Fixture("birch-" + (fixtures.size() - 2), 0, p.x(), p.z()));
                    }
        }
        if (fixtures.size() != 6)
            throw new IllegalStateException(
                    "Native stored-palette scan found too few birch targets");
        if (Boolean.getBoolean("featurea.conifers")) {
            try (var context =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC, host, 0, new ChunkPos(0, 0))) {
                for (int ring = 1; ring <= 64 && fixtures.size() < 8; ring++)
                    for (int z = -ring; z <= ring && fixtures.size() < 8; z++)
                        for (int x = -ring; x <= ring && fixtures.size() < 8; x++) {
                            if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                            var p = new ChunkPos(x * 4, z * 4);
                            var carrier =
                                    new BiomePaletteChunk(p, host, host.palettedContainerFactory());
                            context.generator()
                                    .createBiomes(
                                            context.randomState(),
                                            Blender.empty(),
                                            context.structureManager(),
                                            carrier)
                                    .join();
                            Set<Holder<Biome>> biomes = new HashSet<>();
                            carrier.collectBiomesInPalette(biomes);
                            if (biomes.stream()
                                    .anyMatch(b -> b.is(Biomes.TAIGA) || b.is(Biomes.SNOWY_TAIGA)))
                                fixtures.add(
                                        new Fixture(
                                                "taiga-" + (fixtures.size() - 6), 0, p.x(), p.z()));
                        }
            }
            if (fixtures.size() != 8)
                throw new IllegalStateException("Too few stored-palette taiga targets");
        }
        return List.copyOf(fixtures);
    }

    private static Map<String, Object> verifyIndependentColumns(MinecraftServer server, Path root)
            throws java.io.IOException {
        Path inputs =
                Path.of(System.getProperty("mosaic.repositoryRoot"))
                        .resolve("validations/conformance-prefeatures/result");
        int fixtures = 0, columns = 0, fallbacks = 0, fallbackChunks = 0, densityColumns = 0;
        long heightOnlyQueries = 0, heightCacheHits = 0, rawSamples = 0;
        long densitySamples = 0, densityTiles = 0, fullDensitySamples = 0;
        long cells = 0;
        var details = new ArrayList<Map<String, Object>>();
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
                var pos = new ChunkPos(f.get("x").getAsInt(), f.get("z").getAsInt());
                Path nativePath = directory.resolve(id).resolve("native");
                var produced =
                        com.google.gson.JsonParser.parseString(
                                        Files.readString(nativePath.resolve("summary.json")))
                                .getAsJsonObject();
                if (!produced.get("status").getAsString().equals("PRODUCED")
                        || produced.get("featuresExecuted").getAsInt() != 0
                        || produced.get("pid").getAsLong() == ProcessHandle.current().pid())
                    throw new IllegalStateException("Not independent pure TERRAIN evidence " + id);
                ServerLevel host =
                        Objects.requireNonNull(
                                server.getLevel(
                                        ResourceKey.create(
                                                Registries.DIMENSION,
                                                Identifier.parse(dimension))));
                var snapshot = FeatureStageSnapshot.read(nativePath.resolve("terrain.bin.gz"));
                var noiseSnapshot = FeatureStageSnapshot.read(nativePath.resolve("noise.bin.gz"));
                byte[] nativeDensity = Files.readAllBytes(nativePath.resolve("density.bin"));
                try (var c =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, pos)) {
                    var palettes = new HashMap<Long, ChunkAccess>();
                    try (var query =
                            new LocalTerrainColumns(
                                    c,
                                    p ->
                                            palettes.computeIfAbsent(
                                                    p.pack(),
                                                    key -> {
                                                        var b =
                                                                new BiomePaletteChunk(
                                                                        p,
                                                                        host,
                                                                        host
                                                                                .palettedContainerFactory());
                                                        c.generator()
                                                                .createBiomes(
                                                                        c.randomState(),
                                                                        Blender.empty(),
                                                                        c.structureManager(),
                                                                        b)
                                                                .join();
                                                        return b;
                                                    }))) {
                        int count = 0;
                        ChunkAccess exactFallback = null;
                        for (int[] offset :
                                new int[][] {
                                    {0, 0}, {15, 0}, {0, 15}, {15, 15}, {1, 8}, {14, 8}, {8, 1},
                                    {8, 14}
                                }) {
                            int x = pos.getBlockX(offset[0]), z = pos.getBlockZ(offset[1]);
                            // Query heights before any block column exists, then repeat after the
                            // material/carving column was read. Both routes must match native.
                            for (var type :
                                    List.of(
                                            Heightmap.Types.WORLD_SURFACE_WG,
                                            Heightmap.Types.WORLD_SURFACE,
                                            Heightmap.Types.OCEAN_FLOOR_WG,
                                            Heightmap.Types.OCEAN_FLOOR)) {
                                var height = query.height(type, x, z);
                                if (height.isPresent()
                                        && height.getAsInt() + 1
                                                != snapshot.heightmapFirstAvailable(
                                                        type.getSerializationKey(), x, z))
                                    throw new IllegalStateException(
                                            "Independent height-only/native mismatch "
                                                    + id
                                                    + " "
                                                    + type);
                                if (!height.equals(query.height(type, x, z)))
                                    throw new IllegalStateException(
                                            "Repeated local height changed " + id);
                            }
                            if (query.block(new BlockPos(x, 64, z)).isEmpty()) {
                                fallbacks++;
                                if (exactFallback == null) {
                                    exactFallback =
                                            io.github.recrivenvi.randomnibble6plus24generator
                                                    .worldgen.terrain.LightweightTerrainGenerator
                                                    .generate(
                                                            c,
                                                            io.github.recrivenvi
                                                                    .randomnibble6plus24generator
                                                                    .worldgen.structure
                                                                    .CompactStructureInputs.resolve(
                                                                    c),
                                                            (s, v) -> {})
                                                    .target();
                                    fallbackChunks++;
                                }
                                for (int y = snapshot.minY();
                                        y < snapshot.minY() + snapshot.height();
                                        y++) {
                                    var p = new BlockPos(x, y, z);
                                    if (!exactFallback
                                            .getBlockState(p)
                                            .toString()
                                            .equals(snapshot.blockStateAt(p)))
                                        throw new IllegalStateException(
                                                "Exact fallback/native mismatch " + id + " " + p);
                                }
                                for (var type :
                                        List.of(
                                                Heightmap.Types.WORLD_SURFACE_WG,
                                                Heightmap.Types.OCEAN_FLOOR_WG,
                                                Heightmap.Types.WORLD_SURFACE,
                                                Heightmap.Types.OCEAN_FLOOR))
                                    if (exactFallback.getHeight(type, x, z) + 1
                                            != snapshot.heightmapFirstAvailable(
                                                    type.getSerializationKey(), x, z))
                                        throw new IllegalStateException(
                                                "Exact fallback/native height mismatch "
                                                        + id
                                                        + " "
                                                        + type);
                                continue;
                            }
                            for (int y = snapshot.minY();
                                    y < snapshot.minY() + snapshot.height();
                                    y++) {
                                var p = new BlockPos(x, y, z);
                                String value = query.block(p).orElseThrow().toString();
                                if (!value.equals(snapshot.blockStateAt(p)))
                                    throw new IllegalStateException(
                                            "Independent native local column mismatch "
                                                    + id
                                                    + " "
                                                    + p
                                                    + " local="
                                                    + value
                                                    + " native="
                                                    + snapshot.blockStateAt(p));
                                cells++;
                            }
                            for (var type :
                                    List.of(
                                            Heightmap.Types.WORLD_SURFACE_WG,
                                            Heightmap.Types.OCEAN_FLOOR_WG,
                                            Heightmap.Types.WORLD_SURFACE,
                                            Heightmap.Types.OCEAN_FLOOR))
                                if (query.height(type, x, z).orElseThrow() + 1
                                        != snapshot.heightmapFirstAvailable(
                                                type.getSerializationKey(), x, z))
                                    throw new IllegalStateException(
                                            "Independent native local height mismatch "
                                                    + id
                                                    + " "
                                                    + type);
                            columns++;
                            count++;
                        }
                        for (var column : query.metrics().evidence()) {
                            int depth =
                                    c.generator()
                                            .generatorSettings()
                                            .value()
                                            .noiseSettings()
                                            .clampToHeightAccessor(host)
                                            .height();
                            String hash;
                            try {
                                var digest = java.security.MessageDigest.getInstance("SHA-256");
                                int index = (column.x() & 15) + (column.z() & 15) * 16;
                                digest.update(
                                        nativeDensity,
                                        index * depth * Integer.BYTES,
                                        depth * Integer.BYTES);
                                hash = java.util.HexFormat.of().formatHex(digest.digest());
                            } catch (java.security.NoSuchAlgorithmException impossible) {
                                throw new AssertionError(impossible);
                            }
                            if (!hash.equals(column.densityHash())
                                    || column.noiseSurface()
                                            != noiseSnapshot.heightmapFirstAvailable(
                                                    "WORLD_SURFACE_WG", column.x(), column.z())
                                    || column.noiseFloor()
                                            != noiseSnapshot.heightmapFirstAvailable(
                                                    "OCEAN_FLOOR_WG", column.x(), column.z()))
                                throw new IllegalStateException(
                                        "Independent native local density/NOISE mismatch "
                                                + id
                                                + " "
                                                + column.x()
                                                + ","
                                                + column.z());
                            densityColumns++;
                        }
                        heightOnlyQueries += query.counters().heightOnlyQueries();
                        heightCacheHits += query.counters().heightCacheHits();
                        rawSamples += query.counters().rawSamples();
                        densitySamples += query.counters().densitySamples();
                        densityTiles += query.counters().densityTiles();
                        fullDensitySamples +=
                                (long) query.counters().noiseBatches()
                                        * 256
                                        * c.generator()
                                                .generatorSettings()
                                                .value()
                                                .noiseSettings()
                                                .clampToHeightAccessor(host)
                                                .height();
                        details.add(
                                Map.of(
                                        "fixture",
                                        id,
                                        "dimension",
                                        dimension,
                                        "columns",
                                        count,
                                        "fallbacks",
                                        query.metrics().fallbacks()));
                        expectRejected(
                                () ->
                                        query.block(
                                                new BlockPos(
                                                        pos.getMinBlockX() + 48,
                                                        64,
                                                        pos.getMinBlockZ())));
                        expectRejected(
                                () ->
                                        query.height(
                                                Heightmap.Types.MOTION_BLOCKING,
                                                pos.getMinBlockX(),
                                                pos.getMinBlockZ()));
                        c.close();
                        expectRejected(
                                () ->
                                        query.block(
                                                new BlockPos(
                                                        pos.getMinBlockX(),
                                                        64,
                                                        pos.getMinBlockZ())));
                        query.close();
                        expectRejected(
                                () ->
                                        query.block(
                                                new BlockPos(
                                                        pos.getMinBlockX(),
                                                        64,
                                                        pos.getMinBlockZ())));
                    }
                }
                fixtures++;
            }
        }
        if (fixtures != 90
                || columns < 300
                || cells == 0
                || densityColumns != columns
                || heightOnlyQueries == 0
                || heightCacheHits == 0) {
            write(
                    root.resolve("independent-columns-failure.json"),
                    Map.of(
                            "fixtures",
                            fixtures,
                            "columns",
                            columns,
                            "fallbacks",
                            fallbacks,
                            "details",
                            details));
            throw new IllegalStateException(
                    "Insufficient independent local column proof: "
                            + fixtures
                            + " fixtures, "
                            + columns
                            + " columns, "
                            + fallbacks
                            + " fallbacks");
        }
        var result =
                Map.<String, Object>of(
                        "status",
                        "PASS",
                        "fixtures",
                        fixtures,
                        "columns",
                        columns,
                        "cells",
                        cells,
                        "fallbacks",
                        fallbacks,
                        "fallbackChunks",
                        fallbackChunks,
                        "densityColumns",
                        densityColumns,
                        "details",
                        details);
        var queryWork =
                Map.of(
                        "heightOnlyQueries",
                        heightOnlyQueries,
                        "heightCacheHits",
                        heightCacheHits,
                        "rawSamples",
                        rawSamples,
                        "densitySamples",
                        densitySamples,
                        "densityTiles",
                        densityTiles,
                        "fullDensitySamples",
                        fullDensitySamples);
        write(root.resolve("independent-column-work.json"), queryWork);
        write(root.resolve("independent-columns.json"), result);
        return result;
    }

    private static String generate(
            ServerLevel host,
            Path root,
            Fixture f,
            String run,
            boolean nativeControl,
            Map<String, Object> evidence) {
        int loaded = host.getChunkSource().getLoadedChunksCount(),
                bt = host.getBlockTicks().count(),
                ft = host.getFluidTicks().count();
        long entities =
                java.util.stream.StreamSupport.stream(host.getAllEntities().spliterator(), false)
                        .count();
        ACTIVE.incrementAndGet();
        PEAK.accumulateAndGet(ACTIVE.get(), Math::max);
        WRITERS.get().set(0);
        TRACE.get().clear();
        try (var c =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        host,
                        f.seed,
                        new ChunkPos(f.x, f.z))) {
            var neighbors = new HashMap<Long, String>();
            boolean local =
                    Boolean.getBoolean("featurea.lightweight")
                            && !nativeControl
                            && !run.equals("original");
            LightweightFeatureBaseline.Result preparation = null;
            StrictFeatureBaseline.Result b;
            if (local) {
                preparation =
                        LightweightFeatureBaseline.generate(
                                c,
                                new LocalFeatureInputs.Observer() {
                                    @Override
                                    public void fallback(ProtoChunk chunk, String reason) {
                                        neighbors.put(
                                                chunk.getPos().pack(),
                                                FeatureStageSnapshot.capture(
                                                                "minecraft:overworld",
                                                                chunk,
                                                                host.registryAccess())
                                                        .hash());
                                    }
                                },
                                (r, inputs) -> {});
                b = preparation.contributionInputs(c);
            } else
                b =
                        StrictFeatureBaseline.generate(
                                c,
                                (r, t) ->
                                        t.forEach(
                                                (p, ch) -> {
                                                    if (!ch.getPos().equals(c.target()))
                                                        neighbors.put(
                                                                p,
                                                                FeatureStageSnapshot.capture(
                                                                                "minecraft:overworld",
                                                                                ch,
                                                                                host
                                                                                        .registryAccess())
                                                                        .hash());
                                                }));
            var oreMetrics = NeighborOreContributions.apply(c, b, false, local);
            var before =
                    FeatureStableSnapshot.capture(
                            "minecraft:overworld", b.target(), host.registryAccess());
            NeighborTreeContributions.Metrics metrics = null;
            TreeProjectionRegion.Metrics nativeState = null;
            if (nativeControl) {
                try (var region = fullControl(c, b)) {
                    NATIVE.set(true);
                    try {
                        var serial = (Map<?, ?>) evidence.get("serial-" + f.id);
                        var state =
                                ((NeighborTreeContributions.Metrics) serial.get("metrics")).state();
                        try (var proof = new OreHeightOracle(state.airQuery().evidence())) {
                            if (Boolean.getBoolean("featurea.lightweight"))
                                FeatureAVerifier.prepareHeightOracle(
                                        c, state.airQuery().evidence());
                            for (var column : state.airQuery().evidence())
                                region.terrainQuery().oceanFloor(column.x(), column.z());
                            proof.finish();
                        }
                        for (var pos : state.certifiedAir())
                            if (region.terrainQuery()
                                            .getChunk(
                                                    Math.floorDiv(pos.getX(), 16),
                                                    Math.floorDiv(pos.getZ(), 16),
                                                    ChunkStatus.TERRAIN,
                                                    true)
                                            .getBlockState(pos)
                                    != net.minecraft.world.level.block.Blocks.AIR
                                            .defaultBlockState())
                                throw new IllegalStateException(
                                        "Unsound final AIR certificate " + pos);
                        for (var column : state.localTerrain().evidence()) {
                            var ch =
                                    region.terrainQuery()
                                            .getChunk(
                                                    Math.floorDiv(column.x(), 16),
                                                    Math.floorDiv(column.z(), 16),
                                                    ChunkStatus.TERRAIN,
                                                    true);
                            for (int i = 0; i < column.states().size(); i++) {
                                var p = new BlockPos(column.x(), column.minY() + i, column.z());
                                if (!column.states().get(i).equals(ch.getBlockState(p).toString()))
                                    throw new IllegalStateException(
                                            "Local/native TERRAIN column differs "
                                                    + p
                                                    + " local="
                                                    + column.states().get(i)
                                                    + " native="
                                                    + ch.getBlockState(p));
                            }
                            if (column.surfaceWg()
                                            != ch.getHeight(
                                                            Heightmap.Types.WORLD_SURFACE_WG,
                                                            column.x(),
                                                            column.z())
                                                    + 1
                                    || column.floorWg()
                                            != ch.getHeight(
                                                            Heightmap.Types.OCEAN_FLOOR_WG,
                                                            column.x(),
                                                            column.z())
                                                    + 1)
                                throw new IllegalStateException(
                                        "Local/native retained WG height differs " + column);
                        }
                        for (int z = f.z - 1; z <= f.z + 1; z++)
                            for (int x = f.x - 1; x <= f.x + 1; x++) {
                                var source = new ChunkPos(x, z);
                                if (source.equals(c.target())) continue;
                                region.beginWriter(source);
                                verifyRegionRandom(c, region, source);
                                c.generator()
                                        .applyBiomeDecoration(
                                                region,
                                                region.getChunk(x, z),
                                                b.structures().manager());
                            }
                    } finally {
                        NATIVE.set(false);
                    }
                    nativeState = region.metrics();
                }
            } else
                metrics =
                        NeighborTreeContributions.apply(
                                c,
                                b,
                                (slot, random) -> {
                                    // Additional draw exists only in this probe; next slot is
                                    // independently reseeded.
                                    TRACE.get()
                                            .add(
                                                    slot.key()
                                                            + ":"
                                                            + random.getCount()
                                                            + ":"
                                                            + random.nextLong());
                                });
            var after =
                    FeatureStableSnapshot.capture(
                            "minecraft:overworld", b.target(), host.registryAccess());
            if (!after.structureStartData().equals(before.structureStartData())
                    || !after.canonicalEntityNbt().equals(before.canonicalEntityNbt()))
                throw new IllegalStateException(
                        "Tree changed structure starts / standalone entities");
            if (WRITERS.get().get() != (nativeControl ? 9 : 1))
                throw new IllegalStateException("Hidden neighboring FEATURES");
            for (var entry : b.terrain().entrySet())
                if (!entry.getValue().getPos().equals(c.target())
                        && !neighbors
                                .get(entry.getKey())
                                .equals(
                                        FeatureStageSnapshot.capture(
                                                        "minecraft:overworld",
                                                        entry.getValue(),
                                                        host.registryAccess())
                                                .hash()))
                    throw new IllegalStateException("Tree mutated persistent neighbor");
            if (nativeControl) {
                var serial = (Map<?, ?>) evidence.get("serial-" + f.id);
                var trace =
                        TRACE.get().stream()
                                .map(
                                        s -> {
                                            var pieces = s.split(":", 2);
                                            return pieces[1];
                                        })
                                .toList();
                if (!trace.equals(serial.get("rng")))
                    throw new IllegalStateException(
                            "Tree RNG / source ordering mismatch "
                                    + f.id
                                    + " "
                                    + trace
                                    + " != "
                                    + serial.get("rng"));
            }
            Path output = root.resolve(run + "-" + f.id);
            Files.createDirectories(output);
            before.write(output.resolve("b.bin.gz"));
            after.write(output.resolve("a.bin.gz"));
            write(output.resolve("diff.json"), before.diff(after));
            var entry = new LinkedHashMap<String, Object>();
            entry.put("hash", after.hash());
            entry.put("bHash", before.hash());
            entry.put("fixture", f);
            entry.put("hostSeed", host.getSeed());
            entry.put("writers", WRITERS.get().get());
            entry.put("metrics", metrics == null ? nativeState : metrics);
            entry.put("oreMetrics", oreMetrics);
            entry.put(
                    "localPreparation",
                    preparation == null ? null : preparation.inputs().metrics());
            entry.put("baselineFullNeighbors", b.terrain().size() - 1);
            entry.put("rng", List.copyOf(TRACE.get()));
            entry.put("differingBlocks", before.diff(after).differingBlocks());
            entry.put("be", after.blockEntityCount());
            entry.put("pending", after.pendingBlockEntityNbtCount());
            entry.put("blockTicks", after.blockTickCount());
            entry.put("fluidTicks", after.fluidTickCount());
            entry.put("postProcessing", after.postProcessingCount());
            evidence.put(run + "-" + f.id, entry);
            write(output.resolve("summary.json"), entry);
            if (loaded != host.getChunkSource().getLoadedChunksCount()
                    || bt != host.getBlockTicks().count()
                    || ft != host.getFluidTicks().count()
                    || entities
                            != java.util.stream.StreamSupport.stream(
                                            host.getAllEntities().spliterator(), false)
                                    .count())
                throw new IllegalStateException("Tree physical side effect");
            return after.hash();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            ACTIVE.decrementAndGet();
        }
    }

    private static Map<String, Object> kernel(ServerLevel host, Path root, Fixture f)
            throws java.io.IOException {
        return kernel(host, root, f, false);
    }

    private static Map<String, Object> kernel(
            ServerLevel host, Path root, Fixture f, boolean conifers) throws java.io.IOException {
        // Actual registered native configurations, not handmade trees. Dedicated bee probability-1
        // feature is a kernel fixture, distinct from default biome decoration positive coverage.
        int cases = 0, hives = 0, outsideHives = 0;
        var hashes = new ArrayList<String>();
        var successes = new TreeMap<String, Integer>();
        var targetChanges = new TreeMap<String, Integer>();
        int loaded = host.getChunkSource().getLoadedChunksCount(),
                physicalBlocks = host.getBlockTicks().count(),
                physicalFluids = host.getFluidTicks().count();
        long physicalEntities =
                java.util.stream.StreamSupport.stream(host.getAllEntities().spliterator(), false)
                        .count();
        try (var left =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                host,
                                f.seed,
                                new ChunkPos(f.x, f.z));
                var right =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                host,
                                f.seed,
                                new ChunkPos(f.x, f.z))) {
            var a = StrictFeatureBaseline.generate(left, (r, t) -> {});
            var n = StrictFeatureBaseline.generate(right, (r, t) -> {});
            try (var compact = new TreeProjectionRegion(left, a);
                    var dense = fullControl(right, n)) {
                for (String key :
                        conifers
                                ? List.of("spruce", "pine", "fallen_spruce_tree")
                                : List.of(
                                        "super_birch_bees", "birch", "fallen_birch_tree", "oak")) {
                    Feature feature =
                            host.registryAccess()
                                    .lookupOrThrow(Registries.FEATURE)
                                    .getOrThrow(
                                            ResourceKey.create(
                                                    Registries.FEATURE,
                                                    Identifier.withDefaultNamespace(key)))
                                    .value();
                    for (int offset : new int[] {-1, 0, 15, 16})
                        for (int repeat = 0; repeat < 10; repeat++) {
                            int x = left.target().getMinBlockX() + offset,
                                    z =
                                            left.target().getMinBlockZ()
                                                    + new int[] {-1, 0, 15, 16, 2, 4, 6, 8, 10, 12}
                                                            [repeat];
                            var heightType =
                                    repeat % 2 == 0
                                            ? Heightmap.Types.OCEAN_FLOOR_WG
                                            : Heightmap.Types.OCEAN_FLOOR;
                            int y = compact.getHeight(heightType, x, z);
                            if (y != dense.getHeight(heightType, x, z))
                                throw new IllegalStateException(
                                        "Scratch height feedback differs "
                                                + heightType
                                                + " "
                                                + x
                                                + ","
                                                + z);
                            var pos = new BlockPos(x, y, z);
                            var source = ChunkPos.containing(pos);
                            compact.beginWriter(source);
                            dense.beginWriter(source);
                            WorldgenRandom
                                    ar = new WorldgenRandom(new XoroshiroRandomSource(cases)),
                                    nr = new WorldgenRandom(new XoroshiroRandomSource(cases));
                            var before =
                                    FeatureStableSnapshot.capture(
                                            "minecraft:overworld",
                                            a.target(),
                                            host.registryAccess());
                            boolean av = feature.place(compact, left.generator(), ar, pos),
                                    nv = feature.place(dense, right.generator(), nr, pos);
                            successes.merge(key, av ? 1 : 0, Integer::sum);
                            if (av != nv
                                    || ar.getCount() != nr.getCount()
                                    || ar.nextLong() != nr.nextLong())
                                throw new IllegalStateException(
                                        "Native tree outcome / RNG mismatch " + key + " " + pos);
                            var as =
                                    FeatureStableSnapshot.capture(
                                            "minecraft:overworld",
                                            a.target(),
                                            host.registryAccess());
                            var ns =
                                    FeatureStableSnapshot.capture(
                                            "minecraft:overworld",
                                            n.target(),
                                            host.registryAccess());
                            if (!as.hash().equals(ns.hash()))
                                throw new IllegalStateException(
                                        "Native tree side effect mismatch "
                                                + key
                                                + " "
                                                + pos
                                                + " "
                                                + as.diff(ns)
                                                + " "
                                                + as.deterministicMetadataDifference(ns));
                            cases++;
                            targetChanges.merge(
                                    key, before.diff(as).differingBlocks(), Integer::sum);
                            hashes.add(as.hash());
                            hives = Math.max(hives, as.blockEntityCount());
                            outsideHives = compact.metrics().outsideBlockEntityWrites();
                        }
                }
                if (!conifers && outsideHives == 0)
                    throw new IllegalStateException("No outside hive causal-state positive");
                if (conifers
                        && (successes.values().stream().anyMatch(count -> count == 0)
                                || targetChanges.values().stream().anyMatch(count -> count == 0)))
                    throw new IllegalStateException(
                            "Missing conifer kernel positive " + successes + " " + targetChanges);
                var finalState =
                        FeatureStableSnapshot.capture(
                                "minecraft:overworld", a.target(), host.registryAccess());
                String prefix = conifers ? "conifer-kernel" : "kernel";
                finalState.write(root.resolve(prefix + "-final.bin.gz"));
                FeatureStageSnapshot.capture(
                                "minecraft:overworld", a.target(), host.registryAccess())
                        .write(root.resolve(prefix + "-final-raw.bin.gz"));
                expectRejected(() -> compact.getLevel());
                expectRejected(
                        () ->
                                compact.getChunk(left.target().x(), left.target().z())
                                        .setBlockState(
                                                new BlockPos(
                                                        left.target().getMinBlockX() - 1,
                                                        80,
                                                        left.target().getMinBlockZ()),
                                                net.minecraft.world.level.block.Blocks.GOLD_BLOCK
                                                        .defaultBlockState(),
                                                19));
                expectRejected(() -> compact.getChunk(left.target().x() + 3, left.target().z()));
                if (loaded != host.getChunkSource().getLoadedChunksCount()
                        || physicalBlocks != host.getBlockTicks().count()
                        || physicalFluids != host.getFluidTicks().count()
                        || physicalEntities
                                != java.util.stream.StreamSupport.stream(
                                                host.getAllEntities().spliterator(), false)
                                        .count())
                    throw new IllegalStateException("Tree kernel polluted physical state");
                var proof = new LinkedHashMap<String, Object>();
                proof.put("status", "PASS");
                proof.put("cases", cases);
                proof.put("successes", successes);
                proof.put("targetChanges", targetChanges);
                proof.put("maxTargetBlockEntities", hives);
                proof.put("outsideHiveOperations", outsideHives);
                proof.put("sparseState", compact.metrics());
                proof.put("hashes", hashes);
                proof.put("blockTicks", finalState.blockTickCount());
                proof.put("fluidTicks", finalState.fluidTickCount());
                proof.put("postProcessing", finalState.postProcessingCount());
                proof.put("hiveNbt", finalState.blockEntityNbt());
                compact.close();
                expectRejected(() -> compact.getChunk(left.target().x(), left.target().z()));
                write(root.resolve(prefix + ".json"), proof);
                return proof;
            }
        }
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException
                | IllegalStateException
                | UnsupportedOperationException expected) {
            return;
        }
        throw new IllegalStateException("Missing tree capability/lifecycle rejection");
    }

    private static final class InjectedFailure extends RuntimeException {}

    private static void verifyFailureCleanup(ServerLevel host, Fixture f) {
        boolean propagated = false;
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        host,
                        f.seed,
                        new ChunkPos(f.x, f.z))) {
            var b = StrictFeatureBaseline.generate(context, (r, t) -> {});
            NeighborTreeContributions.apply(
                    context,
                    b,
                    (slot, random) -> {
                        throw new InjectedFailure();
                    });
        } catch (InjectedFailure expected) {
            propagated = true;
        }
        if (!propagated || GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("Tree failure swallowed or context leaked");
    }

    private static void verifyRegionRandom(
            IsolatedGenerationContext c, TreeProjectionRegion tree, ChunkPos source) {
        var chunk = tree.getChunk(source.x(), source.z());
        net.minecraft.server.level.GenerationChunkHolder holder =
                new net.minecraft.server.level.GenerationChunkHolder(source) {
                    @Override
                    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                        return chunk;
                    }

                    @Override
                    protected void addSaveDependency(CompletableFuture<?> future) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int getTicketLevel() {
                        return 0;
                    }

                    @Override
                    public int getQueueLevel() {
                        return 0;
                    }
                };
        var cache =
                net.minecraft.util.StaticCache2D.create(
                        source.x(), source.z(), 0, (x, z) -> holder);
        GenerationContextRegistry.bind(cache, c);
        var original =
                new net.minecraft.server.level.WorldGenRegion(
                        c.hostLevel(),
                        cache,
                        net.minecraft.world.level.chunk.status.ChunkPyramid.GENERATION_PYRAMID
                                .getStepTo(ChunkStatus.FEATURES),
                        chunk);
        GenerationContextRegistry.bind(original, c);
        for (int i = 0; i < 8; i++)
            if (original.getRandom().nextLong() != tree.getRandom().nextLong())
                throw new IllegalStateException("Source region positional RNG differs");
        tree.beginWriter(source);
    }

    private static TreeProjectionRegion fullControl(
            IsolatedGenerationContext c, StrictFeatureBaseline.Result baseline) {
        return new TreeProjectionRegion(
                c,
                baseline,
                base -> {
                    var copy =
                            new ProtoChunk(
                                    base.getPos(),
                                    UpgradeData.EMPTY,
                                    Arrays.stream(base.getSections())
                                            .map(LevelChunkSection::copy)
                                            .toArray(LevelChunkSection[]::new),
                                    new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                                    new net.minecraft.world.ticks.ProtoChunkTicks<>(),
                                    c.hostLevel(),
                                    c.hostLevel().palettedContainerFactory(),
                                    null);
                    copy.setPersistedStatus(ChunkStatus.TERRAIN);
                    // WG maps must be copied before any tree writes. Re-priming later changes
                    // placement.
                    for (var type : Heightmap.Types.values())
                        if (base.hasPrimedHeightmap(type)) {
                            var values =
                                    new net.minecraft.util.SimpleBitStorage(
                                            net.minecraft.util.Mth.ceillog2(base.getHeight() + 1),
                                            256);
                            for (int z = 0; z < 16; z++)
                                for (int x = 0; x < 16; x++)
                                    values.set(
                                            x + z * 16,
                                            base.getHeight(type, x, z) + 1 - base.getMinY());
                            copy.setHeightmap(type, values.getRaw().clone());
                        }
                    return copy;
                });
    }
}
