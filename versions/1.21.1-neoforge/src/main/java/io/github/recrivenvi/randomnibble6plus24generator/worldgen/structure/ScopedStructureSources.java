package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;

/** B-preparation-local native starts, including evaluated empties; never a terrain cache. */
public final class ScopedStructureSources implements AutoCloseable {
    private LevelHeightAccessor world;
    private Object generator, state;
    private NativeBiomeSearchState biomeState;
    private final long seed;
    private final ChunkPos target;
    private final int queryRadius, referenceRadius;
    private final Thread thread = Thread.currentThread();

    private record Entry(StructureInputChunk carrier, Object input, Object output) {}

    private final Map<Long, Entry> sources = new HashMap<>();
    private final Set<Long> evaluated = new HashSet<>();
    private boolean handedOff, closed, climateState;
    private long queries, hits, evaluations, evaluationNanos;
    private long stateMismatches, stateReplays;
    private int peakResident, transferred, uniqueSources;

    /** This Target's unproved optimization is opt-in and limited to the audited native guard. */
    public static ScopedStructureSources candidate(StructureGenerationContext context) {
        if (!Boolean.getBoolean(ModConstants.MOD_ID + ".structure.reuseSources")
                || !context.narrowTemplateLocks()
                || !NativeBiomeSearchState.supports(context.generator().getBiomeSource()))
            return null;
        var input = NativeBiomeSearchState.from(context.generator().getBiomeSource());
        return input == null ? null : new ScopedStructureSources(context, input);
    }

    private ScopedStructureSources(
            StructureGenerationContext context, NativeBiomeSearchState input) {
        this(
                context.host(),
                context.seed(),
                context.target(),
                StructureGenerationContext.sharedPreparationRadius(),
                CompactStructureInputs.referenceRadius(),
                input);
        generator = context.generator();
        state = context.state();
    }

    ScopedStructureSources(
            LevelHeightAccessor world,
            long seed,
            ChunkPos target,
            int queryRadius,
            int referenceRadius) {
        this(world, seed, target, queryRadius, referenceRadius, new NativeBiomeSearchState(null));
    }

    ScopedStructureSources(
            LevelHeightAccessor world,
            long seed,
            ChunkPos target,
            int queryRadius,
            int referenceRadius,
            NativeBiomeSearchState biomeState) {
        this.world = Objects.requireNonNull(world);
        this.seed = seed;
        this.target = Objects.requireNonNull(target);
        if (queryRadius != 2) throw new IllegalArgumentException("Unaudited B source query radius");
        if (referenceRadius != 8)
            throw new IllegalArgumentException("Unaudited native source reference radius");
        this.queryRadius = queryRadius;
        this.referenceRadius = referenceRadius;
        this.biomeState = Objects.requireNonNull(biomeState);
        climateState = biomeState.hasTree();
    }

    void requireCompatible(StructureGenerationContext context) {
        open();
        if (world != context.host()
                || seed != context.seed()
                || generator != context.generator()
                || state != context.state())
            throw new IllegalArgumentException("Foreign structure source world/seed/configuration");
        within(context.target(), target, queryRadius);
    }

    StructureInputChunk source(
            StructureGenerationContext context,
            ChunkPos source,
            Supplier<StructureInputChunk> evaluate) {
        requireCompatible(context);
        return source(context.host(), context.seed(), context.target(), source, evaluate);
    }

    StructureInputChunk source(
            LevelHeightAccessor world,
            long seed,
            ChunkPos query,
            ChunkPos source,
            Supplier<StructureInputChunk> evaluate) {
        open();
        if (world != this.world || seed != this.seed)
            throw new IllegalArgumentException("Foreign structure source world/seed");
        within(query, target, queryRadius);
        within(source, query, referenceRadius);
        queries++;
        Object input = biomeState.capture();
        var existing = sources.get(source.toLong());
        if (existing != null && existing.input() == input) {
            hits++;
            // Returning the same start is insufficient: preserve native next-query continuation.
            biomeState.replay(existing.output());
            if (biomeState.hasTree()) stateReplays++;
            return existing.carrier();
        }
        if (existing != null) stateMismatches++;
        long began = System.nanoTime();
        var result = Objects.requireNonNull(evaluate.get());
        if (!source.equals(result.getPos()) || !result.getAllReferences().isEmpty())
            throw new IllegalArgumentException("Expected raw native source starts only");
        evaluationNanos += System.nanoTime() - began;
        evaluations++;
        if (evaluated.add(source.toLong())) uniqueSources++;
        sources.put(source.toLong(), new Entry(result, input, biomeState.capture()));
        peakResident = Math.max(peakResident, sources.size());
        return result;
    }

    /** Transfer every target-retained start before creating read-only full terrain neighbours. */
    void handoffForDecoration(ChunkPos owner, Set<Long> retainedSources) {
        open();
        if (handedOff || !target.equals(owner))
            throw new IllegalStateException("Wrong/already transferred target structure graph");
        for (long key : retainedSources) {
            within(new ChunkPos(key), target, referenceRadius);
            if (sources.remove(key) != null) transferred++;
        }
        handedOff = true;
    }

    private static void within(ChunkPos pos, ChunkPos center, int radius) {
        if (Math.abs((long) pos.x - center.x) > radius
                || Math.abs((long) pos.z - center.z) > radius)
            throw new IllegalArgumentException("Outside finite structure source scope " + pos);
    }

    private void open() {
        if (closed || Thread.currentThread() != thread)
            throw new IllegalStateException("Closed/wrong-thread structure source scope");
    }

    public record Metrics(
            long queries,
            long hits,
            long evaluations,
            int uniqueSources,
            int peakResident,
            int resident,
            int transferred,
            long evaluationNanos,
            long stateMismatches,
            long stateReplays,
            boolean climateState,
            boolean closed) {}

    public Metrics metrics() {
        return new Metrics(
                queries,
                hits,
                evaluations,
                uniqueSources,
                peakResident,
                sources.size(),
                transferred,
                evaluationNanos,
                stateMismatches,
                stateReplays,
                climateState,
                closed);
    }

    @Override
    public void close() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("Wrong-thread structure source close");
        sources.clear();
        evaluated.clear();
        world = null;
        generator = null;
        state = null;
        biomeState = null;
        closed = true;
    }
}
