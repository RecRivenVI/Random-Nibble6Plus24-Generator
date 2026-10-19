package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/**
 * Controlled legal native supplier order; never changes the production executor or climate state.
 */
public final class RingContinuationProbe {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    private RingContinuationProbe() {}

    public static Executor executor(Executor original) {
        var frame = ACTIVE.get();
        if (frame == null) return original;
        frame.executorSelections++;
        return task ->
                frame.worker.execute(
                        () -> {
                            frame.executed.incrementAndGet();
                            task.run();
                        });
    }

    public static void forked(RandomSource root) {
        var frame = ACTIVE.get();
        if (frame == null) return;
        if (frame.root != null && frame.root != root)
            throw new IllegalStateException("Native ring changed its root random source");
        frame.root = root;
        frame.forks++;
    }

    private static final class Frame {
        final ExecutorService worker;
        final AtomicInteger executed = new AtomicInteger();
        int executorSelections;
        int forks;
        RandomSource root;

        Frame(ExecutorService worker) {
            this.worker = worker;
        }
    }

    private record Observation(
            List<ChunkPos> positions,
            Object exit,
            long next,
            long following,
            int tasks,
            ConcentricRingScope.Metrics metrics) {}

    public static JsonObject run(MinecraftServer server, String inputHash) throws Exception {
        var report = new JsonObject();
        report.addProperty("schema", 1);
        report.addProperty("scope", "BOUNDED_NATIVE_RING_AND_CONTINUATION_NOT_WORLDGEN_PARITY");
        report.addProperty("inputHash", inputHash);
        report.addProperty("pid", ProcessHandle.current().pid());
        report.addProperty("processors", Runtime.getRuntime().availableProcessors());
        report.addProperty(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .toString());
        var level = server.overworld();
        var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        var definitions = level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
        var noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
        var source = generator.getBiomeSource();
        var climate = NativeBiomeSearchState.from(source);
        require(
                climate != null && climate.hasTree(),
                "Missing actual native Overworld climate index");
        var loaded = new java.util.TreeMap<String, Integer>();
        var dimensions = new JsonArray();
        for (var dimension : server.getAllLevels()) {
            loaded.put(
                    dimension.dimension().identifier().toString(),
                    dimension.getChunkSource().getLoadedChunksCount());
            var gen = (NoiseBasedChunkGenerator) dimension.getChunkSource().getGenerator();
            var random = RandomState.create(noises, 123456789L, gen.generatorSettings().value());
            var state = gen.createState(definitions, random, 123456789L);
            var row = new JsonObject();
            row.addProperty("dimension", dimension.dimension().identifier().toString());
            row.addProperty(
                    "nativeConcentricSets",
                    state.possibleStructureSets().stream()
                            .filter(
                                    set ->
                                            set.value().placement()
                                                    instanceof ConcentricRingsStructurePlacement)
                            .count());
            dimensions.add(row);
        }
        report.add("dimensions", dimensions);
        var rows = new JsonArray();
        int outputDifferences = 0, continuationDifferences = 0, cursorDifferences = 0;
        var worker =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            var thread = new Thread(runnable, "mosaic-native-ring-component");
                            thread.setDaemon(true);
                            return thread;
                        });
        try {
            for (long seed : new long[] {0, 1, -1, 123456789}) {
                var random =
                        RandomState.create(noises, seed, generator.generatorSettings().value());
                for (int direction : new int[] {-1, 1}) {
                    Object entry =
                            worker.submit(
                                            () -> {
                                                climate.replay(null);
                                                source.createUncachedResolver(random)
                                                        .getNoiseBiome(
                                                                direction * 512,
                                                                8,
                                                                -direction * 512);
                                                return climate.capture();
                                            })
                                    .get(30, TimeUnit.SECONDS);
                    require(entry != null, "Native warmup did not populate the worker index");
                    var eager =
                            observe(
                                    worker,
                                    climate,
                                    generator.createState(definitions, random, seed),
                                    null,
                                    entry);
                    require(
                            eager.positions().size() == 128 && eager.tasks() == 128,
                            "Native full ring was not evaluated");
                    for (var target : List.of(ChunkPos.ZERO, eager.positions().getFirst())) {
                        var state = generator.createState(definitions, random, seed);
                        var scope =
                                ConcentricRingScope.forV2Target(
                                        target, seed, source, state.possibleStructureSets());
                        require(
                                scope.metrics().fallback() == ConcentricRingScope.Fallback.NONE,
                                "Unexpected finite native fallback");
                        var lazy = observe(worker, climate, state, scope, entry);
                        var expected =
                                eager.positions().stream()
                                        .filter(pos -> scope.contains(pos.x(), pos.z()))
                                        .toList();
                        boolean positionsEqual = expected.equals(lazy.positions());
                        boolean rngEqual =
                                eager.next() == lazy.next()
                                        && eager.following() == lazy.following();
                        var row = new JsonObject();
                        row.addProperty("seed", Long.toString(seed));
                        row.addProperty("prefixDirection", direction);
                        row.addProperty("target", target.toString());
                        row.addProperty("sameNativeEntry", true);
                        row.addProperty("positionsEqual", positionsEqual);
                        row.addProperty("rootRandomContinuationEqual", rngEqual);
                        row.addProperty("rootNext", Long.toHexString(eager.next()));
                        row.addProperty("rootFollowing", Long.toHexString(eager.following()));
                        row.addProperty("nativeTasks", eager.tasks());
                        row.addProperty("lazyTasks", lazy.tasks());
                        row.addProperty("nativeExitSameIdentity", eager.exit() == lazy.exit());
                        if (eager.exit() != lazy.exit()) cursorDifferences++;
                        var continuation =
                                worker.submit(
                                                () ->
                                                        compareReads(
                                                                source,
                                                                random,
                                                                climate,
                                                                target,
                                                                eager.exit(),
                                                                lazy.exit()))
                                        .get(30, TimeUnit.SECONDS);
                        row.add("continuation", continuation);
                        if (!positionsEqual || !rngEqual) outputDifferences++;
                        continuationDifferences += continuation.get("differentBiomes").getAsInt();
                        rows.add(row);
                    }
                }
            }
        } finally {
            ACTIVE.remove();
            worker.shutdown();
            require(
                    worker.awaitTermination(30, TimeUnit.SECONDS),
                    "Native component worker leaked");
        }
        for (var dimension : server.getAllLevels())
            require(
                    loaded.get(dimension.dimension().identifier().toString())
                            == dimension.getChunkSource().getLoadedChunksCount(),
                    "Ring component loaded physical chunks");
        require(rows.size() == 16, "Incomplete native ring matrix");
        report.add("cases", rows);
        report.addProperty("positionOrRandomDifferences", outputDifferences);
        report.addProperty("continuationBiomeDifferences", continuationDifferences);
        report.addProperty("differentWorkerExits", cursorDifferences);
        report.addProperty("executorTerminated", worker.isTerminated());
        report.addProperty("loadedUnchanged", true);
        report.addProperty(
                "status",
                outputDifferences == 0 && continuationDifferences == 0
                        ? "BOUNDED_NATIVE_COMPONENT_PASS"
                        : "COUNTEREXAMPLE");
        return report;
    }

    private static Observation observe(
            ExecutorService worker,
            NativeBiomeSearchState climate,
            ChunkGeneratorStructureState state,
            ConcentricRingScope scope,
            Object entry)
            throws Exception {
        worker.submit(() -> climate.replay(entry)).get(30, TimeUnit.SECONDS);
        if (scope != null)
            ((ConcentricRingStateAccess) state).randomnibble6plus24generator$setRingScope(scope);
        var placement =
                (ConcentricRingsStructurePlacement)
                        state.possibleStructureSets().stream()
                                .filter(
                                        set ->
                                                set.value().placement()
                                                        instanceof
                                                        ConcentricRingsStructurePlacement)
                                .findFirst()
                                .orElseThrow()
                                .value()
                                .placement();
        var frame = new Frame(worker);
        List<ChunkPos> positions;
        ACTIVE.set(frame);
        try {
            positions = state.getRingPositionsFor(placement);
        } finally {
            ACTIVE.remove();
        }
        require(
                frame.forks == 128 && frame.executorSelections == 128 && frame.root != null,
                "Actual native ring fork/executor observation was not hit");
        Object exit = worker.submit(climate::capture).get(30, TimeUnit.SECONDS);
        var metrics = scope == null ? null : scope.metrics();
        require(
                scope == null || metrics.biomeSearches() == frame.executed.get(),
                "Lazy task count differs from actual executor calls");
        return new Observation(
                List.copyOf(positions),
                exit,
                frame.root.nextLong(),
                frame.root.nextLong(),
                frame.executed.get(),
                metrics);
    }

    private static JsonObject compareReads(
            BiomeSource source,
            RandomState random,
            NativeBiomeSearchState climate,
            ChunkPos target,
            Object eager,
            Object lazy) {
        var resolver = source.createUncachedResolver(random);
        var row = new JsonObject();
        var keys = new java.util.ArrayList<String>();
        int samples = 0, differences = 0, sameExit = 0;
        for (int y : new int[] {-16, 0, 16, 32, 64})
            for (int z = -8; z <= 8; z++)
                for (int x = -8; x <= 8; x++) {
                    int qx = target.x() * 4 + x, qz = target.z() * 4 + z;
                    climate.replay(eager);
                    var first = resolver.getNoiseBiome(qx, y, qz);
                    Object firstExit = climate.capture();
                    climate.replay(lazy);
                    var second = resolver.getNoiseBiome(qx, y, qz);
                    Object secondExit = climate.capture();
                    if (firstExit == secondExit) sameExit++;
                    String a = first.unwrapKey().orElseThrow().identifier().toString();
                    String b = second.unwrapKey().orElseThrow().identifier().toString();
                    keys.add(qx + "," + y + "," + qz + ":" + a + "/" + b);
                    if (!a.equals(b)) {
                        differences++;
                        if (!row.has("firstDifference")) {
                            var diff = new JsonObject();
                            diff.addProperty("quart", qx + "," + y + "," + qz);
                            diff.addProperty("native", a);
                            diff.addProperty("lazy", b);
                            climate.replay(lazy);
                            String swappedLazy =
                                    resolver.getNoiseBiome(qx, y, qz)
                                            .unwrapKey()
                                            .orElseThrow()
                                            .identifier()
                                            .toString();
                            climate.replay(eager);
                            String swappedNative =
                                    resolver.getNoiseBiome(qx, y, qz)
                                            .unwrapKey()
                                            .orElseThrow()
                                            .identifier()
                                            .toString();
                            require(
                                    a.equals(swappedNative) && b.equals(swappedLazy),
                                    "Native exit intervention did not reproduce bidirectionally");
                            diff.addProperty("nativeExitReplayReproduced", true);
                            diff.addProperty("lazyExitReplayReproduced", true);
                            row.add("firstDifference", diff);
                        }
                    }
                    samples++;
                }
        row.addProperty("samples", samples);
        row.addProperty("differentBiomes", differences);
        row.addProperty("sameExitAfterRead", sameExit);
        try {
            row.addProperty(
                    "readHash",
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    String.join("\n", keys)
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_8))));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        return row;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
