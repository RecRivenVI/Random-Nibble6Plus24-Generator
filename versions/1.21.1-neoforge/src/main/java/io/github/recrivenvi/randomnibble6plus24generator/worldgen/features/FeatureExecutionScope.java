package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;

/** Synchronous, target-owned decoration routing. Normal runtime simulation never binds it. */
public final class FeatureExecutionScope implements AutoCloseable {
    private static final ThreadLocal<FeatureExecutionScope> ACTIVE = new ThreadLocal<>();
    private static final AtomicInteger BINDINGS = new AtomicInteger();
    private final Thread owner = Thread.currentThread();
    private final ServerLevel host;
    private final StrictFeatureRegion region;
    private final StructureManager structures;
    private boolean closed;
    private long routedSeedReads;
    private long routedStructureReads;

    private FeatureExecutionScope(
            ServerLevel host, StrictFeatureRegion region, StructureManager structures) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested strict B writer");
        this.host = java.util.Objects.requireNonNull(host);
        this.region = java.util.Objects.requireNonNull(region);
        this.structures = java.util.Objects.requireNonNull(structures);
        ACTIVE.set(this);
        BINDINGS.incrementAndGet();
    }

    static FeatureExecutionScope bind(
            ServerLevel host, StrictFeatureRegion region, StructureManager structures) {
        return new FeatureExecutionScope(host, region, structures);
    }

    public static FeatureExecutionScope current(ServerLevel level) {
        var scope = ACTIVE.get();
        if (scope != null && scope.host != level)
            throw new IllegalArgumentException("Foreign B resource host");
        return scope;
    }

    private void requireActive() {
        if (closed || ACTIVE.get() != this)
            throw new IllegalStateException("Inactive B writer scope");
    }

    public long localSeed() {
        requireActive();
        routedSeedReads++;
        return region.getSeed();
    }

    public StructureManager structures() {
        requireActive();
        routedStructureReads++;
        return structures;
    }

    public long seedReads() {
        return routedSeedReads;
    }

    public long structureReads() {
        return routedStructureReads;
    }

    public static int bindingCount() {
        return BINDINGS.get();
    }

    @Override
    public void close() {
        if (closed || Thread.currentThread() != owner || ACTIVE.get() != this)
            throw new IllegalStateException("Invalid B writer scope close");
        ACTIVE.remove();
        BINDINGS.decrementAndGet();
        closed = true;
    }
}
