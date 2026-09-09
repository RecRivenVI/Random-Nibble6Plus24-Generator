package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.StructurePlacementAccessor;

/**
 * Necessary-candidate index for the existing finite V2 universe. This is not a
 * StructureStart resolver: surviving sets still execute the entire native path.
 * Owned by one seed-local structure state, with no world/global cache or save data.
 */
public final class StructureCandidateScope {
    private final ChunkPos target;
    private final long seed;
    private final int radius;
    private final List<Holder<StructureSet>> originalSets;
    private final List<List<Holder<StructureSet>>> bySource;
    private final Map<Fallback, Integer> fallbackSets;
    private final long cellsPossible;
    private final long cellsEvaluated;
    private final long candidatePositions;
    private final long preparationNanos;
    private final LongAdder queries = new LongAdder();
    private final LongAdder checksBefore = new LongAdder();
    private final LongAdder checksAfter = new LongAdder();
    private final LongAdder queryFallbacks = new LongAdder();

    public enum Fallback { NOT_EXACT_RANDOM_SPREAD, DENSE_GRID, INVALID_GRID, UNSAFE_COORDINATES, SPECIAL_EXCLUSION, UNPROVEN_SCOPE }

    public static StructureCandidateScope forV2Target(ChunkPos target, long seed, List<Holder<StructureSet>> sets) {
        // Share only the already-proven conservative dependency radius, not Stronghold's implementation.
        return new StructureCandidateScope(target, seed, ConcentricRingScope.v2QueryRadius(), sets);
    }

    StructureCandidateScope(ChunkPos target, long seed, int radius, List<Holder<StructureSet>> sets) {
        if (radius < 0) throw new IllegalArgumentException("Negative candidate scope");
        long began = System.nanoTime();
        this.target = target;
        this.seed = seed;
        this.radius = radius;
        this.originalSets = sets;
        if (radius > 64) {
            // Unexpected future dependency metadata must not demand an unbounded index.
            // Decline the optimization instead of changing the old generation path.
            this.bySource = List.of();
            this.fallbackSets = Map.of(Fallback.UNPROVEN_SCOPE, sets.size());
            this.cellsPossible = this.cellsEvaluated = this.candidatePositions = 0;
            this.preparationNanos = System.nanoTime() - began;
            return;
        }
        int width = radius * 2 + 1;
        List<List<Holder<StructureSet>>> index = new ArrayList<>(width * width);
        for (int i = 0; i < width * width; i++) index.add(new ArrayList<>());
        EnumMap<Fallback, Integer> fallback = new EnumMap<>(Fallback.class);
        long possible = 0, evaluated = 0, candidates = 0;
        for (Holder<StructureSet> set : sets) {
            StructurePlacement placement = set.value().placement();
            Fallback reason = fallbackReason(placement, target, radius);
            if (reason != null) {
                fallback.merge(reason, 1, Integer::sum);
                for (var source : index) source.add(set);
                continue;
            }
            var spread = (RandomSpreadStructurePlacement) placement;
            CellRange range = cellRange(target, radius, spread.spacing(), spread.separation());
            possible += range.count();
            for (long z = range.minZ(); z <= range.maxZ(); z++) {
                for (long x = range.minX(); x <= range.maxX(); x++) {
                    evaluated++;
                    // Native RNG, salt and spread type. The representative origin is in this
                    // exact placement cell; no frequency/exclusion/start algorithm is copied.
                    ChunkPos candidate = spread.getPotentialStructureChunk(seed,
                            (int) (x * spread.spacing()), (int) (z * spread.spacing()));
                    if (contains(candidate)) {
                        index.get(index(candidate)).add(set);
                        candidates++;
                    }
                }
            }
        }
        // Adding one set at a time preserves native registry/list order, including duplicate definitions.
        this.bySource = index.stream().map(List::copyOf).toList();
        this.fallbackSets = Map.copyOf(fallback);
        this.cellsPossible = possible;
        this.cellsEvaluated = evaluated;
        this.candidatePositions = candidates;
        this.preparationNanos = System.nanoTime() - began;
    }

