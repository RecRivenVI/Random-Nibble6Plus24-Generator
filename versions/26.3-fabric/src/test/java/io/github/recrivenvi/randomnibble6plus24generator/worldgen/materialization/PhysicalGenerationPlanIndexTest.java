package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PhysicalGenerationPlanIndexTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void sharedDependencyRetainsHighestStatusUntilItsLastOwnerCompletes() {
        var index = new PhysicalGenerationPlanIndex<String>();
        index.replace("a", Map.of("shared", ChunkStatus.FULL, "a", ChunkStatus.FEATURES));
        index.replace("b", Map.of("shared", ChunkStatus.LIGHT));
        index.replace("c", Map.of("shared", ChunkStatus.LIGHT));
        assertEquals(ChunkStatus.FULL, index.maximum("shared"));
        assertEquals(Set.of("a", "shared"), index.remove("a"));
        assertEquals(ChunkStatus.LIGHT, index.maximum("shared"));
        index.remove("b");
        assertEquals(ChunkStatus.LIGHT, index.maximum("shared"));
        index.remove("c");
        assertNull(index.maximum("shared"));
        assertEquals(0, index.planCount());
        assertEquals(0, index.dependencyCount());
    }

    @Test
    void replacementIsIdempotentAndRemovesStaleEdgesAndStatusCounts() {
        var index = new PhysicalGenerationPlanIndex<String>();
        var first = Map.of("shared", ChunkStatus.LIGHT, "removed", ChunkStatus.BIOMES);
        index.replace("owner", first);
        index.replace("owner", first);
        index.replace("other", Map.of("shared", ChunkStatus.FEATURES));
        assertEquals(
                Set.of("shared", "removed", "new"),
                index.replace(
                        "owner", Map.of("shared", ChunkStatus.SPAWN, "new", ChunkStatus.FULL)));
        assertNull(index.maximum("removed"));
        assertEquals(ChunkStatus.SPAWN, index.maximum("shared"));
        index.remove("owner");
        assertEquals(ChunkStatus.FEATURES, index.maximum("shared"));
        assertNull(index.maximum("new"));
        index.remove("other");
        assertEquals(Set.of(), index.remove("other"));
        assertEquals(0, index.dependencyCount());
    }

    @Test
    void failureCancelsExactlyTheDependentOwnersAndAllowsLaterRequests() {
        var index = new PhysicalGenerationPlanIndex<String>();
        index.replace("a", Map.of("failed", ChunkStatus.FULL, "a-only", ChunkStatus.LIGHT));
        index.replace("b", Map.of("failed", ChunkStatus.SPAWN));
        index.replace("unrelated", Map.of("safe", ChunkStatus.LIGHT));
        var cancelled = index.ownersDependingOn("failed");
        assertEquals(Set.of("a", "b"), Set.copyOf(cancelled));
        cancelled.forEach(index::remove);
        assertNull(index.maximum("failed"));
        assertNull(index.maximum("a-only"));
        assertEquals(ChunkStatus.LIGHT, index.maximum("safe"));
        index.replace("a", Map.of("failed", ChunkStatus.FULL));
        assertEquals(ChunkStatus.FULL, index.maximum("failed"));
        index.clear();
        assertEquals(0, index.planCount());
        assertEquals(0, index.dependencyCount());
    }

    @Test
    void worldAndDimensionCloseCannotRetainOrContaminateAnotherIdentity() {
        record Key(int world, int dimension, int pos) {}
        var index = new PhysicalGenerationPlanIndex<Key>();
        var a = new Key(1, 0, 0);
        var b = new Key(1, 1, 0);
        var c = new Key(2, 0, 0);
        index.replace(a, Map.of(a, ChunkStatus.FULL));
        index.replace(b, Map.of(b, ChunkStatus.LIGHT));
        index.replace(c, Map.of(c, ChunkStatus.SPAWN));
        index.removeOwnersIf(key -> key.world() == 1 && key.dimension() == 0);
        assertNull(index.maximum(a));
        assertEquals(ChunkStatus.LIGHT, index.maximum(b));
        assertEquals(ChunkStatus.SPAWN, index.maximum(c));
        index.removeOwnersIf(key -> key.world() == 1);
        assertNull(index.maximum(b));
        assertEquals(1, index.planCount());
        index.clear();
        index.replace(a, Map.of(a, ChunkStatus.FEATURES));
        assertEquals(ChunkStatus.FEATURES, index.maximum(a));
        assertNull(index.maximum(c));
    }

    @Test
    void incrementalAccountingMatchesFullScanOracleAfterEveryOperation() {
        var index = new PhysicalGenerationPlanIndex<Integer>();
        Map<Integer, Map<Integer, ChunkStatus>> oracle = new HashMap<>();
        var random = new Random(0x3c2L);
        var statuses = ChunkStatus.getStatusList();
        for (int i = 0; i < 2000; i++) {
            int owner = random.nextInt(32);
            if (random.nextInt(4) == 0) {
                oracle.remove(owner);
                index.remove(owner);
            } else {
                Map<Integer, ChunkStatus> requirements = new HashMap<>();
                for (int j = 0; j < 8; j++) {
                    requirements.put(
                            random.nextInt(48), statuses.get(random.nextInt(statuses.size())));
                }
                oracle.put(owner, Map.copyOf(requirements));
                index.replace(owner, requirements);
            }
            assertOracle(index, oracle);
        }
    }

    @Test
    void concurrentCompletionAndReplacementRespectTheExistingExternalLock() throws Exception {
        var index = new PhysicalGenerationPlanIndex<Integer>();
        Map<Integer, Map<Integer, ChunkStatus>> oracle = new HashMap<>();
        Object lock = new Object();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> operations =
                    IntStream.range(0, 8)
                            .mapToObj(
                                    owner ->
                                            (Callable<Void>)
                                                    () -> {
                                                        for (int i = 0; i < 40; i++) {
                                                            synchronized (lock) {
                                                                var required =
                                                                        Map.of(
                                                                                owner,
                                                                                ChunkStatus.FULL,
                                                                                40,
                                                                                ChunkStatus.LIGHT);
                                                                oracle.put(owner, required);
                                                                index.replace(owner, required);
                                                                assertOracle(index, oracle);
                                                            }
                                                            synchronized (lock) {
                                                                oracle.remove(owner);
                                                                index.remove(owner);
                                                                assertOracle(index, oracle);
                                                            }
                                                        }
                                                        return null;
                                                    })
                            .toList();
            for (var result : executor.invokeAll(operations)) result.get();
        }
        assertEquals(0, index.planCount());
        assertEquals(0, index.dependencyCount());
    }

    @Test
    void completingOnePlanDoesNotSearchThousandsOfUnrelatedPlans() {
        AtomicInteger hashes = new AtomicInteger();
        record Key(int value, AtomicInteger calls) {
            @Override
            public int hashCode() {
                calls.incrementAndGet();
                return value;
            }
        }
        var index = new PhysicalGenerationPlanIndex<Key>();
        for (int i = 0; i < 4096; i++) {
            var owner = new Key(i, hashes);
            index.replace(owner, Map.of(new Key(i + 10000, hashes), ChunkStatus.FULL));
        }
        var dependency = new Key(10007, hashes);
        hashes.set(0);
        index.remove(new Key(7, hashes));
        assertNull(index.maximum(dependency));
        assertTrue(hashes.get() < 20, "Completion must not scan unrelated plans");
        assertEquals(4095, index.planCount());
        assertEquals(4095, index.dependencyCount());
    }

    private static void assertOracle(
            PhysicalGenerationPlanIndex<Integer> index,
            Map<Integer, Map<Integer, ChunkStatus>> oracle) {
        assertEquals(oracle.size(), index.planCount());
        assertEquals(
                oracle.values().stream().flatMap(map -> map.keySet().stream()).distinct().count(),
                (long) index.dependencyCount());
        for (int key = 0; key < 48; key++) {
            ChunkStatus maximum = null;
            for (var plan : oracle.values()) {
                ChunkStatus candidate = plan.get(key);
                if (candidate != null && (maximum == null || candidate.isAfter(maximum)))
                    maximum = candidate;
            }
            assertEquals(maximum, index.maximum(key));
            final int dependency = key;
            assertEquals(
                    oracle.entrySet().stream()
                            .filter(entry -> entry.getValue().containsKey(dependency))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet()),
                    Set.copyOf(index.ownersDependingOn(key)));
        }
    }
}
