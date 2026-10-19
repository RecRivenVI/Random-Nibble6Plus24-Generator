package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.JsonObject;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Forces one legal late-registration interleaving; never loaded in the product JAR. */
public final class CompletionHandoffEvidence {
    private static final String FLAG = ModConstants.MOD_ID + ".phase3c2.production.completionOrder";
    private static final AtomicInteger FORCED = new AtomicInteger();
    private static final AtomicInteger COMPLETED = new AtomicInteger();
    private static final AtomicInteger WRONG_THREAD = new AtomicInteger();
    private static final Set<String> PUBLICATION_THREADS = new ConcurrentSkipListSet<>();
    private static volatile ServerLevel level;
    private static volatile ChunkPos target;
    private static volatile String registeringThread;

    private CompletionHandoffEvidence() {}

    public static void start(ServerLevel owner, ChunkPos position) {
        if (!Boolean.getBoolean(FLAG)) return;
        if (!ProductionChunkVerifier.enabled()
                || Boolean.getBoolean(ModConstants.MOD_ID + ".phase3c2.production.vanillaControl"))
            throw new IllegalStateException("Completion-order probe requires its owned Mosaic run");
        level = owner;
        target = position;
    }

    public static CompletableFuture<ChunkAccess> beforeHandle(
            GenerationChunkHolder holder,
            ChunkStatus status,
            CompletableFuture<ChunkAccess> future) {
        if (level == null
                || status != ChunkStatus.EMPTY
                || !holder.getPos().equals(target)
                || !FORCED.compareAndSet(0, 1)) return future;
        if (level.getServer().isSameThread())
            throw new IllegalStateException("Probe did not reach the native worldgen caller");
        // The owner's native main queue keeps running. Only this focus worker waits; no
        // replacement chunk, generated data or callback is supplied by the probe.
        future.join();
        if (!future.isDone()) throw new AssertionError("Input future did not complete");
        registeringThread = Thread.currentThread().getName();
        System.out.println(
                "MOSAIC_COMPLETION_ORDER input-complete-before-handle target="
                        + target
                        + " registeringThread="
                        + registeringThread);
        return future;
    }

    public static void completed(GenerationChunkHolder holder) {
        var owner = level;
        if (owner == null || !holder.getPos().equals(target)) return;
        COMPLETED.incrementAndGet();
        PUBLICATION_THREADS.add(Thread.currentThread().getName());
        if (!owner.getServer().isSameThread()) WRONG_THREAD.incrementAndGet();
    }

    public static JsonObject verify() {
        var result = snapshot();
        if (level == null) return result;
        if (FORCED.get() != 1
                || registeringThread == null
                || COMPLETED.get() == 0
                || WRONG_THREAD.get() != 0)
            throw new IllegalStateException("Holder completion thread proof failed " + result);
        return result;
    }

    public static JsonObject snapshot() {
        var result = new JsonObject();
        if (level == null) return result;
        result.addProperty("forcedCompletedInputBeforeHandle", FORCED.get());
        result.addProperty("registeringThread", registeringThread);
        result.addProperty("nativeHolderCompletions", COMPLETED.get());
        result.addProperty("offOwnerThreadCompletions", WRONG_THREAD.get());
        result.addProperty("publicationThreads", String.join(",", PUBLICATION_THREADS));
        return result;
    }
}
