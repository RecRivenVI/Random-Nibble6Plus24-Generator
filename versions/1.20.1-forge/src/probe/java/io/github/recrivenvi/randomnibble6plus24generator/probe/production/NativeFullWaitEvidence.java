package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.*;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin.PhysicalHolderAccess;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin.PhysicalStateAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Reads the actual pending graph and JVM stacks; never schedules work or extends the deadline. */
final class NativeFullWaitEvidence {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static long lastSample = -1;
    private static Recording recording;

    private NativeFullWaitEvidence() {}

    static void observe(
            ServerLevel level, long started, Path folder, CompletableFuture<?> request) {
        long elapsed = System.nanoTime() - started;
        long sample = elapsed / 30_000_000_000L;
        if (sample == lastSample) return;
        lastSample = sample;
        if (recording == null) {
            try {
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.setDestination(folder.resolve("full-wait.jfr"));
                recording.setDumpOnExit(true);
                recording.start();
            } catch (IOException | java.text.ParseException failure) {
                throw new IllegalStateException(
                        "Cannot start game-owned FULL wait recording", failure);
            }
        }
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("scope", "READ_ONLY_ACTUAL_PENDING_GRAPH_NOT_A_PERFORMANCE_BENCHMARK");
        result.addProperty("elapsedNanos", elapsed);
        result.addProperty("pid", ProcessHandle.current().pid());
        result.addProperty("requestDone", request.isDone());
        result.addProperty("requestExceptional", request.isCompletedExceptionally());
        var state = MosaicPhysicalLevelOwner.forLevel(level).mosaic$physicalState();
        result.add("physical", JSON.toJsonTree(state.metrics()));
        var access = (PhysicalStateAccess) (Object) state;
        var requirements = new HashMap<ChunkPos, ChunkStatus>();
        synchronized (state) {
            result.addProperty("explicitTargets", access.mosaic$requested().size());
            result.addProperty("actualPlans", access.mosaic$plans().size());
            result.addProperty("artifactObligations", access.mosaic$obligations().size());
            access.mosaic$plans()
                    .values()
                    .forEach(
                            plan ->
                                    plan.forEach(
                                            (pos, status) ->
                                                    requirements.merge(
                                                            pos,
                                                            status,
                                                            (a, b) -> a.isOrAfter(b) ? a : b)));
        }
        var holders = (PhysicalHolderAccess) level.getChunkSource().chunkMap;
        var nodes = new JsonArray();
        requirements.entrySet().stream()
                .sorted(
                        Comparator.comparingInt(
                                        (Map.Entry<ChunkPos, ChunkStatus> e) -> e.getKey().z)
                                .thenComparingInt(e -> e.getKey().x))
                .forEach(
                        entry -> {
                            var node = new JsonObject();
                            node.addProperty("x", entry.getKey().x);
                            node.addProperty("z", entry.getKey().z);
                            node.addProperty("required", entry.getValue().toString());
                            var holder = holders.mosaic$holder(entry.getKey().toLong());
                            node.addProperty("holderPresent", holder != null);
                            if (holder != null) {
                                node.addProperty("ticketLevel", holder.getTicketLevel());
                                var future = holder.getFutureIfPresentUnchecked(entry.getValue());
                                node.addProperty("done", future.isDone());
                                node.addProperty("exceptional", future.isCompletedExceptionally());
                                if (future.isDone() && !future.isCompletedExceptionally()) {
                                    var value = future.getNow(null);
                                    node.addProperty(
                                            "availableStatus",
                                            value.left()
                                                    .map(chunk -> chunk.getStatus().toString())
                                                    .orElse("UNLOADED"));
                                }
                            }
                            nodes.add(node);
                        });
        result.add("dependencies", nodes);
        var bean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = bean.findDeadlockedThreads();
        result.add(
                "deadlockedThreadIds",
                JSON.toJsonTree(deadlocked == null ? new long[0] : deadlocked));
        var threads = new JsonArray();
        for (var thread : bean.dumpAllThreads(true, true)) {
            var row = new JsonObject();
            row.addProperty("id", thread.getThreadId());
            row.addProperty("name", thread.getThreadName());
            row.addProperty("state", thread.getThreadState().name());
            row.addProperty("cpuNanos", bean.getThreadCpuTime(thread.getThreadId()));
            row.addProperty("lockOwnerId", thread.getLockOwnerId());
            row.addProperty("lock", thread.getLockName());
            var stack = new JsonArray();
            for (var frame : thread.getStackTrace()) stack.add(frame.toString());
            row.add("stack", stack);
            threads.add(row);
        }
        result.add("threads", threads);
        try {
            Files.writeString(
                    folder.resolve("pending-" + sample + ".json"),
                    JSON.toJson(result) + "\n",
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static void close() {
        if (recording != null) {
            recording.stop();
            recording.close();
            recording = null;
        }
    }
}
