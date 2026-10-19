package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** One B preparation's native source metadata, including evaluated empty sources. */
public final class ScopedStructureSources implements AutoCloseable {
    private LevelHeightAccessor world;
    private final long seed;
    private final ChunkPos target;
    private final int queryRadius;
    private final Thread thread = Thread.currentThread();

    private record Entry(StructureInputChunk carrier, Object input, Object output) {}

    private final Map<Long, Entry> sources = new HashMap<>();
    private final Set<Long> evaluated = new java.util.HashSet<>();
    private Object settings;
    private Object biomeSource;
    private NativeBiomeSearchState biomeState;
    private final boolean climateState;
    private boolean structuresEnabled;
    private boolean handedOff, closed;
    private long queries, hits, evaluations, evaluationNanos;
    private long stateMismatches, stateReplays;
    private int peakResident, transferred, uniqueSources;

    public ScopedStructureSources(IsolatedGenerationContext context) {
        this(
                context.hostLevel(),
                context.worldSeed(),
                context.target(),
                featureReadRadius(),
                requireNativeState(context));
        requireMode(context);
        settings = context.generator().generatorSettings().value();
        biomeSource = context.generator().getBiomeSource();
        structuresEnabled = context.structureManager().shouldGenerateStructures();
    }

    /** Unproved environments take the same exact native evaluator without source memoization. */
    public static ScopedStructureSources create(IsolatedGenerationContext context) {
        requireMode(context);
        if (!auditedEnvironment()
                || NativeBiomeSearchState.from(context.generator().getBiomeSource()) == null)
            return null;
        return new ScopedStructureSources(context);
    }

    private static boolean auditedEnvironment() {
        return auditedEnvironment(
                FabricLoader.getInstance().getAllMods().stream()
                        .map(mod -> mod.getMetadata().getId())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    static boolean auditedEnvironment(Set<String> mods) {
        var known =
                Set.of(
                        "minecraft",
                        "java",
                        "fabricloader",
                        "mixinextras",
                        "fabric-api-base",
                        "fabric-networking-api-v1",
                        "fabric-registry-sync-v0",
                        ModConstants.MOD_ID,
                        ModConstants.MOD_ID + "_probe");
        return known.containsAll(mods);
    }

    private static NativeBiomeSearchState requireNativeState(IsolatedGenerationContext context) {
        if (!auditedEnvironment())
            throw new IllegalArgumentException("Unaudited structure reuse environment");
        return Objects.requireNonNull(
                NativeBiomeSearchState.from(context.generator().getBiomeSource()),
                "Unaudited native biome search state");
    }

    ScopedStructureSources(LevelHeightAccessor world, long seed, ChunkPos target, int queryRadius) {
        this(world, seed, target, queryRadius, new NativeBiomeSearchState(null));
    }

    ScopedStructureSources(
            LevelHeightAccessor world,
            long seed,
            ChunkPos target,
            int queryRadius,
            NativeBiomeSearchState biomeState) {
        this.world = Objects.requireNonNull(world);
        this.seed = seed;
        this.target = Objects.requireNonNull(target);
        if (queryRadius < 0 || queryRadius > 1)
            throw new IllegalArgumentException("Unaudited B structure query radius");
        this.queryRadius = queryRadius;
        this.biomeState = Objects.requireNonNull(biomeState);
        this.climateState = biomeState.hasTree();
    }

    private static int featureReadRadius() {
        int radius =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.FEATURES)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.TERRAIN);
        if (radius != 1) throw new IllegalStateException("Unaudited B FEATURES read radius");
        return radius;
    }

    private static void requireMode(IsolatedGenerationContext context) {
        if (context.mode() != IsolatedGenerationMode.ISOLATED_MOSAIC)
            throw new IllegalArgumentException(
                    "Structure reuse requires isolated seed-local state");
    }

    void requireCompatible(IsolatedGenerationContext context) {
        requireMode(context);
        open();
        if (world != context.hostLevel()
                || seed != context.worldSeed()
                || settings != context.generator().generatorSettings().value()
                || biomeSource != context.generator().getBiomeSource()
                || structuresEnabled != context.structureManager().shouldGenerateStructures())
            throw new IllegalArgumentException(
                    "Structure source scope belongs to another world/configuration/seed");
        requireWithin(context.target(), target, queryRadius);
    }

    StructureInputChunk source(
            IsolatedGenerationContext context,
            ChunkPos source,
            Supplier<StructureInputChunk> evaluate) {
        requireCompatible(context);
        return source(context.hostLevel(), context.worldSeed(), context.target(), source, evaluate);
    }

    StructureInputChunk source(
            LevelHeightAccessor world,
            long seed,
            ChunkPos query,
            ChunkPos source,
            Supplier<StructureInputChunk> evaluate) {
        open();
        if (this.world != world || this.seed != seed)
            throw new IllegalArgumentException("Wrong structure source world/seed");
        requireWithin(query, target, queryRadius);
        requireWithin(source, query, CompactStructureInputs.referenceRadius());
        queries++;
        Object input = biomeState.capture();
        var existing = sources.get(source.pack());
        if (existing != null && existing.input() == input) {
            hits++;
            biomeState.replay(existing.output());
            if (climateState) stateReplays++;
            return existing.carrier();
        }
        if (existing != null) stateMismatches++;
        long began = System.nanoTime();
        var result = Objects.requireNonNull(evaluate.get());
        if (!source.equals(result.getPos()) || !result.getAllReferences().isEmpty())
            throw new IllegalArgumentException(
                    "Source evaluation must return raw native starts only");
        evaluationNanos += System.nanoTime() - began;
        evaluations++;
        if (evaluated.add(source.pack())) uniqueSources++;
        sources.put(source.pack(), new Entry(result, input, biomeState.capture()));
        peakResident = Math.max(peakResident, sources.size());
        return result;
    }

    /** Native placement mutates pieces. Transfer the target graph, never share it with reads. */
    void handoffForDecoration(ChunkPos owner, Set<Long> retainedSources) {
        open();
        if (handedOff || !target.equals(owner))
            throw new IllegalStateException(
                    "Target structure graph already handed off/wrong owner");
        for (long position : retainedSources) {
            requireWithin(
                    ChunkPos.unpack(position), target, CompactStructureInputs.referenceRadius());
            if (sources.remove(position) != null) transferred++;
        }
        handedOff = true;
    }

    private static void requireWithin(ChunkPos p, ChunkPos center, int radius) {
        if (Math.abs((long) p.x() - center.x()) > radius
                || Math.abs((long) p.z() - center.z()) > radius)
            throw new IllegalArgumentException("Outside bounded structure source scope " + p);
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
        settings = null;
        biomeSource = null;
        biomeState = null;
        closed = true;
    }
}
