package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.CheckerboardColumnBiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePlacementAccessor;

/**
 * Exact finite view of Vanilla concentric-ring positions. No placement/RNG algorithm
 * lives here: Vanilla still calculates every slot and consumes every random.fork().
 * Only its independent 112-block biome-search suppliers can be skipped.
 */
public final class ConcentricRingScope {
    private static final int MIN_V2_QUERY_RADIUS = 11;
    // Native quart search from chunkMin+8 visits [chunkMin-104, chunkMin+120].
    // Floor-to-chunk therefore moves each coordinate by at most seven chunks.
    static final int BIOME_SEARCH_CHUNK_DISPLACEMENT = 7;
    private final ChunkPos target;
    private final long localSeed;
    private final int queryRadius;
    private final Fallback fallback;
    private final LongAdder slots = new LongAdder();
    private final LongAdder searches = new LongAdder();
    private final LongAdder skipped = new LongAdder();
    private final LongAdder fullRingFallbacks = new LongAdder();
    private final LongAdder ringNanos = new LongAdder();

    public enum Fallback { NONE, UNPROVEN_BIOME_SOURCE, UNPROVEN_PLACEMENT, CONCENTRIC_EXCLUSION }

    ConcentricRingScope(ChunkPos target, long localSeed, int queryRadius, Fallback fallback) {
        if (queryRadius < 0) throw new IllegalArgumentException("Negative ring query radius");
        this.target = target;
        this.localSeed = localSeed;
        this.queryRadius = queryRadius;
        this.fallback = fallback;
    }

    public static ConcentricRingScope forV2Target(
            ChunkPos target, long localSeed, BiomeSource biomes, List<Holder<StructureSet>> sets) {
        Fallback fallback = supportsBiomeSource(biomes.getClass())
                ? placementFallback(sets) : Fallback.UNPROVEN_BIOME_SOURCE;
        return new ConcentricRingScope(target, localSeed, v2QueryRadius(), fallback);
    }

    public static int v2QueryRadius() {
        var features = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        int derived = features.blockStateWriteRadius()
                + features.accumulatedDependencies().getRadiusOf(ChunkStatus.STRUCTURE_STARTS);
        // This repair does not reduce the proven V2 frontier even if metadata shrinks.
        return Math.max(MIN_V2_QUERY_RADIUS, derived);
    }

    static boolean supportsBiomeSource(Class<?> type) {
        // Exact classes, not instanceof: an unknown subclass can override the search radius semantics.
        return type == MultiNoiseBiomeSource.class || type == FixedBiomeSource.class
                || type == TheEndBiomeSource.class || type == CheckerboardColumnBiomeSource.class;
    }

    private static Fallback placementFallback(List<Holder<StructureSet>> roots) {
        ArrayDeque<StructurePlacement> pending = new ArrayDeque<>();
        roots.forEach(set -> pending.add(set.value().placement()));
        Set<StructurePlacement> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!pending.isEmpty()) {
            StructurePlacement placement = pending.removeFirst();
            if (!visited.add(placement)) continue;
            if (placement.getClass() != RandomSpreadStructurePlacement.class
                    && placement.getClass() != ConcentricRingsStructurePlacement.class) {
                return Fallback.UNPROVEN_PLACEMENT;
            }
            var exclusion = ((StructurePlacementAccessor) placement)
                    .randomnibble6plus24generator$exclusionZone();
            if (exclusion.isPresent()) {
                StructurePlacement other = exclusion.get().otherSet().value().placement();
                if (other instanceof ConcentricRingsStructurePlacement) {
                    // Exclusion searches can extend beyond the target frontier. Keep full Vanilla results.
                    return Fallback.CONCENTRIC_EXCLUSION;
                }
                pending.add(other);
            }
        }
        return Fallback.NONE;
    }

    public boolean contains(int x, int z) {
        return Math.abs((long) x - target.x()) <= queryRadius
                && Math.abs((long) z - target.z()) <= queryRadius;
    }

    public boolean mayReachQueryRange(int initialX, int initialZ) {
        return Math.abs((long) initialX - target.x()) <= queryRadius + (long) BIOME_SEARCH_CHUNK_DISPLACEMENT
                && Math.abs((long) initialZ - target.z()) <= queryRadius + (long) BIOME_SEARCH_CHUNK_DISPLACEMENT;
    }

    public void requireQueryInRange(int x, int z) {
        if (fallback == Fallback.NONE && !contains(x, z)) {
            throw new IllegalStateException("Concentric-ring query escaped isolated V2 scope: target="
                    + target + ", localSeed=" + localSeed + ", radius=" + queryRadius
                    + ", query=" + new ChunkPos(x, z));
        }
    }

    public CompletableFuture<ChunkPos> search(
            int initialX, int initialZ, Supplier<ChunkPos> vanillaSearch, Executor executor) {
        slots.increment();
        if (fallback == Fallback.NONE && !mayReachQueryRange(initialX, initialZ)) {
            skipped.increment();
            // This value is provably outside the query domain and is removed by finish().
            // Keeping the slot here preserves the native sequence/fork order without scheduling its work.
            return CompletableFuture.completedFuture(new ChunkPos(initialX, initialZ));
        }
        searches.increment();
        return CompletableFuture.supplyAsync(vanillaSearch, executor);
    }

    public void beginRing() {
        if (fallback != Fallback.NONE) fullRingFallbacks.increment();
    }

    public List<ChunkPos> finish(List<ChunkPos> nativeOrder, long elapsedNanos) {
        ringNanos.add(elapsedNanos);
        return fallback == Fallback.NONE
                ? nativeOrder.stream().filter(pos -> contains(pos.x(), pos.z())).toList()
                : nativeOrder;
    }

    /** Constant-size, state-owned diagnostics; no global cache, world reference, log or profiler dependency. */
    public Metrics metrics() {
        return new Metrics(queryRadius, fallback, slots.sum(), searches.sum(), skipped.sum(),
                fullRingFallbacks.sum(), ringNanos.sum());
    }

    public record Metrics(int queryRadius, Fallback fallback, long slotsConsidered, long biomeSearches,
            long searchesSkipped, long fullRingFallbacks, long ringWallNanos) {}
}
