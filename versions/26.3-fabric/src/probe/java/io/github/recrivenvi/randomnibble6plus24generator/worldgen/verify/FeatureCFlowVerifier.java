package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.GsonBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.*;

/** Full native target loop. Dense C-policy reference is not nine-writer Vanilla parity. */
public final class FeatureCFlowVerifier {
    private FeatureCFlowVerifier() {}

    private static final ThreadLocal<Trace> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> INJECT_FAILURE = new ThreadLocal<>();
    private static final IllegalStateException SENTINEL =
            new IllegalStateException("Injected C generation failure");
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger PEAK =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong GENERATIONS =
            new java.util.concurrent.atomic.AtomicLong();

    static long generationCount() {
        return GENERATIONS.get();
    }

    private static final class Trace {
        final ChunkPos target;
        boolean decorating;
        Object random;
        int writers, selective, slots, draws;
        long hash = 0xcbf29ce484222325L;

        Trace(ChunkPos target) {
            this.target = target;
        }

        void add(long n) {
            hash = (hash ^ n) * 0x100000001b3L;
        }

        Map<String, Object> evidence() {
            return Map.of(
                    "writers",
                    writers,
                    "selective",
                    selective,
                    "slots",
                    slots,
                    "draws",
                    draws,
                    "randomHash",
                    Long.toUnsignedString(hash));
        }
    }

    public static void begin(WorldGenLevel level, ChunkAccess target) {
        var t = CURRENT.get();
        if (t == null) return;
        if (t.decorating || !target.getPos().equals(t.target))
            throw new IllegalStateException("Foreign/nested C writer");
        t.writers++;
        if (level instanceof SelectiveFeatureRegion) t.selective++;
        t.decorating = true;
    }

    public static void end() {
        var t = CURRENT.get();
        if (t != null) t.decorating = false;
    }

    public static void random(Object r) {
        var t = CURRENT.get();
        if (t != null && t.decorating) {
            if (t.random != null) throw new IllegalStateException("Decoration RNG replaced");
            t.random = r;
        }
    }

    public static void slot(Object r, long seed, int index, int step) {
        var t = CURRENT.get();
        if (t != null && t.decorating && t.random == r) {
            t.slots++;
            t.add(seed);
            t.add(index);
            t.add(step);
        }
    }

    public static void draw(Object r, int bits, int value) {
        var t = CURRENT.get();
        if (t != null && t.decorating && t.random == r) {
            if (t.slots > 0 && Boolean.TRUE.equals(INJECT_FAILURE.get())) throw SENTINEL;
            t.draws++;
            t.add(bits);
            t.add(value);
        }
    }

    private static final List<FeatureBVerifier.Fixture> FIXTURES;

    static {
        var fixtures = new ArrayList<>(FeatureBVerifier.FIXTURES);
        // Native Shulker rotation is independently nondeterministic; do not broaden normalization.
        fixtures.removeIf(f -> f.id().equals("end-city"));
        fixtures.add(new FeatureBVerifier.Fixture("birch-0", "overworld", 0, -12, -12));
        fixtures.add(new FeatureBVerifier.Fixture("birch-1", "overworld", 0, -8, -12));
        fixtures.add(new FeatureBVerifier.Fixture("birch-2", "overworld", 0, -12, -8));
        fixtures.add(new FeatureBVerifier.Fixture("birch-alt", "overworld", 1, -12, -12));
        fixtures.add(
                new FeatureBVerifier.Fixture(
                        "fossil-resource",
                        "overworld",
                        new MosaicSeedResolver(MosaicWorldProfile.current())
                                .resolveLocalWorldSeed(
                                        123456789, Level.OVERWORLD, new ChunkPos(-8, -13)),
                        -8,
                        -13));
        fixtures.add(new FeatureBVerifier.Fixture("taiga-0", "overworld", 0, -72, 144));
        fixtures.add(new FeatureBVerifier.Fixture("taiga-1", "overworld", 0, -68, 144));
        FIXTURES = List.copyOf(fixtures);
    }

