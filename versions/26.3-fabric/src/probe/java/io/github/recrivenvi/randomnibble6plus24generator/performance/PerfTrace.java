package io.github.recrivenvi.randomnibble6plus24generator.performance;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jdk.jfr.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;

/** Opt-in test-mod observer. No task, future, ticket, or priority is changed here. */
public final class PerfTrace {
    private static final long ORIGIN = System.nanoTime();
    private static final ConcurrentHashMap<String, Timeline> CHUNKS = new ConcurrentHashMap<>();
    private static final Map<Object, ServerLevel> OWNERS =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ConcurrentHashMap<Thread, String> SCOPES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Thread, String> PURPOSES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Thread, ServerLevel> PLAN_LEVELS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> AGGREGATES =
            new ConcurrentHashMap<>();
    private static final Map<MinecraftServer, long[]> PROFILE_IO =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static volatile MinecraftServer server;
    private static volatile Recording recording;
    private static volatile Path output;
    private static volatile ScheduledExecutorService sampler;
    private static volatile double latestProcessCpuLoad;

    public static double latestProcessCpuLoad() {
        return latestProcessCpuLoad;
    }

    private static final List<JsonObject> SAMPLES = new CopyOnWriteArrayList<>();
    private static final AtomicLong LAST_DUMP = new AtomicLong();
    private static final AtomicInteger RUNS = new AtomicInteger();
    private static final MosaicSeedResolver RESOLVER =
            new MosaicSeedResolver(MosaicWorldProfile.current());

    @Name("mosaic.ChunkPhase")
    @Label("Physical Chunk phase")
    @Category("Mosaic")
    @StackTrace(false)
    public static class PhaseEvent extends Event {
        public String key, phase, detail;
        public int chunkX, chunkZ;
        public long elapsedNanos;
    }

    @Name("mosaic.SyncSpan")
    @Label("Synchronous production call")
    @Category("Mosaic")
    @StackTrace(false)
    public static class SyncSpan extends Event {
        public String key, phase;
    }

    private static final class Timeline {
        final String key, dimension;
        final int x, z;
        final long seed;
        final boolean mosaic;
        final ConcurrentHashMap<String, Long> first = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Long> last = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();

        Timeline(ServerLevel level, ChunkPos pos) {
            key = key(level, pos);
            dimension = level.dimension().identifier().toString();
            x = pos.x();
            z = pos.z();
            mosaic = level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator;
            long master = level.getServer().getWorldGenSettings().options().seed();
            seed = mosaic ? RESOLVER.resolveLocalWorldSeed(master, level.dimension(), pos) : master;
        }
    }

    private PerfTrace() {}

    public static String key(ServerLevel level, ChunkPos pos) {
        return level.dimension().identifier() + ":" + pos.x() + "," + pos.z();
    }

