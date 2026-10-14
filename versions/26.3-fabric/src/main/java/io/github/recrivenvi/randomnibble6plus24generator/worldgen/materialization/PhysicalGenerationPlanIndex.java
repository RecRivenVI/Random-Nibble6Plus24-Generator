package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Physical dependency accounting. All access is owned by the materializer's plan lock. */
final class PhysicalGenerationPlanIndex<K> {
    private static final List<ChunkStatus> STATUSES = List.copyOf(ChunkStatus.getStatusList());

    private final Map<K, Map<K, ChunkStatus>> plans = new HashMap<>();
    private final Map<K, Requirement<K>> dependencies = new HashMap<>();

    Set<K> replace(K owner, Map<K, ChunkStatus> requirements) {
        Map<K, ChunkStatus> next = Map.copyOf(requirements);
        Map<K, ChunkStatus> previous = plans.put(owner, next);
        Set<K> affected = new LinkedHashSet<>();
        if (previous != null) {
            previous.forEach(
                    (key, status) -> {
                        affected.add(key);
                        if (next.get(key) != status) removeRequirement(owner, key, status);
                    });
        }
        next.forEach(
                (key, status) -> {
                    affected.add(key);
                    if (previous == null || previous.get(key) != status) {
                        Requirement<K> requirement =
                                dependencies.computeIfAbsent(key, ignored -> new Requirement<>());
                        requirement.owners.add(owner);
                        requirement.counts[STATUSES.indexOf(status)]++;
                    }
                });
        return affected;
    }

    Set<K> remove(K owner) {
        Map<K, ChunkStatus> removed = plans.remove(owner);
        if (removed == null) return Set.of();
        removed.forEach((key, status) -> removeRequirement(owner, key, status));
        return removed.keySet();
    }

    ChunkStatus maximum(K key) {
        Requirement<K> requirement = dependencies.get(key);
        if (requirement == null) return null;
        for (int i = requirement.counts.length - 1; i >= 0; i--) {
            if (requirement.counts[i] > 0) return STATUSES.get(i);
        }
        throw new IllegalStateException("Physical dependency has owners but no required status");
    }

    List<K> ownersDependingOn(K key) {
        Requirement<K> requirement = dependencies.get(key);
        return requirement == null ? List.of() : List.copyOf(requirement.owners);
    }

    void removeOwnersIf(Predicate<K> predicate) {
        // Full traversal is restricted to world/dimension lifecycle cleanup, never completion.
        List<K> owners = plans.keySet().stream().filter(predicate).toList();
        owners.forEach(this::remove);
    }

    void clear() {
        plans.clear();
        dependencies.clear();
    }

    int planCount() {
        return plans.size();
    }

    int dependencyCount() {
        return dependencies.size();
    }

    private void removeRequirement(K owner, K key, ChunkStatus status) {
        Requirement<K> requirement = dependencies.get(key);
        int index = STATUSES.indexOf(status);
        if (requirement == null
                || !requirement.owners.remove(owner)
                || requirement.counts[index] <= 0) {
            throw new IllegalStateException("Unbalanced physical dependency removal");
        }
        requirement.counts[index]--;
        if (requirement.owners.isEmpty()) dependencies.remove(key);
    }

    private static final class Requirement<K> {
        final Set<K> owners = new HashSet<>();
        final int[] counts = new int[STATUSES.size()];
    }
}
