package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePlacementAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.*;

/** Native slots/forks still run; only provably irrelevant 112-block searches are skipped. */
public final class ConcentricRingScope {
    private static final ThreadLocal<ConcentricRingScope> SEARCH = new ThreadLocal<>();
    private static final int SEARCH_DISPLACEMENT = 7;
    private final ChunkPos target;
    private final long seed;
    private final BiomeSource source;
    private final int radius;
    private final Fallback fallback;
    private final ConcentricBiomeTail tail;
    private final LongAdder scheduled = new LongAdder(),
            lookups = new LongAdder(),
            searches = new LongAdder();
    private final LongAdder tailReplays = new LongAdder(), tailFallbacks = new LongAdder();
    private final AtomicInteger active = new AtomicInteger();
    private volatile long began, wallNanos;
    private int expectedSlots;

    public enum Fallback {
        NONE,
        UNPROVEN_ENVIRONMENT,
        UNPROVEN_BIOME_SOURCE,
        UNPROVEN_PLACEMENT,
        CONCENTRIC_EXCLUSION,
        MULTIPLE_RING_SETS,
        UNPROVEN_RING_GEOMETRY
    }

    private ConcentricRingScope(
            ChunkPos target, long seed, BiomeSource source, int radius, Fallback fallback) {
        this.target = target;
        this.seed = seed;
        this.source = source;
        this.radius = radius;
        this.fallback = fallback;
        tail =
                fallback == Fallback.NONE && source.getClass() == MultiNoiseBiomeSource.class
                        ? new ConcentricBiomeTail((MultiNoiseBiomeSource) source)
                        : null;
    }

    public static ConcentricRingScope forV2Target(
            ChunkPos target, long seed, BiomeSource source, List<Holder<StructureSet>> sets) {
        Set<String> known =
                Set.of(
                        "minecraft",
                        "forge",
                        "mclanguage",
                        "lowcodefml",
                        "javafml",
                        "mixinextras",
                        ModConstants.MOD_ID,
                        ModConstants.MOD_ID + "_probe");
        Fallback fallback =
                !net.minecraftforge.fml.ModList.get().getMods().stream()
                                .allMatch(mod -> known.contains(mod.getModId()))
                        ? Fallback.UNPROVEN_ENVIRONMENT
                        : !supportsSource(source.getClass())
                                ? Fallback.UNPROVEN_BIOME_SOURCE
                                : placementFallback(sets);
        // An unsupported environment must not evaluate or assume our native dependency closure.
        return new ConcentricRingScope(
                target, seed, source, fallback == Fallback.NONE ? v2QueryRadius() : 11, fallback);
    }

    private static final class NativeRadius {
        private static final int VALUE = derive();

        private static int derive() {
            int starts = 0;
            for (var entry :
                    MosaicPhysicalGenerationPlan.derive(ChunkPos.ZERO, ChunkStatus.FEATURES)
                            .requirements()
                            .entrySet())
                if (entry.getValue().isOrAfter(ChunkStatus.STRUCTURE_STARTS))
                    starts =
                            Math.max(
                                    starts,
                                    Math.max(
                                            Math.abs(entry.getKey().x),
                                            Math.abs(entry.getKey().z)));
            // Native FEATURES constructs WorldGenRegion with a write cutoff of one. Keep the
            // conservative V2 nine-writer influence bound even for the single-writer B contract.
            return Math.max(11, Math.addExact(1, starts));
        }
    }

    public static int v2QueryRadius() {
        return NativeRadius.VALUE;
    }

    private static boolean supportsSource(Class<?> type) {
        return type == MultiNoiseBiomeSource.class
                || type == FixedBiomeSource.class
                || type == CheckerboardColumnBiomeSource.class
                || type == TheEndBiomeSource.class;
    }

