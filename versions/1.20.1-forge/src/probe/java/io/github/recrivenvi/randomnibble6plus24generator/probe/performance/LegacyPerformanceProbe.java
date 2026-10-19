package io.github.recrivenvi.randomnibble6plus24generator.probe.performance;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin.PerformanceMapAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jdk.jfr.*;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Native 1.20.1 ticket frontier, not a client/player/rendering measurement. */
public final class LegacyPerformanceProbe {
    private static final String PREFIX = "mosaic.legacyPerformance.";
    private static final ConcurrentHashMap<Long, Timeline> CHUNKS = new ConcurrentHashMap<>();
    private static final List<JsonObject> SAMPLES = new ArrayList<>();
    private static final LongAdder scheduled = new LongAdder(),
            contexts = new LongAdder(),
            narrowContexts = new LongAdder();
    private static final LongAdder structureCalls = new LongAdder(),
            sources = new LongAdder(),
            structureNanos = new LongAdder();
    private static final LongAdder sourceReuseHits = new LongAdder(),
            sourceReuseEvaluations = new LongAdder();
    private static final LongAdder sourceStateMismatches = new LongAdder(),
            sourceStateReplays = new LongAdder();
    private static final LongAdder ringPreparations = new LongAdder(),
            ringBiomeSearches = new LongAdder();
    private static final LongAdder scopedRingContexts = new LongAdder(),
            scopedRingSlots = new LongAdder(),
            scopedTailReplays = new LongAdder(),
            scopedTailFallbacks = new LongAdder();

    public static void scopedRing(
            boolean created,
            io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingScope
                            .Metrics
                    metrics) {
        if (!enabled() || began == 0 || finished) return;
        if (created) scopedRingContexts.increment();
        else {
            scopedRingSlots.add(metrics.slotsConsidered());
            scopedTailReplays.add(metrics.tailReplays());
            scopedTailFallbacks.add(metrics.tailFallbacks());
        }
    }

    private static final AtomicInteger active = new AtomicInteger(),
            peakActive = new AtomicInteger();
    private static final AtomicReference<Throwable> failure = new AtomicReference<>();
    private static final ThreadLocal<Work> WORK = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TICKET_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RING_TASK = new ThreadLocal<>();

    public static boolean inRingTask() {
        return enabled() && Boolean.TRUE.equals(RING_TASK.get());
    }

    public static java.util.function.Supplier<ChunkPos> ringTask(
            java.util.function.Supplier<ChunkPos> original) {
        if (!enabled()) return original;
        return () -> {
            var previous = RING_TASK.get();
            RING_TASK.set(true);
            try {
                return original.get();
            } finally {
                if (previous == null) RING_TASK.remove();
                else RING_TASK.set(previous);
            }
        };
    }

    private static int tokenRespawnSkips;
    private static final LongAdder preWindowWorkers = new LongAdder();
    private static volatile long began;
    private static volatile boolean finished;
    private static volatile ServerLevel level;
    private static volatile MosaicPhysicalLevelState physical;
    private static Recording recording;
    private static ServerPlayer viewer;
    private static ChunkPos center;
    private static Path output;
    private static int moves, view, simulation;
    private static long nextSample, lastTick, maxTickGap, initialCpu, initialServerCpu, initialGc;

    private LegacyPerformanceProbe() {}

    @Name("mosaic.ChunkPhase")
    @StackTrace(false)
    public static class Marker extends Event {
        public String phase, key, detail;
    }

    private static void mark(String phase) {
        var marker = new Marker();
        marker.phase = phase;
        marker.key = level.dimension().location() + ":" + center.x + "," + center.z;
        marker.detail = "native-1.20.1-player-ticket-frontier";
        marker.commit();
    }

    public static boolean enabled() {
        return "performance-legacy_frontier".equals(System.getProperty("validation.name"))
                && Boolean.getBoolean(PREFIX + "enabled");
    }

    public static boolean constructingTicketSource() {
        return enabled() && Boolean.TRUE.equals(TICKET_SOURCE.get());
    }

    public static void tokenRespawnSkipped() {
        if (!constructingTicketSource())
            throw new IllegalStateException("Not the offline ticket source");
        tokenRespawnSkips++;
    }

