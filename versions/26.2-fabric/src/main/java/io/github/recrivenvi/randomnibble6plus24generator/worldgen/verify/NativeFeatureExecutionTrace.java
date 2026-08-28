package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.level.ChunkPos;

/** Process-scoped trace for one explicit native canonical FEATURES procedure. */
public final class NativeFeatureExecutionTrace {

    public record Result(
            List<ChunkPos> requestedWriters,
            List<ChunkPos> completedWriters,
            int maxConcurrentFeatureWriters,
            long decorationSeedReads,
            long featureSeedInvocationCount,
            long featureSeedSequenceHash,
            List<String> featureVisibleBiomeSequence) {
        public Result {
            requestedWriters = List.copyOf(requestedWriters);
            completedWriters = List.copyOf(completedWriters);
            featureVisibleBiomeSequence = List.copyOf(featureVisibleBiomeSequence);
        }
    }

    private static final AtomicReference<Session> ACTIVE = new AtomicReference<>();

    private NativeFeatureExecutionTrace() {
    }

    public static void begin(List<ChunkPos> expectedWriters) {
        Session session = new Session(expectedWriters);
        if (!ACTIVE.compareAndSet(null, session)) {
            throw new IllegalStateException("Native FEATURES execution trace is already active");
        }
    }

    public static boolean active() {
        return ACTIVE.get() != null;
    }

    public static void beginWriter(ChunkPos writer) {
        Session session = ACTIVE.get();
        if (session == null) return;
        if (!session.expectedWriters.contains(writer)) {
            throw new IllegalStateException("Native FEATURES writer outside canonical frontier: " + writer);
        }
        session.requestedWriters.add(writer);
        int active = session.activeWriters.incrementAndGet();
        session.maxConcurrentWriters.accumulateAndGet(active, Math::max);
    }

    public static void completeWriter(ChunkPos writer) {
        Session session = ACTIVE.get();
        if (session == null) return;
        session.completedWriters.add(writer);
        int active = session.activeWriters.decrementAndGet();
        if (active != 0) {
            throw new IllegalStateException("Native FEATURES writer accounting imbalance after " + writer);
        }
    }

    public static void recordDecorationSeedRead() {
        Session session = ACTIVE.get();
        if (session != null) session.decorationSeedReads.incrementAndGet();
    }

    public static void recordFeatureVisibleBiomes(String signature) {
        Session session = ACTIVE.get();
        if (session != null) session.featureVisibleBiomeSequence.add(signature);
    }

    public static void recordFeatureSeed(long decorationSeed, int featureIndex, int step) {
        Session session = ACTIVE.get();
        if (session == null) return;
        session.featureSeedInvocationCount.incrementAndGet();
        session.featureSeedSequenceHash.updateAndGet(current -> {
            long mixed = current ^ decorationSeed;
            mixed *= 0x100000001b3L;
            mixed ^= Integer.toUnsignedLong(featureIndex);
            mixed *= 0x100000001b3L;
            mixed ^= Integer.toUnsignedLong(step);
            return mixed * 0x100000001b3L;
        });
    }

    public static Result finish() {
        Session session = ACTIVE.getAndSet(null);
        if (session == null) throw new IllegalStateException("No active native FEATURES execution trace");
        if (!session.requestedWriters.equals(session.expectedWriters)
                || !session.completedWriters.equals(session.expectedWriters)
                || session.activeWriters.get() != 0
                || session.maxConcurrentWriters.get() != 1) {
            throw new IllegalStateException(
                    "Native FEATURES canonical order violation expected=" + session.expectedWriters
                            + ", requested=" + session.requestedWriters
                            + ", completed=" + session.completedWriters
                            + ", active=" + session.activeWriters
                            + ", maxConcurrent=" + session.maxConcurrentWriters);
        }
        return new Result(
                session.requestedWriters,
                session.completedWriters,
                session.maxConcurrentWriters.get(),
                session.decorationSeedReads.get(),
                session.featureSeedInvocationCount.get(),
                session.featureSeedSequenceHash.get(),
                session.featureVisibleBiomeSequence);
    }

    public static void abort() {
        ACTIVE.set(null);
    }

    private static final class Session {
        private final List<ChunkPos> expectedWriters;
        private final List<ChunkPos> requestedWriters = new CopyOnWriteArrayList<>();
        private final List<ChunkPos> completedWriters = new CopyOnWriteArrayList<>();
        private final AtomicInteger activeWriters = new AtomicInteger();
        private final AtomicInteger maxConcurrentWriters = new AtomicInteger();
        private final AtomicLong decorationSeedReads = new AtomicLong();
        private final List<String> featureVisibleBiomeSequence = new CopyOnWriteArrayList<>();
        private final AtomicLong featureSeedInvocationCount = new AtomicLong();
        private final AtomicLong featureSeedSequenceHash = new AtomicLong(0xcbf29ce484222325L);

        private Session(List<ChunkPos> expectedWriters) {
            this.expectedWriters = List.copyOf(expectedWriters);
        }
    }
}