    record Result(
            ProtoChunk target,
            FeatureStableSnapshot snapshot,
            List<SelectiveFeatureRegion.Call> calls,
            List<TreeContribution> batches,
            Map<String, Long> rejected,
            Map<String, Long> levelEscapes,
            Map<String, Object> trace) {}

    private static Result generate(
            MinecraftServer server, FeatureBVerifier.Fixture fixture, String mode) {
        return generate(
                server,
                fixture,
                mode,
                new TreeContribution.Scope(
                        "feature-c-flow",
                        "minecraft:" + fixture.dimension(),
                        "26.3-c-slot-feedback-2"));
    }

    static Result generate(
            MinecraftServer server,
            FeatureBVerifier.Fixture fixture,
            String mode,
            TreeContribution.Scope scope) {
        GENERATIONS.incrementAndGet();
        var host =
                server.getLevel(
                        ResourceKey.create(
                                Registries.DIMENSION,
                                Identifier.withDefaultNamespace(fixture.dimension())));
        var position = new ChunkPos(fixture.x(), fixture.z());
        var trace = new Trace(position);
        if (CURRENT.get() != null) throw new IllegalStateException("Nested probe");
        CURRENT.set(trace);
        PEAK.accumulateAndGet(ACTIVE.incrementAndGet(), Math::max);
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, host, fixture.seed(), position)) {
            ProtoChunk target;
            List<SelectiveFeatureRegion.Call> calls = new ArrayList<>();
            List<TreeContribution> batches = new ArrayList<>();
            Map<String, Long> rejected = new HashMap<>();
            Map<String, Long> levelEscapes = new HashMap<>();
            if (mode.equals("light")) {
                var result = SelectiveFeatureGeneration.generate(context, scope);
                target = result.target();
                calls.addAll(result.calls());
                batches.addAll(result.contributions());
                rejected.putAll(result.rejected());
                levelEscapes.putAll(result.levelEscapes());
            } else if (mode.equals("B-dense")) {
                var result = StrictFeatureBaseline.generate(context, (r, t) -> {});
                target = result.target();
                rejected.putAll(result.rejected());
                levelEscapes.putAll(result.region().levelEscapeCounts());
            } else if (mode.equals("B")) {
                var result =
                        LightweightFeatureBaseline.generate(
                                context, new LocalFeatureInputs.Observer() {}, (r, i) -> {});
                target = result.target();
                rejected.putAll(result.rejected());
                levelEscapes.putAll(result.region().levelEscapeCounts());
            } else {
                var result =
                        StrictFeatureBaseline.generate(
                                context,
                                (r, t) -> {},
                                (c, prepared) -> {
                                    Map<Long, StrictFeatureChunk> chunks = new HashMap<>();
                                    java.util.function.Consumer<String> drop =
                                            api -> rejected.merge(api, 1L, Long::sum);
                                    prepared.terrain()
                                            .forEach(
                                                    (p, chunk) ->
                                                            chunks.put(
                                                                    p,
                                                                    new StrictFeatureChunk(
                                                                            chunk,
                                                                            position,
                                                                            host
                                                                                    .palettedContainerFactory(),
                                                                            drop,
                                                                            host
                                                                                    .registryAccess())));
                                    try (var region =
                                            new SelectiveFeatureRegion(
                                                    c,
                                                    prepared.target(),
                                                    chunks,
                                                    prepared::terrain,
                                                    prepared.structures(),
                                                    scope,
                                                    drop) {
                                                @Override
                                                protected Evaluation evaluateTree(
                                                        PlacedFeature feature,
                                                        RandomSource random,
                                                        BlockPos origin,
                                                        TreeContribution.Key key) {
                                                    var copies =
                                                            new HashMap<
                                                                    Long,
                                                                    FeatureCVerifier
                                                                            .RecordingChunk>();
                                                    try (var dense =
                                                            new TreeProjectionRegion(
                                                                    c,
                                                                    baseline(),
                                                                    base -> {
                                                                        var copy =
                                                                                new FeatureCVerifier
                                                                                        .RecordingChunk(
                                                                                        base, host);
                                                                        copies.put(
                                                                                base.getPos()
                                                                                        .pack(),
                                                                                copy);
                                                                        return copy;
                                                                    },
                                                                    this)) {
                                                        boolean success =
                                                                new FeaturePlacer(
                                                                                dense,
                                                                                c.generator())
                                                                        .placeWithBiomeCheck(
                                                                                feature, random,
                                                                                origin);
                                                        return new Evaluation(
                                                                success,
                                                                FeatureCVerifier.capture(
                                                                        key,
                                                                        prepared.target().getMinY(),
                                                                        prepared.target()
                                                                                .getHeight(),
                                                                        host,
                                                                        copies.values()));
                                                    }
                                                }
                                            }) {
                                        c.generator()
                                                .applyBiomeDecoration(
                                                        region,
                                                        prepared.target(),
                                                        prepared.structures().manager());
                                        calls.addAll(region.calls());
                                        batches.addAll(region.contributions());
                                        levelEscapes.putAll(region.levelEscapeCounts());
                                        region.close();
                                        expectFailure(() -> region.recordSlot(0, 0, 0));
                                    }
                                });
                target = result.target();
            }
            if (trace.writers != 1
                    || trace.selective != (mode.startsWith("B") ? 0 : 1)
                    || trace.decorating
                    || trace.draws == 0
                    || target.getPersistedStatus() != ChunkStatus.FEATURES)
                throw new IllegalStateException(
                        "Incomplete C full native flow: " + trace.evidence());
            return new Result(
                    target,
                    FeatureStableSnapshot.capture(
                            "minecraft:" + fixture.dimension(), target, host.registryAccess()),
                    List.copyOf(calls),
                    List.copyOf(batches),
                    Map.copyOf(rejected),
                    Map.copyOf(levelEscapes),
                    trace.evidence());
        } finally {
            CURRENT.remove();
            ACTIVE.decrementAndGet();
        }
    }

    static void compare(Result expected, Result actual, String id) {
        if (!expected.snapshot.hash().equals(actual.snapshot.hash()))
            throw new IllegalStateException(
                    id
                            + " full target mismatch: "
                            + expected.snapshot.diff(actual.snapshot)
                            + "; "
                            + expected.snapshot.deterministicMetadataDifference(actual.snapshot));
        if (!expected.calls.equals(actual.calls) || !expected.trace.equals(actual.trace))
            throw new IllegalStateException(id + " native slot/random sequence mismatch");
        if (!expected.batches.equals(actual.batches))
            throw new IllegalStateException(id + " outgoing data mismatch");
    }

    public static void run(MinecraftServer server, Path root) throws Exception {
        Files.createDirectories(root);
        var physical = physical(server);
        verifyCapabilities(server);
        verifyFailure(server);
        var serial = new LinkedHashMap<String, Result>();
        var evidence = new ArrayList<Object>();
        int cells = 0, treeSlots = 0, coniferCells = 0, coniferSlots = 0;
        for (var f : FIXTURES) {
            System.out.println("C FLOW " + f.id());
            var result = generate(server, f, "light");
            compare(result, generate(server, f, "dense"), f.id());
            var b = generate(server, f, "B");
            if (f.id().equals("fossil-resource")) {
                if (result.levelEscapes.getOrDefault(
                                "net.minecraft.world.level.levelgen.feature.FossilFeature.place",
                                0L)
                        <= 0)
                    throw new IllegalStateException("Natural fossil template route missing");
                compare(b, generate(server, f, "B-dense"), "fossil original B");
            }
            if ((f.id().equals("end-spike") || f.id().equals("mineshaft"))
                            && result.snapshot.entityCount() == 0
                    || (f.id().equals("trial") || f.id().equals("village"))
                            && result.snapshot.blockEntityCount() == 0
                    || f.id().equals("mineshaft") && result.snapshot.blockTickCount() == 0
                    || f.id().equals("monument") && result.snapshot.fluidTickCount() == 0
                    || f.id().equals("village-edge") && result.snapshot.postProcessingCount() == 0)
                throw new IllegalStateException("Missing C real side-effect positive " + f.id());
            if (result.batches.isEmpty() && !result.snapshot.hash().equals(b.snapshot.hash()))
                throw new IllegalStateException(
                        "C changed B without an authorized tree: " + f.id());
            serial.put(f.id(), result);
            if (f.id().startsWith("taiga-")) {
                var taiga =
                        result.batches.stream()
                                .filter(
                                        batch ->
                                                batch.key()
                                                        .feature()
                                                        .equals("minecraft:trees_taiga"))
                                .toList();
                if (taiga.isEmpty())
                    throw new IllegalStateException("Natural taiga slot not audited");
                coniferSlots += taiga.size();
                coniferCells += taiga.stream().mapToInt(batch -> batch.cells().size()).sum();
            }
            cells += result.batches.stream().mapToInt(batch -> batch.cells().size()).sum();
            treeSlots += result.batches.size();
            var row = new LinkedHashMap<String, Object>();
            row.put("fixture", f);
            row.put("hash", result.snapshot.hash());
            row.put("bHash", b.snapshot.hash());
            row.put("trace", result.trace);
            row.put("calls", result.calls);
            row.put("rejected", result.rejected);
            row.put("levelEscapes", result.levelEscapes);
            row.put("treeSlots", result.batches.size());
            row.put("cells", result.batches.stream().mapToInt(batch -> batch.cells().size()).sum());
            row.put("blockEntities", result.snapshot.blockEntityCount());
            row.put("entities", result.snapshot.entityCount());
            row.put("blockTicks", result.snapshot.blockTickCount());
            row.put("fluidTicks", result.snapshot.fluidTickCount());
            row.put("postProcessing", result.snapshot.postProcessingCount());
            row.put("pendingBlockEntities", result.snapshot.pendingBlockEntityNbtCount());
            row.put(
                    "outsideEffects",
                    Map.of(
                            "blockEntities",
                            result.batches.stream()
                                    .flatMap(batch -> batch.cells().stream())
                                    .filter(cell -> cell.blockEntity().isPresent())
                                    .count(),
                            "blockTicks",
                            result.batches.stream()
                                    .mapToInt(batch -> batch.blockTicks().size())
                                    .sum(),
                            "fluidTicks",
                            result.batches.stream()
                                    .mapToInt(batch -> batch.fluidTicks().size())
                                    .sum(),
                            "postProcessing",
                            result.batches.stream()
                                    .mapToInt(batch -> batch.postProcessing().size())
                                    .sum()));
            evidence.add(row);
            result.snapshot.write(root.resolve(f.id() + ".bin.gz"));
            Files.writeString(
                    root.resolve(f.id() + "-entities-raw.json"),
                    new GsonBuilder()
                                    .setPrettyPrinting()
                                    .create()
                                    .toJson(result.snapshot.rawEntityNbt())
                            + "\n");
            var raw = new CompoundTag();
            var list = new ListTag();
            result.batches.forEach(batch -> list.add(batch.encode()));
            raw.put("contributions", list);
            NbtIo.writeCompressed(raw, root.resolve(f.id() + ".nbt"));
            if (root.getFileName().toString().equals("host_1")) {
                Path previous = root.getParent().resolve("host_0");
                if (!raw.equals(
                                NbtIo.readCompressed(
                                        previous.resolve(f.id() + ".nbt"),
                                        NbtAccounter.unlimitedHeap()))
                        || !result.snapshot
                                .hash()
                                .equals(
                                        FeatureStableSnapshot.read(
                                                        previous.resolve(f.id() + ".bin.gz"))
                                                .hash()))
                    throw new IllegalStateException("C independent host mismatch: " + f.id());
            }
        }
        // Reverse requests and actual concurrent sessions include identical coordinates with
        // different seeds.
        var repeated =
                List.of(
                        FIXTURES.get(0),
                        FIXTURES.get(13),
                        FIXTURES.get(14),
                        FIXTURES.get(17),
                        FIXTURES.get(11),
                        FIXTURES.get(19),
                        FIXTURES.get(20));
        for (int i = repeated.size() - 1; i >= 0; i--)
            compare(
                    serial.get(repeated.get(i).id()),
                    generate(server, repeated.get(i), "light"),
                    "reverse");
        try (var pool = Executors.newFixedThreadPool(3)) {
            var futures =
                    repeated.stream()
                            .map(
                                    f ->
                                            CompletableFuture.runAsync(
                                                    () ->
                                                            compare(
                                                                    serial.get(f.id()),
                                                                    generate(server, f, "light"),
                                                                    "parallel"),
                                                    pool))
                            .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        if (cells == 0
                || coniferCells == 0
                || coniferSlots == 0
                || treeSlots == 0
                || PEAK.get() < 3
                || !physical.equals(physical(server))
                || GenerationContextRegistry.bindingCount() != 0
                || CURRENT.get() != null)
            throw new IllegalStateException(
                    "C positive/concurrency/physical isolation proof missing");
        var summary = new LinkedHashMap<String, Object>();
        summary.put("status", "PASS");
        summary.put("proofVersion", 2);
        summary.put("pid", ProcessHandle.current().pid());
        summary.put("hostSeed", server.overworld().getSeed());
        summary.put("fixtures", evidence);
        summary.put("cells", cells);
        summary.put("treeSlots", treeSlots);
        summary.put("coniferCells", coniferCells);
        summary.put("coniferSlots", coniferSlots);
        summary.put("peakSessions", PEAK.get());
        summary.put("repeatComparisons", repeated.size() * 2);
        summary.put("bindings", GenerationContextRegistry.bindingCount());
        summary.put("physicalUnchanged", true);
        summary.put(
                "capabilities",
                "all seven write channels rejected outside; region RNG/tick owner shared");
        summary.put("exceptionCleanup", true);
        Files.writeString(
                root.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(summary) + "\n");
    }

    static List<Long> physical(MinecraftServer server) {
        var out = new ArrayList<Long>();
        for (ServerLevel host : server.getAllLevels()) {
            out.add((long) host.getChunkSource().getLoadedChunksCount());
            out.add((long) host.getBlockTicks().count());
            out.add((long) host.getFluidTicks().count());
            out.add(
                    java.util.stream.StreamSupport.stream(
                                    host.getAllEntities().spliterator(), false)
                            .count());
        }
        return out;
    }

    private static void verifyFailure(MinecraftServer server) {
        INJECT_FAILURE.set(true);
        boolean seen = false;
        try {
            generate(server, FIXTURES.get(14), "light");
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause())
                if (cause == SENTINEL) seen = true;
            if (!seen) throw failure;
        } finally {
            INJECT_FAILURE.remove();
        }
        if (!seen || CURRENT.get() != null || GenerationContextRegistry.bindingCount() != 0)
            throw new IllegalStateException("C exception swallowed or session leaked");
    }

    private static void verifyCapabilities(MinecraftServer server) {
        var host = server.overworld();
        try (var context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, host, 0, new ChunkPos(-12, -12))) {
            StrictFeatureBaseline.generate(
                    context,
                    (r, t) -> {},
                    (c, prepared) -> {
                        Map<String, Long> rejected = new HashMap<>();
                        java.util.function.Consumer<String> drop =
                                api -> rejected.merge(api, 1L, Long::sum);
                        Map<Long, StrictFeatureChunk> chunks = new HashMap<>();
                        prepared.terrain()
                                .forEach(
                                        (p, chunk) ->
                                                chunks.put(
                                                        p,
                                                        new StrictFeatureChunk(
                                                                chunk,
                                                                c.target(),
                                                                host.palettedContainerFactory(),
                                                                drop,
                                                                host.registryAccess())));
                        try (var region =
                                new SelectiveFeatureRegion(
                                        c,
                                        prepared.target(),
                                        chunks,
                                        prepared::terrain,
                                        prepared.structures(),
                                        new TreeContribution.Scope(
                                                "capability", "minecraft:overworld", "C"),
                                        drop)) {
                            try {
                                region.getLevel();
                                throw new IllegalStateException(
                                        "Unknown physical level escape accepted");
                            } catch (UnsupportedOperationException expected) {
                                if (!expected.getMessage()
                                        .startsWith("Unknown B physical level escape"))
                                    throw expected;
                            }
                            var p = c.target().getWorldPosition().offset(16, 80, 4);
                            var neighbor = prepared.terrain().get(ChunkPos.containing(p).pack());
                            String before =
                                    FeatureStableSnapshot.capture(
                                                    "minecraft:overworld",
                                                    neighbor,
                                                    host.registryAccess())
                                            .hash();
                            var view =
                                    (StrictFeatureChunk)
                                            region.getChunk(
                                                    ChunkPos.containing(p).x(),
                                                    ChunkPos.containing(p).z());
                            var chest =
                                    net.minecraft.world.level.block.Blocks.CHEST
                                            .defaultBlockState();
                            if (region.setBlock(p, chest, 2))
                                throw new IllegalStateException("C non-tree region write accepted");
                            view.setBlockState(p, chest, 2);
                            view.setBlockEntity(
                                    new net.minecraft.world.level.block.entity.ChestBlockEntity(
                                            p, chest));
                            var pending = new CompoundTag();
                            pending.putInt("x", p.getX());
                            pending.putInt("y", p.getY());
                            pending.putInt("z", p.getZ());
                            pending.putString("id", "DUMMY");
                            view.setBlockEntityNbt(pending);
                            var entity = new CompoundTag();
                            entity.putString("id", "minecraft:end_crystal");
                            var coords = new ListTag();
                            coords.add(DoubleTag.valueOf(p.getX()));
                            coords.add(DoubleTag.valueOf(p.getY()));
                            coords.add(DoubleTag.valueOf(p.getZ()));
                            entity.put("Pos", coords);
                            view.addEntityNbt(entity);
                            var crystal =
                                    new net.minecraft.world.entity.boss.enderdragon.EndCrystal(
                                            host, p.getX(), p.getY(), p.getZ());
                            crystal.setPos(p.getX(), p.getY(), p.getZ());
                            if (region.addFreshEntity(crystal))
                                throw new IllegalStateException("C non-tree entity accepted");
                            var blockTick =
                                    new net.minecraft.world.ticks.ScheduledTick<>(
                                            net.minecraft.world.level.block.Blocks.STONE, p, 0, 0);
                            var fluidTick =
                                    new net.minecraft.world.ticks.ScheduledTick<
                                            net.minecraft.world.level.material.Fluid>(
                                            net.minecraft.world.level.material.Fluids.WATER,
                                            p,
                                            0,
                                            0);
                            region.getBlockTicks().schedule(blockTick);
                            region.getFluidTicks().schedule(fluidTick);
                            view.getBlockTicks().schedule(blockTick);
                            view.getFluidTicks().schedule(fluidTick);
                            view.markPosForPostProcessing(p);
                            if (!before.equals(
                                            FeatureStableSnapshot.capture(
                                                            "minecraft:overworld",
                                                            neighbor,
                                                            host.registryAccess())
                                                    .hash())
                                    || rejected.values().stream().mapToLong(Long::longValue).sum()
                                            != 11)
                                throw new IllegalStateException(
                                        "C non-tree boundary did not reject every capability: "
                                                + rejected);
                            var baseline =
                                    new StrictFeatureBaseline.Result(
                                            prepared.target(),
                                            region,
                                            prepared.terrain(),
                                            prepared.structures(),
                                            Map.of(),
                                            0);
                            long n = region.nextSubTickCount();
                            try (var first = new TreeProjectionRegion(c, baseline, null, region);
                                    var second =
                                            new TreeProjectionRegion(c, baseline, null, region)) {
                                if (first.getRandom() != region.getRandom()
                                        || second.getRandom() != region.getRandom()
                                        || first.nextSubTickCount() != n + 1
                                        || second.nextSubTickCount() != n + 2
                                        || region.nextSubTickCount() != n + 3)
                                    throw new IllegalStateException(
                                            "C tree slot reset region random/tick order");
                            }
                        }
                    });
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            return;
        }
        throw new IllegalStateException("Closed C capability accepted");
    }
}
