package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** One ServerLevel's close boundary. It is neither a scheduler nor a global cancellation filter. */
public final class MosaicGenerationLifecycle {
    private volatile boolean active;
    private volatile boolean closing;
    private int workers;
    private long expectedTerminations;
    private final java.util.concurrent.CompletableFuture<Void> closedWorkers = new java.util.concurrent.CompletableFuture<>();

    /** Called only after the authoritative Mosaic identity check. */
    public synchronized boolean activate() {
        active = true;
        return !closing;
    }

    public boolean active() { return active; }
    public boolean closing() { return closing; }

    public synchronized void beginClosing() {
        closing = true;
        if (workers == 0) closedWorkers.complete(null);
    }

    public synchronized void workerAccepted() {
        checkPreparing();
        workers++;
    }

    public synchronized void workerFinished() {
        if (--workers < 0) throw new IllegalStateException("Mosaic generation worker released twice");
        if (closing && workers == 0) closedWorkers.complete(null);
    }

    /** Exceptional shutdown cannot pump an event loop that already holds a genuine delayed crash. */
    public void awaitClosedWorkers() {
        if (!closing) throw new IllegalStateException("World has not begun closing");
        closedWorkers.join();
    }

    public synchronized boolean quiescent() { return workers == 0; }

    /** Safe checkpoints before computation and before beginning the physical handoff transaction. */
    public void checkPreparing() {
        if (closing) throw cancellation();
    }

    public CancellationException cancellation() {
        if (!closing) throw new IllegalStateException("No Mosaic lifecycle close is in progress");
        return new LifecycleCancellation(this);
    }

    public boolean isExpected(Throwable failure) {
        Throwable root = failure;
        while ((root instanceof CompletionException || root instanceof ExecutionException)
                && root.getCause() != null) root = root.getCause();
        return root instanceof LifecycleCancellation cancellation
                && cancellation.lifecycle == this && closing;
    }

    public synchronized void recordExpectedTermination() { expectedTerminations++; }

    public synchronized Snapshot snapshot() {
        return new Snapshot(active, closing, workers, expectedTerminations);
    }

    public record Snapshot(boolean active, boolean closing, int workers, long expectedTerminations) {}

    private static final class LifecycleCancellation extends CancellationException {
        private final MosaicGenerationLifecycle lifecycle;

        private LifecycleCancellation(MosaicGenerationLifecycle lifecycle) {
            super("Mosaic generation terminated by its world shutdown lifecycle");
            this.lifecycle = lifecycle;
        }
    }
}