    private static ServerPlayer ticketSource(MinecraftServer server) {
        if (TICKET_SOURCE.get() != null)
            throw new IllegalStateException("Nested ticket source creation");
        TICKET_SOURCE.set(true);
        try {
            return new ServerPlayer(
                    server,
                    level,
                    new GameProfile(
                            UUID.nameUUIDFromBytes(
                                    "Mosaic120TicketProfile"
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            "ProfileTicket"));
        } finally {
            TICKET_SOURCE.remove();
        }
    }

    private static boolean observing(ServerLevel owner) {
        return enabled() && began != 0 && !finished && owner == level;
    }

    public static final class Work {
        final long queued = System.nanoTime();
        volatile long start, end;
        volatile boolean canonical;
    }

    private static final class Timeline {
        final ChunkPos position;
        final long localSeed;
        final Map<String, Long> first = new ConcurrentHashMap<>();
        final List<Work> work = new CopyOnWriteArrayList<>();
        long firstDemand, lastDemand;
        volatile long preparationNanos, decorationNanos;
        final AtomicInteger canonicalInvocations = new AtomicInteger();

        Timeline(ChunkPos position) {
            this.position = position;
            long master = level.getServer().getWorldData().worldGenOptions().seed();
            var generator = level.getChunkSource().getGenerator();
            localSeed =
                    generator instanceof MosaicChunkGenerator mosaic
                            ? new MosaicSeedResolver(mosaic.profile())
                                    .resolveLocalWorldSeed(master, level.dimension(), position)
                            : master;
        }

        void at(String phase) {
            first.putIfAbsent(phase, System.nanoTime());
        }
    }

    private static Timeline timeline(ChunkPos position) {
        return CHUNKS.computeIfAbsent(position.toLong(), ignored -> new Timeline(position));
    }

    public static void phase(ServerLevel owner, ChunkPos position, String phase) {
        if (observing(owner)) timeline(position).at(phase);
    }

    public static void phase(String dimension, ChunkPos position, String phase) {
        var owner = level;
        if (owner != null && owner.dimension().location().toString().equals(dimension))
            phase(owner, position, phase);
    }

    public static void scheduled(ServerLevel owner, ChunkPos position, ChunkStatus status) {
        if (!observing(owner)) return;
        scheduled.increment();
        timeline(position).at("SCHEDULE_" + status);
    }

    public static void step(ServerLevel owner, ChunkPos position, ChunkStatus status, boolean end) {
        phase(owner, position, "STEP_" + status + (end ? "_END" : "_BEGIN"));
    }

    public static void failed(Throwable error) {
        if (!finished && enabled()) failure.compareAndSet(null, error);
    }

    /** Wrap exactly one original runnable; keep the original executor and returned future. */
    public static Runnable worker(
            ServerLevel owner,
            ChunkPos position,
            MosaicPhysicalLevelState state,
            Runnable original) {
        if (enabled() && began == 0) preWindowWorkers.increment();
        if (!observing(owner)) return original;
        physical = state;
        var work = new Work();
        timeline(position).work.add(work);
        return () -> {
            work.start = System.nanoTime();
            int running = active.incrementAndGet();
            peakActive.accumulateAndGet(running, Math::max);
            var previous = WORK.get();
            WORK.set(work);
            try {
                original.run();
            } finally {
                work.end = System.nanoTime();
                active.decrementAndGet();
                if (previous == null) WORK.remove();
                else WORK.set(previous);
            }
        };
    }

    public static void canonical(
            ServerLevel owner, ChunkPos position, boolean end, BFeaturePreparation.Result result) {
        if (!observing(owner)) return;
        var work = WORK.get();
        if (work != null) work.canonical = true;
        var row = timeline(position);
        row.at(end ? "B_END" : "B_BEGIN");
        if (!end) row.canonicalInvocations.incrementAndGet();
        else {
            row.preparationNanos =
                    row.first.get("B_END") - row.first.get("B_BEGIN") - result.decorationNanos();
            row.decorationNanos = result.decorationNanos();
        }
    }

    public static void context(boolean narrow) {
        if (!enabled() || began == 0 || finished) return;
        contexts.increment();
        if (narrow) narrowContexts.increment();
    }

    public static void structures(long nanos, int evaluated) {
        if (!enabled() || began == 0 || finished) return;
        structureCalls.increment();
        sources.add(evaluated);
        structureNanos.add(nanos);
    }

    public static void sourceReuse(long hits, long evaluations, long mismatches, long replays) {
        if (!enabled() || began == 0 || finished) return;
        sourceReuseHits.add(hits);
        sourceReuseEvaluations.add(evaluations);
        sourceStateMismatches.add(mismatches);
        sourceStateReplays.add(replays);
    }

    public static void ring(boolean search) {
        if (!enabled() || began == 0 || finished) return;
        if (search) ringBiomeSearches.increment();
        else ringPreparations.increment();
    }

    public static void tick(MinecraftServer server) {
        if (!enabled() || finished) return;
        try {
            advance(server);
        } catch (Throwable error) {
            failure.compareAndSet(null, error);
            finished = true;
            try {
                if (output != null) writeFailure(error);
            } catch (Exception writing) {
                error.addSuppressed(writing);
            }
            throw new IllegalStateException("Native performance workload failed", error);
        }
    }

    private static void advance(MinecraftServer server) throws Exception {
        if (began == 0) {
            level =
                    Objects.requireNonNull(
                            server.getLevel(
                                    ResourceKey.create(
                                            Registries.DIMENSION,
                                            new ResourceLocation(
                                                    System.getProperty(
                                                            PREFIX + "dimension",
                                                            "minecraft:overworld")))));
            output = Path.of(System.getProperty(PREFIX + "output"));
            Files.createDirectories(output);
            if (Files.exists(output.resolve("summary.json")))
                throw new IllegalStateException("Existing performance evidence");
            view = Integer.getInteger(PREFIX + "view", 8);
            simulation = Integer.getInteger(PREFIX + "simulation", 8);
            if (!Set.of(8, 12, 16).contains(view)
                    || simulation != view
                    || Runtime.getRuntime().availableProcessors() != 4)
                throw new IllegalArgumentException(
                        "Controlled native frontier requires 8/8, 12/12 or 16/16 and four CPUs");
            boolean mosaic = level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator;
            if (mosaic != Boolean.getBoolean(PREFIX + "mosaic"))
                throw new IllegalStateException("Wrong world identity");
            requireCold(server);
            center = new ChunkPos(125, -37);
            level.getChunkSource().setViewDistance(view);
            level.getChunkSource().setSimulationDistance(simulation);
            viewer = ticketSource(server);
            if (tokenRespawnSkips != 1 || TICKET_SOURCE.get() != null || physical != null)
                throw new IllegalStateException(
                        "Unrelated spawn work entered ticket source preparation");
            requireCold(server);
            recording = new Recording(Configuration.getConfiguration("profile"));
            recording.setName("Mosaic 1.20.1 native frontier");
            recording.setToDisk(true);
            recording.setMaxSize(256L * 1024 * 1024);
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(5));
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(5));
            recording.start();
            initialCpu = cpu();
            initialServerCpu = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            initialGc = gcMillis();
            began = lastTick = System.nanoTime();
            nextSample = began;
            mark("WORKLOAD_START_frontier");
            demand();
            level.getChunkSource()
                    .chunkMap
                    .getDistanceManager()
                    .addPlayer(SectionPos.of(center, 0), viewer);
            System.out.println("MOSAIC_120_PERF_START pid=" + ProcessHandle.current().pid());
        }
        long now = System.nanoTime();
        maxTickGap = Math.max(maxTickGap, now - lastTick);
        lastTick = now;
        int due = Math.min(2, (int) ((now - began) / 15_000_000_000L));
        while (moves < due) {
            var manager = level.getChunkSource().chunkMap.getDistanceManager();
            manager.removePlayer(SectionPos.of(center, 0), viewer);
            center = new ChunkPos(center.x + 2, center.z);
            manager.addPlayer(SectionPos.of(center, 0), viewer);
            moves++;
            demand();
        }
        for (var holder :
                List.copyOf(
                        ((PerformanceMapAccess) level.getChunkSource().chunkMap)
                                .mosaic$updatingMap()
                                .values())) {
            var row = timeline(holder.getPos());
            if (ready(holder.getFutureIfPresentUnchecked(ChunkStatus.FULL))) row.at("STATUS_FULL");
            if (ready(holder.getFullChunkFuture())) row.at("ACCESSIBLE_FULL");
            if (ready(holder.getTickingChunkFuture())) row.at("BLOCK_TICKING");
            if (ready(holder.getEntityTickingChunkFuture())) row.at("ENTITY_TICKING");
            if (holder.getTickingChunk() != null) row.at("SEND_READY");
        }
        if (failure.get() != null)
            throw new IllegalStateException("Observed generation future failed", failure.get());
        if (now >= nextSample) {
            sample(now);
            nextSample = now + 1_000_000_000L;
        }
        if (now - began >= Long.getLong(PREFIX + "seconds", 60L) * 1_000_000_000L)
            finish(server, System.nanoTime());
    }

    private static boolean ready(CompletableFuture<? extends Either<?, ?>> future) {
        var result = future.getNow(null);
        return result != null && result.left().isPresent();
    }

    private static void requireCold(MinecraftServer server) {
        if (preWindowWorkers.sum() != 0) throw new IllegalStateException("Pre-window Mosaic work");
        for (var dimension : server.getAllLevels()) {
            var holders =
                    ((PerformanceMapAccess) dimension.getChunkSource().chunkMap)
                            .mosaic$updatingMap();
            var metrics =
                    MosaicPhysicalLevelOwner.forLevel(dimension).mosaic$physicalState().metrics();
            if (!holders.isEmpty()
                    || dimension.getChunkSource().getLoadedChunksCount() != 0
                    || metrics.generations() != 0
                    || metrics.publications() != 0
                    || metrics.inFlight() != 0
                    || metrics.spawn() != 0
                    || metrics.full() != 0)
                throw new IllegalStateException(
                        "Ticket source prewarmed physical state: " + dimension.dimension());
        }
    }

    private static List<ChunkPos> viewport() {
        var result = new ArrayList<ChunkPos>();
        for (int z = center.z - view - 1; z <= center.z + view + 1; z++)
            for (int x = center.x - view - 1; x <= center.x + view + 1; x++)
                if (ChunkMap.isChunkInRange(x, z, center.x, center.z, view))
                    result.add(new ChunkPos(x, z));
        return result;
    }

    private static void demand() {
        long now = System.nanoTime();
        viewport()
                .forEach(
                        pos -> {
                            var row = timeline(pos);
                            if (row.firstDemand == 0) row.firstDemand = now;
                            row.lastDemand = now;
                        });
    }

    private static long cpu() {
        return ((com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean())
                .getProcessCpuTime();
    }

    private static long gcMillis() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(b -> Math.max(0, b.getCollectionTime()))
                .sum();
    }

    private static void sample(long now) {
        JsonObject row = new JsonObject();
        row.addProperty("seconds", (now - began) / 1e9);
        row.addProperty("records", CHUNKS.size());
        row.addProperty("activeWorkers", active.get());
        row.addProperty(
                "queuedWorkers",
                CHUNKS.values().stream()
                        .flatMap(c -> c.work.stream())
                        .filter(w -> w.start == 0)
                        .count());
        row.addProperty(
                "completedFull",
                CHUNKS.values().stream().filter(c -> c.first.containsKey("STATUS_FULL")).count());
        row.addProperty(
                "completedSendReady",
                CHUNKS.values().stream().filter(c -> c.first.containsKey("SEND_READY")).count());
        AtomicInteger visible = new AtomicInteger(), sent = new AtomicInteger();
        viewport()
                .forEach(
                        pos -> {
                            visible.incrementAndGet();
                            var c = CHUNKS.get(pos.toLong());
                            if (c != null && c.first.containsKey("SEND_READY"))
                                sent.incrementAndGet();
                        });
        row.addProperty("viewportChunks", visible.get());
        row.addProperty("viewportReady", sent.get());
        row.addProperty(
                "processCpuBudgetFraction", (cpu() - initialCpu) / (double) (now - began) / 4);
        row.addProperty(
                "serverCpuFraction",
                (ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - initialServerCpu)
                        / (double) (now - began));
        row.addProperty("gcMillis", gcMillis() - initialGc);
        row.addProperty(
                "usedHeap", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        if (physical != null) row.add("physicalMetrics", new Gson().toJsonTree(physical.metrics()));
        SAMPLES.add(row);
    }

    private static void finish(MinecraftServer server, long end) throws Exception {
        // Freeze the fixed observation window before ticket removal, cancellation or saving.
        finished = true;
        mark("WORKLOAD_END");
        sample(end);
        level.getChunkSource()
                .chunkMap
                .getDistanceManager()
                .removePlayer(SectionPos.of(center, 0), viewer);
        JsonObject result = new JsonObject();
        result.addProperty("status", "MEASURED");
        result.addProperty("schema", 2);
        result.addProperty("tokenRespawnSkips", tokenRespawnSkips);
        result.addProperty("coldAfterToken", true);
        result.addProperty("coldUpdatingHoldersVerified", true);
        result.addProperty("preWindowMosaicWorkers", preWindowWorkers.sum());
        result.addProperty("sendReadyDefinition", "NATIVE_GET_TICKING_CHUNK_NOT_PACKET");
        result.addProperty("target", "1.20.1-forge");
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("dimension", level.dimension().location().toString());
        result.addProperty("inputHash", System.getProperty(PREFIX + "inputHash"));
        result.addProperty(
                "mosaic", level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator);
        result.addProperty("worldSeed", server.getWorldData().worldGenOptions().seed());
        result.addProperty("seconds", (end - began) / 1e9);
        result.addProperty("view", view);
        result.addProperty("simulation", simulation);
        result.addProperty("cliFrontierNotPlayerOrPacket", true);
        result.addProperty("nativePlayerTickets", true);
        result.addProperty("moves", moves);
        result.addProperty("cpus", Runtime.getRuntime().availableProcessors());
        result.addProperty("peakActiveWorkers", peakActive.get());
        result.addProperty("scheduledTasks", scheduled.sum());
        result.addProperty("contexts", contexts.sum());
        result.addProperty("narrowContexts", narrowContexts.sum());
        result.addProperty("structureCalls", structureCalls.sum());
        result.addProperty("evaluatedStructureSources", sources.sum());
        result.addProperty("sourceReuseHits", sourceReuseHits.sum());
        result.addProperty("sourceReuseEvaluations", sourceReuseEvaluations.sum());
        result.addProperty("sourceStateMismatches", sourceStateMismatches.sum());
        result.addProperty("sourceStateReplays", sourceStateReplays.sum());
        result.addProperty("structureCumulativeNanos", structureNanos.sum());
        result.addProperty("maxObservedTickGapMillis", maxTickGap / 1e6);
        result.addProperty("ringPreparations", ringPreparations.sum());
        result.addProperty("ringBiomeSearches", ringBiomeSearches.sum());
        result.addProperty("scopedRingContexts", scopedRingContexts.sum());
        result.addProperty("scopedRingSlots", scopedRingSlots.sum());
        result.addProperty("scopedTailReplays", scopedTailReplays.sum());
        result.addProperty("scopedTailFallbacks", scopedTailFallbacks.sum());
        result.addProperty(
                "processCpuBudgetFraction", (cpu() - initialCpu) / (double) (end - began) / 4);
        result.addProperty(
                "serverCpuFraction",
                (ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - initialServerCpu)
                        / (double) (end - began));
        result.addProperty("gcMillis", gcMillis() - initialGc);
        result.add("samples", new Gson().toJsonTree(SAMPLES));
        JsonArray chunks = new JsonArray();
        CHUNKS.values().stream()
                .sorted(
                        Comparator.comparingInt((Timeline c) -> c.position.z)
                                .thenComparingInt(c -> c.position.x))
                .forEach(
                        c -> {
                            JsonObject row = new JsonObject();
                            row.addProperty("x", c.position.x);
                            row.addProperty("z", c.position.z);
                            row.addProperty("localSeed", c.localSeed);
                            if (c.firstDemand != 0) {
                                row.addProperty("demandNanos", c.firstDemand - began);
                                row.addProperty("lastDemandNanos", c.lastDemand - began);
                            }
                            JsonObject phases = new JsonObject();
                            new TreeMap<>(c.first)
                                    .forEach(
                                            (key, nanos) -> {
                                                if (nanos <= end)
                                                    phases.addProperty(key, nanos - began);
                                            });
                            row.add("phases", phases);
                            row.addProperty("canonicalInvocations", c.canonicalInvocations.get());
                            row.addProperty("preparationNanos", c.preparationNanos);
                            row.addProperty("decorationNanos", c.decorationNanos);
                            JsonArray work = new JsonArray();
                            for (var w : c.work) {
                                if (w.queued > end) continue;
                                JsonObject span = new JsonObject();
                                span.addProperty("queuedNanos", w.queued - began);
                                if (w.start != 0 && w.start <= end)
                                    span.addProperty("startNanos", w.start - began);
                                if (w.end != 0 && w.end <= end)
                                    span.addProperty("endNanos", w.end - began);
                                span.addProperty("canonical", w.canonical);
                                work.add(span);
                            }
                            row.add("work", work);
                            chunks.add(row);
                        });
        result.add("chunks", chunks);
        recording.stop();
        recording.dump(output.resolve("checkpoint.jfr"));
        recording.close();
        recording = null;
        Files.writeString(
                output.resolve("summary.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("MOSAIC_120_PERF_MEASURED " + output);
        server.halt(false);
    }

    private static void writeFailure(Throwable error) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("status", "FAIL");
        result.addProperty("failure", error.toString());
        Files.writeString(
                output.resolve("failure.json"), new GsonBuilder().create().toJson(result));
        if (recording != null) {
            recording.stop();
            recording.dump(output.resolve("failed.jfr"));
            recording.close();
            recording = null;
        }
    }
}
