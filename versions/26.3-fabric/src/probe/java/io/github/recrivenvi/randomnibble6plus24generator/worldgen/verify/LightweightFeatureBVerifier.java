package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** Every actual neighbor read is checked against fresh original B, including bulk ore reads. */
final class LightweightFeatureBVerifier {
    private static final AtomicInteger ACTIVE = new AtomicInteger(), PEAK = new AtomicInteger();

    private LightweightFeatureBVerifier() {}

    private record Observation(FeatureStableSnapshot snapshot, ListTag raw) {}

    private static LightweightFeatureBaseline.Result generateLocal(
            IsolatedGenerationContext context,
            LocalFeatureInputs.Observer observer,
            java.util.function.BiConsumer<StrictFeatureRegion, LocalFeatureInputs>
                    beforeDecoration) {
        return Boolean.getBoolean("featureb.structureReuse")
                ? LightweightFeatureBaseline.generateWithStructureReuse(
                        context, observer, beforeDecoration)
                : LightweightFeatureBaseline.generate(context, observer, beforeDecoration);
    }

    static void run(MinecraftServer server, Path root) throws Exception {
        if (Boolean.getBoolean("featureb.structureReuse"))
            write(root.resolve("structure-locks.json"), StructureLockVerifier.run(server));
        var evidence = new ConcurrentHashMap<String, Object>();
        var fixtures = new ArrayList<>(FeatureBVerifier.FIXTURES);
        fixtures.add(new FeatureBVerifier.Fixture("birch", "overworld", 0, -12, -12));
        fixtures.add(
                new FeatureBVerifier.Fixture(
                        "extension-fallback", "overworld", -4896871951352006295L, -1, 1));
        fixtures.add(
                new FeatureBVerifier.Fixture(
                        "carved-surface-fallback",
                        "overworld",
                        -195955039870900853L,
                        -20000,
                        30000));
        var serial = new HashMap<String, Observation>();
        for (var f : fixtures) serial.put(f.id(), generate(server, root, f, "serial", evidence));
        var representative =
                List.of(
                        fixtures.get(0),
                        fixtures.get(14),
                        fixtures.get(1),
                        fixtures.get(2),
                        fixtures.get(4),
                        fixtures.get(6),
                        fixtures.get(8),
                        fixtures.get(9),
                        fixtures.get(11),
                        fixtures.get(15),
                        fixtures.get(16),
                        fixtures.get(17));
        for (int i = representative.size() - 1; i >= 0; i--) {
            var f = representative.get(i);
            equal(
                    serial.get(f.id()),
                    generate(server, root, f, "reverse", evidence),
                    f.id(),
                    root.resolve("reverse-" + f.id()).resolve("repeat-comparison.json"));
        }
        var executor = Executors.newFixedThreadPool(5);
        try {
            var futures =
                    representative.stream()
                            .map(
                                    f ->
                                            CompletableFuture.runAsync(
                                                    () ->
                                                            equal(
                                                                    serial.get(f.id()),
                                                                    generate(
                                                                            server,
                                                                            root,
                                                                            f,
                                                                            "parallel",
                                                                            evidence),
                                                                    f.id(),
                                                                    root.resolve(
                                                                                    "parallel-"
                                                                                            + f
                                                                                                    .id())
                                                                            .resolve(
                                                                                    "repeat-comparison.json")),
                                                    executor))
                            .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } finally {
            executor.shutdown();
        }
        var f = fixtures.get(1);
        var physical =
                server.overworld()
                        .getChunkSource()
                        .getChunk(f.x(), f.z(), ChunkStatus.TERRAIN, true);
        for (String mode : List.of("generated-host", "mutated-host")) {
            if (mode.equals("mutated-host"))
                physical.setBlockState(
                        physical.getPos().getWorldPosition().offset(3, 20, 3),
                        net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(),
                        0);
            String before =
                    FeatureStageSnapshot.capture(
                                    "minecraft:overworld", physical, server.registryAccess())
                            .hash();
            equal(
                    serial.get(f.id()),
                    generate(server, root, f, mode, evidence),
                    f.id(),
                    root.resolve(mode + "-" + f.id()).resolve("repeat-comparison.json"));
            if (!before.equals(
                    FeatureStageSnapshot.capture(
                                    "minecraft:overworld", physical, server.registryAccess())
                            .hash()))
                throw new IllegalStateException("B physical frontier pollution");
        }
        var sentinel = new IllegalStateException("Injected lightweight B failure");
        var failedInput = new AtomicReference<LocalFeatureInputs>();
        try (var c =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        server.overworld(),
                        0,
                        new ChunkPos(0, 0))) {
            try {
                generateLocal(
                        c,
                        new LocalFeatureInputs.Observer() {},
                        (r, i) -> {
                            failedInput.set(i);
                            throw sentinel;
                        });
                throw new IllegalStateException("B swallowed failure");
            } catch (IllegalStateException e) {
                if (e != sentinel) throw e;
            }
        }
        if (!failedInput.get().isClosed())
            throw new IllegalStateException("Failed B input retained");
        if (Boolean.getBoolean("featureb.structureReuse")) {
            var reuse = failedInput.get().structureReuseMetrics().orElseThrow();
            if (!reuse.closed() || reuse.resident() != 0)
                throw new IllegalStateException("Failed B source graph retained");
            var biomes = failedInput.get().biomeReuseCounters().orElseThrow();
            if (!biomes.closed() || biomes.resident() != 0)
                throw new IllegalStateException("Failed B biome input scope retained");
        }
        verifyCapabilities(server);
        if (Boolean.getBoolean("featureb.structureReuse")) verifyBiomeReuseCapabilities(server);
        runMonsterRooms(server, root);
        if (GenerationContextRegistry.bindingCount() != 0 || ACTIVE.get() != 0 || PEAK.get() < 5)
            throw new IllegalStateException("B cleanup/concurrent coverage missing");
        if (root.getFileName().toString().equals("host_1")) {
            for (var name : new TreeSet<>(evidence.keySet())) {
                var first = root.resolveSibling("host_0").resolve(name);
                var second = root.resolve(name);
                equal(
                        read(first),
                        read(second),
                        name.endsWith("-end-city") ? "end-city" : name,
                        second.resolve("cross-host.json"));
            }
        }
        var summary =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "status",
                                "PASS",
                                "fixtures",
                                fixtures.size(),
                                "repeatComparisons",
                                representative.size() * 2,
                                "bindings",
                                0,
                                "peakTargets",
                                PEAK.get(),
                                "evidence",
                                evidence,
                                "pid",
                                ProcessHandle.current().pid(),
                                "proofVersion",
                                1,
                                "exceptionCleanup",
                                true));
        summary.put("entityAcceptanceVersion", 1);
        summary.put("inputHash", System.getProperty("featureb.inputHash", "UNBOUND"));
        summary.put(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize()
                        .toString());
        summary.put("hostSeed", Long.toString(server.overworld().getSeed()));
        write(root.resolve("summary.json"), summary);
    }

    private static void verifyBiomeReuseCapabilities(MinecraftServer server) {
        var position = ChunkPos.ZERO;
        try (var owner =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, server.overworld(), 0, position)) {
            var inputs =
                    new io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain
                            .TerrainBiomeInputs(owner);
            var prepared = inputs.prepare(owner, position);
            try (var foreignSeed =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    server.overworld(),
                                    1,
                                    position);
                    var foreignDimension =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    server.getLevel(net.minecraft.world.level.Level.NETHER),
                                    0,
                                    position)) {
                denied(() -> inputs.requireCompatible(foreignSeed));
                denied(() -> inputs.requireCompatible(foreignDimension));
                denied(() -> inputs.palette(new ChunkPos(3, 0)));
            }
            inputs.close();
            if (!inputs.counters().closed() || inputs.counters().resident() != 0)
                throw new IllegalStateException("Stored biome scope retained after close");
            denied(() -> inputs.palette(position));
            denied(() -> prepared.manager().getNoiseBiomeAtQuart(0, 0, 0));
        }
    }

    private static void denied(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return;
        }
        throw new IllegalStateException("Foreign/closed stored biome input was accepted");
    }

    static void runMonsterRooms(MinecraftServer server, Path root) throws Exception {
        var evidence = new LinkedHashMap<String, Object>();
        for (var f : monsterFixtures()) generate(server, root, f, "monster-room", evidence);
        var events = MonsterRoomProbe.drain();
        for (var f : monsterFixtures()) {
            var matching =
                    events.stream()
                            .filter(
                                    e ->
                                            e.get("writer")
                                                            .equals(
                                                                    new ChunkPos(f.x(), f.z())
                                                                            .toString())
                                                    && e.get("seed")
                                                            .equals(Long.toString(f.seed())))
                            .toList();
            if (matching.size() != 2
                    || matching.stream()
                            .anyMatch(
                                    e ->
                                            !Boolean.TRUE.equals(e.get("insideWriter"))
                                                    || !Boolean.TRUE.equals(e.get("protected"))
                                                    || !e.get("state")
                                                            .toString()
                                                            .startsWith("Block{minecraft:chest}")
                                                    || !e.get("entity")
                                                            .equals(
                                                                    "net.minecraft.world.level.block.entity.ChestBlockEntity")))
                throw new IllegalStateException(
                        "Monster-room warning changed or a target BlockEntity was lost: "
                                + f.id()
                                + matching);
        }
        write(
                root.resolve("monster-rooms.json"),
                Map.of(
                        "fixtures",
                        evidence,
                        "events",
                        events,
                        "traces",
                        MonsterRoomProbe.drainTraces()));
    }

    static List<FeatureBVerifier.Fixture> monsterFixtures() {
        var resolver =
                new io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed
                        .MosaicSeedResolver(
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile
                                .MosaicWorldProfile.current());
        var result = new ArrayList<FeatureBVerifier.Fixture>();
        for (var pos :
                List.of(
                        new ChunkPos(645, -623),
                        new ChunkPos(621, -647),
                        new ChunkPos(594, -603))) {
            result.add(
                    new FeatureBVerifier.Fixture(
                            "monster-" + pos.x() + "_" + pos.z(),
                            "overworld",
                            resolver.resolveLocalWorldSeed(
                                    246813580, net.minecraft.world.level.Level.OVERWORLD, pos),
                            pos.x(),
                            pos.z()));
        }
        return result;
    }

    private static void verifyCapabilities(MinecraftServer server) {
        try (var c =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                        server.overworld(),
                        0,
                        new ChunkPos(0, 0))) {
            var target =
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain
                            .LightweightTerrainGenerator.generate(
                                    c,
                                    io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .structure.CompactStructureInputs.resolve(c),
                                    (s, ch) -> {})
                            .target();
            var drops = new TreeMap<String, Long>();
            LocalFeatureInputs inputs;
            ChunkAccess neighbor;
            try (var local =
                    new LocalFeatureInputs(
                            c,
                            target,
                            api -> drops.merge(api, 1L, Long::sum),
                            new LocalFeatureInputs.Observer() {})) {
                inputs = local;
                neighbor = local.chunks().get(new ChunkPos(1, 0).pack());
                var p = new BlockPos(16, 30, 0);
                var state = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
                neighbor.setBlockState(p, state, 0);
                neighbor.setBlockEntity(
                        new net.minecraft.world.level.block.entity.ChestBlockEntity(p, state));
                var nbt = new net.minecraft.nbt.CompoundTag();
                nbt.putString("id", "DUMMY");
                nbt.putInt("x", 16);
                nbt.putInt("y", 30);
                nbt.putInt("z", 0);
                neighbor.setBlockEntityNbt(nbt);
                neighbor.removeBlockEntity(p);
                neighbor.markPosForPostProcessing(p);
                neighbor.addPackedPostProcess(
                        new it.unimi.dsi.fastutil.shorts.ShortArrayList(new short[] {1}), 0);
                neighbor.getBlockTicks()
                        .schedule(
                                new net.minecraft.world.ticks.ScheduledTick<>(
                                        net.minecraft.world.level.block.Blocks.STONE, p, 0, 0));
                neighbor.getFluidTicks()
                        .schedule(
                                new net.minecraft.world.ticks.ScheduledTick<>(
                                        net.minecraft.world.level.material.Fluids.WATER, p, 0, 0));
                if (local.metrics().fullNeighbors() != 0 || drops.size() != 8)
                    throw new IllegalStateException(
                            "Rejected B writes forced terrain/mutated state");
                try {
                    neighbor.getBlockState(new BlockPos(0, 0, 0));
                    throw new IllegalStateException("Foreign coordinate accepted");
                } catch (IllegalArgumentException expected) {
                }
                // Deliberate unaudited read must produce an exact full fallback, not an empty
                // tick container or a guessed height. The original B comparison covers this data.
                neighbor.getBlockTicks().count();
                if (local.metrics().fullNeighbors() != 1
                        || !local.metrics().fallbackReasons().containsValue("tick container read"))
                    throw new IllegalStateException("Missing explicit exact B fallback");
                var old = neighbor.getBlockState(p);
                var section = neighbor.getSection(neighbor.getSectionIndex(p.getY()));
                if (!old.equals(
                                section.setBlockState(
                                        0,
                                        p.getY() & 15,
                                        0,
                                        net.minecraft.world.level.block.Blocks.GOLD_BLOCK
                                                .defaultBlockState(),
                                        false))
                        || !old.equals(neighbor.getBlockState(p)))
                    throw new IllegalStateException("B neighbor section write leaked");
                var copied = section.getStates();
                copied.getAndSetUnchecked(
                        0,
                        p.getY() & 15,
                        0,
                        net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState());
                if (!old.equals(neighbor.getBlockState(p)))
                    throw new IllegalStateException("B neighbor palette write leaked");
            }
            if (!inputs.isClosed()) throw new IllegalStateException("B query resource retained");
            try {
                neighbor.getBlockState(new BlockPos(16, 0, 0));
                throw new IllegalStateException("Closed B query accepted");
            } catch (IllegalStateException expected) {
                if (expected.getMessage().equals("Closed B query accepted")) throw expected;
            }
        }
    }

    private static Observation generate(
            MinecraftServer server,
            Path root,
            FeatureBVerifier.Fixture f,
            String run,
            Map<String, Object> evidence) {
        var host =
                Objects.requireNonNull(
                        server.getLevel(
                                ResourceKey.create(
                                        Registries.DIMENSION,
                                        Identifier.withDefaultNamespace(f.dimension()))));
        String dimension = "minecraft:" + f.dimension();
        int loaded = host.getChunkSource().getLoadedChunksCount(),
                blockTicks = host.getBlockTicks().count(),
                fluidTicks = host.getFluidTicks().count();
        long entities =
                java.util.stream.StreamSupport.stream(host.getAllEntities().spliterator(), false)
                        .count();
        PEAK.accumulateAndGet(ACTIVE.incrementAndGet(), Math::max);
        var traces = new ArrayList<Object>();
        try {
            StrictFeatureBaseline.Result oracle;
            FeatureStableSnapshot expected;
            Map<String, Object> oldTrace;
            long oldStart = System.nanoTime(), oldNanos;
            try (var c =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC,
                            host,
                            f.seed(),
                            new ChunkPos(f.x(), f.z()))) {
                var count = new AtomicInteger();
                oracle =
                        StrictFeatureBaseline.generate(
                                c,
                                (r, t) -> {
                                    FeatureBVerifier.registerWriter(r, count);
                                    traces.add(r);
                                });
                oldNanos = System.nanoTime() - oldStart;
                if (count.get() != 1) throw new IllegalStateException("Original B writer count");
                expected =
                        FeatureStableSnapshot.capture(
                                dimension, oracle.target(), host.registryAccess());
                oldTrace =
                        trace(
                                c,
                                oracle.region(),
                                oracle.rejected(),
                                oracle.structures(),
                                oracle.terrain());
            }
            Map<Long, String> immutableBefore = new TreeMap<>();
            AtomicInteger checkedBlocks = new AtomicInteger(),
                    checkedHeights = new AtomicInteger(),
                    checkedBiomes = new AtomicInteger();
            LightweightFeatureBaseline.Result result;
            FeatureStableSnapshot actual;
            Map<String, Object> newTrace;
            var observedBiomes = new AtomicReference<Map<Long, SortedSet<String>>>();
            long newStart = System.nanoTime(), newNanos;
            try (var c =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC,
                            host,
                            f.seed(),
                            new ChunkPos(f.x(), f.z()))) {
                var count = new AtomicInteger();
                result =
                        generateLocal(
                                c,
                                new LocalFeatureInputs.Observer() {
                                    @Override
                                    public void block(
                                            BlockPos p,
                                            net.minecraft.world.level.block.state.BlockState
                                                    state) {
                                        var original =
                                                oracle.terrain()
                                                        .get(ChunkPos.containing(p).pack())
                                                        .getBlockState(p);
                                        if (!original.equals(state))
                                            throw new IllegalStateException(
                                                    "B local block differs "
                                                            + f.id()
                                                            + " "
                                                            + p
                                                            + " "
                                                            + original
                                                            + " / "
                                                            + state);
                                        checkedBlocks.incrementAndGet();
                                    }

                                    @Override
                                    public void height(
                                            ChunkPos p,
                                            Heightmap.Types t,
                                            int x,
                                            int z,
                                            int value) {
                                        int original =
                                                oracle.terrain().get(p.pack()).getHeight(t, x, z);
                                        if (original != value)
                                            throw new IllegalStateException(
                                                    "B local height differs "
                                                            + f.id()
                                                            + " "
                                                            + p
                                                            + " "
                                                            + x
                                                            + ","
                                                            + z
                                                            + " "
                                                            + t
                                                            + " "
                                                            + original
                                                            + "/"
                                                            + value);
                                        checkedHeights.incrementAndGet();
                                    }

                                    @Override
                                    public void biome(
                                            int x,
                                            int y,
                                            int z,
                                            Holder<net.minecraft.world.level.biome.Biome> value) {
                                        var original =
                                                oracle.terrain()
                                                        .get(
                                                                new ChunkPos(
                                                                                QuartPos.toSection(
                                                                                        x),
                                                                                QuartPos.toSection(
                                                                                        z))
                                                                        .pack())
                                                        .getNoiseBiome(x, y, z);
                                        if (!original.equals(value))
                                            throw new IllegalStateException(
                                                    "B stored biome differs "
                                                            + f.id()
                                                            + " "
                                                            + x
                                                            + ","
                                                            + y
                                                            + ","
                                                            + z);
                                        checkedBiomes.incrementAndGet();
                                    }

                                    @Override
                                    public void fallback(ProtoChunk chunk, String reason) {
                                        String hash =
                                                FeatureStageSnapshot.capture(
                                                                dimension,
                                                                chunk,
                                                                host.registryAccess())
                                                        .hash();
                                        if (!hash.equals(
                                                FeatureStageSnapshot.capture(
                                                                dimension,
                                                                oracle.terrain()
                                                                        .get(chunk.getPos().pack()),
                                                                host.registryAccess())
                                                        .hash()))
                                            throw new IllegalStateException(
                                                    "B fallback differs " + f.id() + " " + reason);
                                        immutableBefore.put(chunk.getPos().pack(), hash);
                                    }
                                },
                                (r, i) -> {
                                    FeatureBVerifier.registerWriter(r, count);
                                    traces.add(r);
                                    observedBiomes.set(biomes(i.chunks()));
                                });
                newNanos = System.nanoTime() - newStart;
                if (Boolean.getBoolean("featureb.structureReuse")) {
                    var reuse = result.inputs().structureReuseMetrics().orElseThrow();
                    if (!reuse.closed()
                            || reuse.resident() != 0
                            || reuse.peakResident() > 361
                            || reuse.queries() != reuse.hits() + reuse.evaluations())
                        throw new IllegalStateException(
                                "B structure source scope leaked/unbounded");
                    var biomes = result.inputs().biomeReuseCounters().orElseThrow();
                    if (!biomes.closed()
                            || biomes.resident() != 0
                            || biomes.palettes() > 25
                            || biomes.nativeBuilds() != biomes.palettes()
                            || biomes.targetCopies() != result.inputs().counters().fullNeighbors())
                        throw new IllegalStateException("B stored biome reuse ownership/counts");
                }
                if (count.get() != 1 || !result.inputs().isClosed())
                    throw new IllegalStateException("B writer/resources");
                actual =
                        FeatureStableSnapshot.capture(
                                dimension, result.target(), host.registryAccess());
                var output = root.resolve(run + "-" + f.id());
                Files.createDirectories(output);
                actual.write(output.resolve("features.bin.gz"));
                expected.write(output.resolve("original.bin.gz"));
                writeRaw(output.resolve("original-entities.nbt"), raw(oracle.target()));
                writeRaw(output.resolve("entities.nbt"), raw(result.target()));
                equal(
                        new Observation(expected, raw(oracle.target())),
                        new Observation(actual, raw(result.target())),
                        f.id(),
                        output.resolve("comparison.json"));
                newTrace =
                        new LinkedHashMap<>(
                                trace(
                                        c,
                                        result.region(),
                                        result.rejected(),
                                        result.structures(),
                                        Map.of()));
                newTrace.put("visibleBiomes", observedBiomes.get());
                if (!oldTrace.equals(newTrace))
                    throw new IllegalStateException(
                            "B RNG/structure/write trace differs "
                                    + f.id()
                                    + " "
                                    + oldTrace
                                    + " / "
                                    + newTrace);
                result.inputs()
                        .fullTerrain()
                        .forEach(
                                (p, chunk) -> {
                                    if (p == c.target().pack()) return;
                                    if (chunk.getPersistedStatus() != ChunkStatus.TERRAIN
                                            || !immutableBefore
                                                    .get(p)
                                                    .equals(
                                                            FeatureStageSnapshot.capture(
                                                                            dimension,
                                                                            chunk,
                                                                            host.registryAccess())
                                                                    .hash()))
                                        throw new IllegalStateException(
                                                "B fallback neighbor mutated");
                                });
            }
            if (loaded != host.getChunkSource().getLoadedChunksCount()
                    || blockTicks != host.getBlockTicks().count()
                    || fluidTicks != host.getFluidTicks().count()
                    || entities
                            != java.util.stream.StreamSupport.stream(
                                            host.getAllEntities().spliterator(), false)
                                    .count())
                throw new IllegalStateException("B physical pollution");
            Path output = root.resolve(run + "-" + f.id());
            Files.createDirectories(output);
            write(
                    output.resolve("entities-raw.json"),
                    Map.of("original", expected.rawEntityNbt(), "local", actual.rawEntityNbt()));
            var m = result.inputs().metrics();
            var counters = result.inputs().counters();
            if (counters.fullNeighbors() != m.fullNeighbors()
                    || !counters.fallbackReasons().equals(m.fallbackReasons())
                    || counters.fallbackNanos() != m.fallbackNanos()
                    || counters.blockReads() != m.blockReads()
                    || counters.heightReads() != m.heightReads()
                    || counters.sectionReads() != m.sectionReads()
                    || counters.biomePalettes() != m.biomePalettes()
                    || counters.columns().noiseBatches() != m.columns().noiseBatches()
                    || counters.columns().columns() != m.columns().columns()
                    || counters.columns().blockReads() != m.columns().blockReads()
                    || counters.columns().uniqueReads() != m.columns().uniqueReads()
                    || counters.columns().nanos() != m.columns().nanos()
                    || !counters.columns().fallbacks().equals(m.columns().fallbacks())
                    || m.columns().evidence().size() != counters.columns().columns()
                    || counters.columns().proofColumns() < counters.columns().columns())
                throw new IllegalStateException("B lightweight counters/full proofs disagree");
            Map<String, Object> entry = new LinkedHashMap<>(newTrace);
            result.inputs()
                    .structureReuseMetrics()
                    .ifPresent(metrics -> entry.put("structureReuse", metrics));
            result.inputs()
                    .biomeReuseCounters()
                    .ifPresent(metrics -> entry.put("biomeReuse", metrics));
            entry.put("hash", actual.hash());
            entry.put("worldgenHash", actual.worldgenDataHash());
            entry.put(
                    "nonEntityHash",
                    nonEntityEvidence(result.target(), dimension, host.registryAccess()));
            entry.put("originalHash", expected.hash());
            entry.put("seed", Long.toString(f.seed()));
            entry.put("hostSeed", Long.toString(host.getSeed()));
            entry.put("fullNeighbors", m.fullNeighbors());
            entry.put("fallbackReasons", m.fallbackReasons());
            entry.put("columnFallbacks", m.columns().fallbacks());
            entry.put("columns", m.columns().columns());
            entry.put("queryCounters", counters.columns());
            entry.put("noiseBatches", m.columns().noiseBatches());
            entry.put("biomePalettes", m.biomePalettes());
            entry.put("checkedBlocks", checkedBlocks.get());
            entry.put("checkedHeights", checkedHeights.get());
            entry.put("sectionReads", m.sectionReads());
            entry.put("checkedBiomes", checkedBiomes.get());
            entry.put("originalMs", oldNanos / 1e6);
            entry.put("localMs", newNanos / 1e6);
            entry.put("fallbackMs", m.fallbackNanos() / 1e6);
            entry.put("localQueryMs", m.columns().nanos() / 1e6);
            entry.put("preparationMs", result.preparationNanos() / 1e6);
            entry.put("decorationMs", result.decorationNanos() / 1e6);
            entry.put("blockEntities", actual.blockEntityCount());
            entry.put("entities", actual.entityCount());
            entry.put("pending", actual.pendingBlockEntityNbtCount());
            entry.put("blockTicks", actual.blockTickCount());
            entry.put("fluidTicks", actual.fluidTickCount());
            entry.put("postProcessing", actual.postProcessingCount());
            evidence.put(run + "-" + f.id(), entry);
            System.out.println("LIGHTWEIGHT B PASS " + run + " " + f.id() + " " + entry);
            return new Observation(actual, raw(result.target()));
        } catch (Exception e) {
            throw new IllegalStateException("Local B " + run + " " + f.id(), e);
        } finally {
            traces.forEach(FeatureBVerifier::removeWriter);
            ACTIVE.decrementAndGet();
        }
    }

    private static String nonEntityEvidence(
            ProtoChunk chunk, String dimension, net.minecraft.core.RegistryAccess registries) {
        // An explicitly limited End City diagnostic, NOT a new canonical equality/ignore rule.
        var snapshot = FeatureStageSnapshot.capture(dimension, chunk, registries);
        Map<String, Object> state = new TreeMap<>();
        try {
            for (String name :
                    List.of(
                            "dimension",
                            "chunkPos",
                            "minY",
                            "height",
                            "status",
                            "blocks",
                            "biomes",
                            "heightmaps",
                            "structureStarts",
                            "structureReferences",
                            "carvingMaskPresent",
                            "carvingMask",
                            "blockEntities",
                            "blockTicks",
                            "fluidTicks",
                            "postProcessing")) {
                var field = FeatureStageSnapshot.class.getDeclaredField(name);
                field.setAccessible(true);
                state.put(name, field.get(snapshot));
            }
            return HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            new GsonBuilder()
                                                    .create()
                                                    .toJson(state)
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (ReflectiveOperationException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> trace(
            IsolatedGenerationContext c,
            StrictFeatureRegion region,
            Map<String, Long> rejected,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                            .CompactStructureInputs
                    structures,
            Map<Long, ? extends ChunkAccess> chunks) {
        var tags =
                structures
                        .captureStartTags(
                                net.minecraft.world.level.levelgen.structure.pieces
                                        .StructurePieceSerializationContext.fromLevel(
                                        c.hostLevel()))
                        .stream()
                        .map(PreFeaturesVerifier::canonicalNbt)
                        .toList();
        return Map.of(
                "visibleBiomes",
                biomes(chunks),
                "structureTags",
                tags,
                "featureSeedCalls",
                counter(c, "featureSeedInvocationCount"),
                "featureSeedSequence",
                Long.toString(counter(c, "featureSeedSequenceHash")),
                "rejected",
                rejected,
                "physicalLevelEscapes",
                region.levelEscapeCounts());
    }

    private static Map<Long, SortedSet<String>> biomes(Map<Long, ? extends ChunkAccess> chunks) {
        Map<Long, SortedSet<String>> result = new TreeMap<>();
        chunks.forEach(
                (p, ch) -> {
                    var holders = new HashSet<Holder<net.minecraft.world.level.biome.Biome>>();
                    ch.collectBiomesInPalette(holders);
                    var names = new TreeSet<String>();
                    holders.forEach(
                            b -> names.add(b.unwrapKey().orElseThrow().identifier().toString()));
                    result.put(p, names);
                });
        return result;
    }

    private static long counter(IsolatedGenerationContext c, String name) {
        try {
            var f = IsolatedGenerationContext.class.getDeclaredField(name);
            f.setAccessible(true);
            return ((AtomicLong) f.get(c)).get();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ListTag raw(ProtoChunk chunk) {
        var result = new ListTag();
        chunk.getEntities().forEach(entity -> result.add(entity.copy()));
        return result;
    }

    private static void writeRaw(Path path, ListTag raw) throws java.io.IOException {
        var root = new CompoundTag();
        root.put("entities", raw);
        NbtIo.writeCompressed(root, path);
    }

    private static Observation read(Path root) throws java.io.IOException {
        return new Observation(
                FeatureStableSnapshot.read(root.resolve("features.bin.gz")),
                NbtIo.readCompressed(root.resolve("entities.nbt"), NbtAccounter.unlimitedHeap())
                        .getListOrEmpty("entities"));
    }

    private static void equal(Observation a, Observation b, String id, Path path) {
        var comparison =
                NativeEntityAcceptance.compare(
                        a.snapshot(), a.raw(), b.snapshot(), b.raw(), id.equals("end-city"));
        try {
            write(path, comparison);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot preserve B comparison " + id, failure);
        }
        if (!comparison.accepted())
            throw new IllegalStateException("B mismatch " + id + " " + comparison);
    }

    private static void write(Path p, Object value) throws java.io.IOException {
        Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n");
    }
}