    public List<Holder<StructureSet>> forSource(ChunkPos source, List<Holder<StructureSet>> nativeSets) {
        queries.increment();
        checksBefore.add(nativeSets.size());
        List<Holder<StructureSet>> result;
        if (bySource.isEmpty() || nativeSets != originalSets || !contains(source)) {
            // A changed definition view or an unproven query domain keeps the full old path.
            queryFallbacks.increment();
            result = nativeSets;
        } else {
            result = bySource.get(index(source));
        }
        checksAfter.add(result.size());
        return result;
    }

    private boolean contains(ChunkPos source) {
        return Math.abs((long) source.x() - target.x()) <= radius
                && Math.abs((long) source.z() - target.z()) <= radius;
    }

    private int index(ChunkPos source) {
        return (int) ((source.z() - (long) target.z() + radius) * (radius * 2 + 1)
                + source.x() - (long) target.x() + radius);
    }

    private static Fallback fallbackReason(StructurePlacement placement, ChunkPos target, int radius) {
        if (placement.getClass() != RandomSpreadStructurePlacement.class) return Fallback.NOT_EXACT_RANDOM_SPREAD;
        var spread = (RandomSpreadStructurePlacement) placement;
        if (spread.spacing() <= 0 || spread.spacing() > 4096 || spread.separation() < 0
                || spread.separation() >= spread.spacing() || spread.spreadType() == null) return Fallback.INVALID_GRID;
        // Every coordinate is a candidate in spacing=1 grids (e.g. mineshafts).
        // Do not duplicate their RNG work or change their later validity/piece ordering.
        if (spread.spacing() == 1) return Fallback.DENSE_GRID;
        if (cellRange(target, radius, spread.spacing(), spread.separation()) == null) return Fallback.UNSAFE_COORDINATES;
        Set<StructurePlacement> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        StructurePlacement current = placement;
        while (visited.add(current)) {
            if (current.getClass() != RandomSpreadStructurePlacement.class) return Fallback.SPECIAL_EXCLUSION;
            var exclusion = ((StructurePlacementAccessor) current).randomnibble6plus24generator$exclusionZone();
            if (exclusion.isEmpty()) return null;
            if (exclusion.get().chunkCount() < 1 || exclusion.get().chunkCount() > 16) return Fallback.SPECIAL_EXCLUSION;
            current = exclusion.get().otherSet().value().placement();
        }
        return Fallback.SPECIAL_EXCLUSION;
    }

    /** Null means integer overflow could break the native same-cell proof: keep eager. */
    static CellRange cellRange(ChunkPos target, int radius, int spacing, int separation) {
        if (radius < 0 || spacing <= 0 || separation < 0 || separation >= spacing) return null;
        long minX = (long) target.x() - radius, maxX = (long) target.x() + radius;
        long minZ = (long) target.z() - radius, maxZ = (long) target.z() + radius;
        long x0 = Math.floorDiv(minX, spacing), x1 = Math.floorDiv(maxX, spacing);
        long z0 = Math.floorDiv(minZ, spacing), z1 = Math.floorDiv(maxZ, spacing);
        long offset = spacing - (long) separation - 1;
        if (x0 * spacing < Integer.MIN_VALUE || z0 * spacing < Integer.MIN_VALUE
                || x1 * spacing + offset > Integer.MAX_VALUE || z1 * spacing + offset > Integer.MAX_VALUE
                || minX < Integer.MIN_VALUE || minZ < Integer.MIN_VALUE
                || maxX > Integer.MAX_VALUE || maxZ > Integer.MAX_VALUE) return null;
        return new CellRange(x0, x1, z0, z1);
    }

    record CellRange(long minX, long maxX, long minZ, long maxZ) {
        long count() { return (maxX - minX + 1) * (maxZ - minZ + 1); }
    }

    public Metrics metrics() {
        return new Metrics(target, seed, radius, originalSets.size(), fallbackSets,
                cellsPossible, cellsEvaluated, candidatePositions, preparationNanos,
                queries.sum(), checksBefore.sum(), checksAfter.sum(), queryFallbacks.sum());
    }

    public record Metrics(ChunkPos target, long localSeed, int radius, int definitionCount,
            Map<Fallback, Integer> fallbackSets, long cellsPossible, long cellsEvaluated,
            long candidatePositions, long preparationNanos, long sourceQueries,
            long setChecksBefore, long setChecksAfter, long queryFallbacks) {}
}