    private static Fallback placementFallback(List<Holder<StructureSet>> sets) {
        var pending = new ArrayDeque<StructurePlacement>();
        Set<StructurePlacement> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int rings = 0;
        for (var set : sets) {
            var placement = set.value().placement();
            if (placement.getClass() != RandomSpreadStructurePlacement.class
                    && placement.getClass() != ConcentricRingsStructurePlacement.class)
                return Fallback.UNPROVEN_PLACEMENT;
            pending.add(placement);
            if (placement instanceof ConcentricRingsStructurePlacement ring) {
                rings++;
                // Reject geometries for which native int multiplication or block-coordinate
                // conversion can wrap before the 112-block search. No approximate geometry.
                if (ring.distance() < 0
                        || ring.count() < 0
                        || ring.spread() < 0
                        || ring.distance()
                                > (((Integer.MAX_VALUE - 120L) / 16L) - 1L)
                                        / (6L * ring.count() + 7L))
                    return Fallback.UNPROVEN_RING_GEOMETRY;
            }
        }
        if (rings > 1) return Fallback.MULTIPLE_RING_SETS;
        while (!pending.isEmpty()) {
            var placement = pending.removeFirst();
            if (!visited.add(placement)) continue;
            if (placement.getClass() != RandomSpreadStructurePlacement.class
                    && placement.getClass() != ConcentricRingsStructurePlacement.class)
                return Fallback.UNPROVEN_PLACEMENT;
            var exclusion = ((StructurePlacementAccessor) placement).mosaic$exclusionZone();
            if (exclusion.isPresent()) {
                var other = exclusion.get().otherSet().value().placement();
                if (other instanceof ConcentricRingsStructurePlacement)
                    return Fallback.CONCENTRIC_EXCLUSION;
                pending.add(other);
            }
        }
        return Fallback.NONE;
    }

    public boolean contains(int x, int z) {
        return Math.abs((long) x - target.x) <= radius && Math.abs((long) z - target.z) <= radius;
    }

    public boolean mayReach(int x, int z) {
        return Math.abs((long) x - target.x) <= radius + (long) SEARCH_DISPLACEMENT
                && Math.abs((long) z - target.z) <= radius + (long) SEARCH_DISPLACEMENT;
    }

    public void requireQuery(int x, int z) {
        if (fallback == Fallback.NONE && !contains(x, z))
            throw new IllegalStateException(
                    "Ring query escaped seed-local scope: target="
                            + target
                            + ", seed="
                            + seed
                            + ", radius="
                            + radius
                            + ", query="
                            + new ChunkPos(x, z));
    }

    public static ConcentricRingScope currentSearch() {
        return SEARCH.get();
    }

    public void beginRing(int count) {
        if (began != 0 || fallback != Fallback.NONE)
            throw new IllegalStateException("Unproven or reused ring scope");
        expectedSlots = count;
        began = System.nanoTime();
    }

    public Supplier<ChunkPos> wrapSearch(Supplier<ChunkPos> nativeSearch) {
        if (began == 0) throw new IllegalStateException("Ring supplier before native ring entry");
        scheduled.increment();
        return () -> {
            var previous = SEARCH.get();
            active.incrementAndGet();
            SEARCH.set(this);
            try {
                return nativeSearch.get();
            } finally {
                if (previous == null) SEARCH.remove();
                else SEARCH.set(previous);
                active.decrementAndGet();
            }
        };
    }

    public boolean skipLookup(
            BiomeSource caller, int x, int y, int z, int searchRadius, Climate.Sampler sampler) {
        if (SEARCH.get() != this
                || caller != source
                || y != 0
                || searchRadius != 112
                || (x & 15) != 8
                || (z & 15) != 8)
            throw new IllegalStateException("Unaudited native concentric biome search");
        lookups.increment();
        if (!mayReach(x >> 4, z >> 4)) {
            if (tail == null) return true;
            // The audited native step=1/findClosest=false loop always visits this point last.
            // Do not use NoiseChunk.cachedClimateSampler or change stored-biome authority.
            var point = sampler.sample((x >> 2) + 28, y >> 2, (z >> 2) + 28);
            if (tail.replay(point)) {
                tailReplays.increment();
                return true;
            }
            tailFallbacks.increment();
        }
        searches.increment();
        return false;
    }

    public List<ChunkPos> finish(List<ChunkPos> positions) {
        if (active.get() != 0 || scheduled.sum() != expectedSlots || lookups.sum() != expectedSlots)
            throw new IllegalStateException("Incomplete native ring suppliers or leaked scope");
        wallNanos = System.nanoTime() - began;
        return positions.stream().filter(pos -> contains(pos.x, pos.z)).toList();
    }

    public Metrics metrics() {
        return new Metrics(
                radius,
                fallback,
                scheduled.sum(),
                lookups.sum(),
                searches.sum(),
                lookups.sum() - searches.sum(),
                active.get(),
                wallNanos,
                tailReplays.sum(),
                tailFallbacks.sum());
    }

    public record Metrics(
            int queryRadius,
            Fallback fallback,
            long suppliers,
            long slotsConsidered,
            long biomeSearches,
            long searchesSkipped,
            int activeSuppliers,
            long ringWallNanos,
            long tailReplays,
            long tailFallbacks) {}
}
