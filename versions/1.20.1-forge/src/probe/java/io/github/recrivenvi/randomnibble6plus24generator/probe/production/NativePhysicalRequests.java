package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Concurrent native requests, not a replacement generation executor or parallel-session proof. */
final class NativePhysicalRequests {
    private static final AtomicInteger PENDING = new AtomicInteger(),
            PEAK = new AtomicInteger(),
            COMPLETED = new AtomicInteger();
    private static final AtomicLong FIRST_COMPLETION = new AtomicLong(Long.MAX_VALUE);
    private static volatile long allIssued;
    private static volatile int issued;
    private static volatile boolean callerOffThread, identical;

    private NativePhysicalRequests() {}

    static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> submit(
            ServerLevel level, ChunkPos pos, ChunkStatus status) {
        int count = Integer.getInteger(NativePhysicalProbe.PREFIX + "requests", 1);
        if (count != 1 && count != 8 && count != 16 && count != 32)
            throw new IllegalArgumentException("Unsupported native request burst");
        return CompletableFuture.supplyAsync(
                        () -> {
                            callerOffThread = !level.getServer().isSameThread();
                            if (!callerOffThread)
                                throw new IllegalStateException(
                                        "Native request burst ran on server thread");
                            var requests =
                                    new ArrayList<
                                            CompletableFuture<
                                                    Either<
                                                            ChunkAccess,
                                                            ChunkHolder.ChunkLoadingFailure>>>();
                            for (int i = 0; i < count; i++) {
                                PEAK.accumulateAndGet(PENDING.incrementAndGet(), Math::max);
                                requests.add(
                                        level.getChunkSource()
                                                .getChunkFuture(pos.x, pos.z, status, true)
                                                .whenComplete(
                                                        (chunk, error) -> {
                                                            FIRST_COMPLETION.accumulateAndGet(
                                                                    System.nanoTime(), Math::min);
                                                            COMPLETED.incrementAndGet();
                                                            PENDING.decrementAndGet();
                                                        }));
                                issued++;
                            }
                            allIssued = System.nanoTime();
                            return requests;
                        })
                .thenCompose(
                        requests ->
                                CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
                                        .thenApply(
                                                ignored -> {
                                                    var first = requests.get(0).join();
                                                    var target =
                                                            first.left()
                                                                    .orElseThrow(
                                                                            () ->
                                                                                    new IllegalStateException(
                                                                                            "Native request became unloaded"));
                                                    for (var future : requests)
                                                        if (future.join().left().orElseThrow()
                                                                != target)
                                                            throw new IllegalStateException(
                                                                    "Same-target requests returned different physical Chunks");
                                                    identical = true;
                                                    return first;
                                                }));
    }

    static JsonObject evidence() {
        var result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("scope", "SAME_TARGET_PENDING_REQUESTS_NOT_PARALLEL_SESSION_PROOF");
        result.addProperty(
                "requested", Integer.getInteger(NativePhysicalProbe.PREFIX + "requests", 1));
        result.addProperty("issued", issued);
        result.addProperty("completed", COMPLETED.get());
        result.addProperty("pending", PENDING.get());
        result.addProperty("peakPending", PEAK.get());
        result.addProperty("allIssuedBeforeFirstCompletion", allIssued < FIRST_COMPLETION.get());
        result.addProperty("callerOffThread", callerOffThread);
        result.addProperty("samePhysicalIdentity", identical);
        return result;
    }
}