    public static synchronized void start(MinecraftServer value) {
        if (recording != null) return;
        server = value;
        StructureIsolation.arm(value);
        try {
            CHUNKS.clear();
            OWNERS.clear();
            SAMPLES.clear();
            SCOPES.clear();
            PURPOSES.clear();
            AGGREGATES.clear();
            ACTIVE.set(0);
            String label = System.getProperty("mosaic.profile.label", "manual");
            output =
                    Path.of(
                            System.getProperty(
                                    "mosaic.profile.output",
                                    Path.of(
                                                    System.getProperty(
                                                            "validation.result",
                                                            Path.of(
                                                                            System.getProperty(
                                                                                    "mosaic.repositoryRoot",
                                                                                    "."),
                                                                            "validations/performance-worldgen/result")
                                                                    .toString()),
                                                    "current",
                                                    "performance")
                                            .toString()));
            RUNS.incrementAndGet();
            Files.createDirectories(output);
            recording = new Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            recording.setName("Mosaic end-to-end " + label);
            recording.setToDisk(true);
            recording.setMaxSize(512L * 1024 * 1024);
            recording.setDumpOnExit(true);
            recording.setDestination(output.resolve("recording.jfr"));
            recording.enable(PhaseEvent.class);
            recording.enable(SyncSpan.class);
            recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(5));
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(5));
            recording.enable("jdk.FileRead").withThreshold(Duration.ofMillis(10));
            recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(10));
            recording.start();
            sampler =
                    Executors.newSingleThreadScheduledExecutor(
                            r -> {
                                Thread t = new Thread(r, "mosaic-profile-observer");
                                t.setDaemon(true);
                                return t;
                            });
            sampler.scheduleAtFixedRate(PerfTrace::sample, 1, 1, TimeUnit.SECONDS);
            System.out.println(
                    "MOSAIC PROFILE START pid="
                            + ProcessHandle.current().pid()
                            + " output="
                            + output);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start requested profiler", e);
        }
    }

    public static void bind(Object object, ServerLevel level) {
        OWNERS.put(object, level);
    }

    public static ServerLevel owner(Object object) {
        return OWNERS.get(object);
    }

    public static void event(ServerLevel level, ChunkPos pos, String phase, String detail) {
        if (recording == null || level == null) return;
        String key = key(level, pos);
        Timeline t = CHUNKS.computeIfAbsent(key, k -> new Timeline(level, pos));
        long now = System.nanoTime() - ORIGIN;
        t.first.putIfAbsent(phase, now);
        t.last.put(phase, now);
        t.counts.computeIfAbsent(phase, k -> new LongAdder()).increment();
        PhaseEvent event = new PhaseEvent();
        event.key = key;
        event.chunkX = pos.x();
        event.chunkZ = pos.z();
        event.phase = phase;
        event.detail = detail;
        event.elapsedNanos = now;
        event.commit();
    }

    public static void event(Object owner, ChunkPos pos, String phase, String detail) {
        event(owner(owner), pos, phase, detail);
    }

    public static SyncSpan begin(ServerLevel level, ChunkPos pos, String phase) {
        event(level, pos, phase + "_BEGIN", Thread.currentThread().getName());
        SyncSpan span = new SyncSpan();
        span.key = level == null ? "unknown" : key(level, pos);
        span.phase = phase;
        span.begin();
        return span;
    }

    public static void end(ServerLevel level, ChunkPos pos, String phase, SyncSpan span) {
        span.end();
        span.commit();
        event(level, pos, phase + "_END", "");
    }

    public static void active(ServerLevel level, ChunkPos pos, boolean begin) {
        if (begin) {
            ACTIVE.incrementAndGet();
            SCOPES.put(Thread.currentThread(), key(level, pos));
        } else {
            ACTIVE.decrementAndGet();
            SCOPES.remove(Thread.currentThread());
        }
    }

    public static void purpose(String value) {
        if (value == null) PURPOSES.remove(Thread.currentThread());
        else PURPOSES.put(Thread.currentThread(), value);
    }

    public static String purpose() {
        return PURPOSES.getOrDefault(Thread.currentThread(), "canonical");
    }

    public static void count(String key, long value) {
        if (recording != null) AGGREGATES.computeIfAbsent(key, k -> new LongAdder()).add(value);
    }

    public static void profileIo(MinecraftServer owner, long nanos) {
        synchronized (PROFILE_IO) {
            long[] totals = PROFILE_IO.computeIfAbsent(owner, k -> new long[2]);
            totals[0]++;
            totals[1] += nanos;
        }
    }

    public static String currentKey() {
        return SCOPES.get(Thread.currentThread());
    }

    public static void planLevel(ServerLevel level) {
        if (level == null) PLAN_LEVELS.remove(Thread.currentThread());
        else PLAN_LEVELS.put(Thread.currentThread(), level);
    }

    public static ServerLevel planLevel() {
        return PLAN_LEVELS.get(Thread.currentThread());
    }

    public static void keyEvent(String key, String phase) {
        if (key == null) return;
        Timeline t = CHUNKS.get(key);
        if (t == null) return;
        long now = System.nanoTime() - ORIGIN;
        t.first.putIfAbsent(phase, now);
        t.last.put(phase, now);
        t.counts.computeIfAbsent(phase, k -> new LongAdder()).increment();
        PhaseEvent e = new PhaseEvent();
        e.key = key;
        e.chunkX = t.x;
        e.chunkZ = t.z;
        e.phase = phase;
        e.detail = "";
        e.elapsedNanos = now;
        e.commit();
    }

    public static <T> void observe(
            CompletableFuture<T> future, ServerLevel level, ChunkPos pos, String phase) {
        future.whenComplete(
                (result, error) -> {
                    boolean ok =
                            error == null
                                    && (!(result instanceof ChunkResult<?> cr) || cr.isSuccess());
                    event(
                            level,
                            pos,
                            ok ? phase : phase + "_FAILED",
                            error != null
                                    ? error.toString()
                                    : result instanceof ChunkResult<?> cr && !cr.isSuccess()
                                            ? cr.getError()
                                            : "ok");
                });
    }

    public static void marker(String name) {
        PhaseEvent e = new PhaseEvent();
        e.key = "WORKLOAD";
        e.phase = name;
        e.detail = "";
        e.elapsedNanos = System.nanoTime() - ORIGIN;
        e.commit();
        System.out.println("MOSAIC PROFILE MARKER " + name);
    }

    private static void sample() {
        try {
            JsonObject sample = new JsonObject();
            sample.addProperty("seconds", (System.nanoTime() - ORIGIN) / 1e9);
            var os =
                    (com.sun.management.OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            latestProcessCpuLoad = os.getProcessCpuLoad();
            sample.addProperty("processCpuLoad", latestProcessCpuLoad);
            sample.addProperty("processCpuNanos", os.getProcessCpuTime());
            sample.addProperty("processors", os.getAvailableProcessors());
            sample.addProperty(
                    "heapUsed", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
            if (server != null && server.getPlayerList() != null) {
                sample.addProperty("viewDistance", server.getPlayerList().getViewDistance());
                sample.addProperty(
                        "simulationDistance", server.getPlayerList().getSimulationDistance());
                var players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    var player = players.getFirst();
                    var center = player.chunkPosition();
                    int radius = server.getPlayerList().getViewDistance(), readyCount = 0;
                    String dimension = player.level().dimension().identifier().toString();
                    for (int z = center.z() - radius; z <= center.z() + radius; z++)
                        for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                            Timeline t = CHUNKS.get(dimension + ":" + x + "," + z);
                            if (t == null) continue;
                            Long ready = t.last.get("READY_TO_SEND"),
                                    gone = t.last.get("FULL_STATUS_INACCESSIBLE");
                            if (ready != null && (gone == null || ready > gone)) readyCount++;
                        }
                    sample.addProperty("playerChunkX", center.x());
                    sample.addProperty("playerChunkZ", center.z());
                    sample.addProperty("playerDimension", dimension);
                    sample.addProperty("currentViewportReady", readyCount);
                    sample.addProperty("currentViewportSize", (radius * 2 + 1) * (radius * 2 + 1));
                }
            }
            sample.addProperty("activePrepare", ACTIVE.get());
            sample.addProperty("knownPhysicalChunks", CHUNKS.size());
            int submitted = 0, started = 0, finished = 0, full = 0, ready = 0;
            for (Timeline t : CHUNKS.values()) {
                if (t.first.containsKey("ARTIFACT_REQUEST")) submitted++;
                if (t.first.containsKey("PREPARE_BEGIN")) started++;
                if (t.first.containsKey("PREPARE_END")) finished++;
                if (t.first.containsKey("STAGE_FULL_END")) full++;
                if (t.first.containsKey("READY_TO_SEND")) ready++;
            }
            sample.addProperty("canonicalRequested", submitted);
            sample.addProperty("canonicalStarted", started);
            sample.addProperty("canonicalCompleted", finished);
            sample.addProperty("queuedPrepare", submitted - started);
            sample.addProperty("full", full);
            sample.addProperty("readyToSend", ready);
            var metrics = MosaicPhysicalMaterializer.metrics();
            sample.addProperty("dedupHits", metrics.dedupHits());
            sample.addProperty("activePlans", metrics.requestedTargetCount());
            sample.addProperty("planCalls", metrics.planCount());
            sample.addProperty("artifactCaptures", metrics.artifactCaptureCount());
            sample.addProperty("inFlight", metrics.inFlightCount());
            JsonObject totals = new JsonObject();
            AGGREGATES.forEach((k, v) -> totals.addProperty(k, v.sum()));
            sample.add("aggregates", totals);
            JsonObject states = new JsonObject();
            Map<String, Integer> counts = new TreeMap<>();
            var bean = ManagementFactory.getThreadMXBean();
            for (var info : bean.getThreadInfo(bean.getAllThreadIds(), 0))
                if (info != null) {
                    counts.merge(info.getThreadState().toString(), 1, Integer::sum);
                    if (info.getThreadName().equals("Server thread"))
                        sample.addProperty(
                                "serverCpuNanos", bean.getThreadCpuTime(info.getThreadId()));
                }
            counts.forEach(states::addProperty);
            sample.add("threadStates", states);
            SAMPLES.add(sample);
            long now = System.nanoTime();
            if (now - LAST_DUMP.get() > 10_000_000_000L) {
                LAST_DUMP.set(now);
                dump(false);
            }
        } catch (Throwable e) {
            System.err.println("PROFILE OBSERVER ERROR " + e);
        }
    }

    public static synchronized void dump(boolean finalDump) {
        if (output == null) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("integrated", server != null && !server.isDedicatedServer());
            root.addProperty("final", finalDump);
            root.addProperty("processId", ProcessHandle.current().pid());
            root.addProperty("javaRuntime", System.getProperty("java.runtime.version"));
            root.addProperty("javaVendor", System.getProperty("java.vendor"));
            root.addProperty("availableProcessors", Runtime.getRuntime().availableProcessors());
            root.add(
                    "jvmArguments",
                    new Gson()
                            .toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
            root.addProperty("uptimeNanos", System.nanoTime() - ORIGIN);
            root.add("samples", new Gson().toJsonTree(SAMPLES));
            root.add("structureExperiment", new Gson().toJsonTree(StructureIsolation.snapshot()));
            root.add(
                    "physicalBiomes",
                    new Gson()
                            .toJsonTree(
                                    io.github.recrivenvi.randomnibble6plus24generator.worldgen
                                            .materialization.PhysicalBiomePreparation.metrics()));
            JsonObject totals = new JsonObject();
            AGGREGATES.forEach((k, v) -> totals.addProperty(k, v.sum()));
            root.add("aggregates", totals);
            synchronized (PROFILE_IO) {
                long[] io = PROFILE_IO.getOrDefault(server, new long[2]);
                root.addProperty("lifecycleProfileFileCalls", io[0]);
                root.addProperty("lifecycleProfileFileNanos", io[1]);
            }
            JsonArray chunks = new JsonArray();
            for (Timeline t : CHUNKS.values()) {
                JsonObject c = new JsonObject();
                c.addProperty("key", t.key);
                c.addProperty("dimension", t.dimension);
                c.addProperty("x", t.x);
                c.addProperty("z", t.z);
                c.addProperty("localSeed", Long.toString(t.seed));
                c.addProperty("mosaic", t.mosaic);
                c.add("firstNanos", new Gson().toJsonTree(t.first));
                c.add("lastNanos", new Gson().toJsonTree(t.last));
                JsonObject counts = new JsonObject();
                t.counts.forEach((k, v) -> counts.addProperty(k, v.sum()));
                c.add("counts", counts);
                chunks.add(c);
            }
            root.add("chunks", chunks);
            Files.writeString(output.resolve("timeline.json"), new Gson().toJson(root));
        } catch (Exception e) {
            System.err.println("PROFILE DUMP ERROR " + e);
        }
    }

    public static synchronized void stop() {
        if (recording == null) return;
        if (sampler != null) sampler.shutdown();
        dump(true);
        try {
            recording.stop();
            if (recording.getState() != RecordingState.CLOSED) recording.close();
        } catch (Exception e) {
            System.err.println("PROFILE JFR CLOSE ERROR " + e);
        } finally {
            recording = null;
        }
    }

    public static synchronized void checkpoint() {
        if (recording == null) return;
        try {
            Files.copy(
                    output.resolve("timeline.json"),
                    output.resolve("checkpoint-timeline.json"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            recording.dump(output.resolve("checkpoint.jfr"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
