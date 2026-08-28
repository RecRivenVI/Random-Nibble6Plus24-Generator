package io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

class MosaicSeedResolverDeterminismTest {

    private record Query(long masterSeed, ResourceKey<Level> dimension, ChunkPos chunkPos) {
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void repeatedCallsAndDifferentResolverInstancesAgree() {
        Query query = new Query(0x13579BDF2468ACE0L, Level.OVERWORLD, new ChunkPos(25_000, -40_000));
        MosaicSeedResolver first = new MosaicSeedResolver(MosaicWorldProfile.current());
        MosaicSeedResolver second = new MosaicSeedResolver(MosaicWorldProfile.current());
        long expected = first.resolveLocalWorldSeed(query.masterSeed(), query.dimension(), query.chunkPos());

        for (int i = 0; i < 10_000; i++) {
            assertEquals(expected, first.resolveLocalWorldSeed(query.masterSeed(), query.dimension(), query.chunkPos()));
            assertEquals(expected, second.resolveLocalWorldSeed(query.masterSeed(), query.dimension(), query.chunkPos()));
        }
    }

    @Test
    void forwardReverseAndShuffledQueryOrdersAgree() {
        List<Query> queries = queries(512);
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        long[] expected = resolveAll(resolver, queries);

        List<Query> reversed = new ArrayList<>(queries);
        Collections.reverse(reversed);
        resolveAll(resolver, reversed);
        assertArrayEquals(expected, resolveAll(resolver, queries));

        List<Query> shuffled = new ArrayList<>(queries);
        Collections.shuffle(shuffled, new Random(0x51554552595F5631L));
        resolveAll(resolver, shuffled);
        assertArrayEquals(expected, resolveAll(resolver, queries));
    }

    @Test
    @Timeout(30)
    void concurrentQueriesAgreeAcrossWorkerCounts() throws Exception {
        List<Query> queries = queries(1_024);
        MosaicSeedResolver resolver = new MosaicSeedResolver(MosaicWorldProfile.current());
        long[] expected = resolveAll(resolver, queries);

        for (int workers : new int[] {1, 2, 4, 8}) {
            ExecutorService executor = Executors.newFixedThreadPool(workers);
            try {
                List<Callable<Long>> tasks = queries.stream()
                        .<Callable<Long>>map(query -> () -> resolver.resolveLocalWorldSeed(
                                query.masterSeed(),
                                query.dimension(),
                                query.chunkPos()))
                        .toList();
                List<Future<Long>> results = executor.invokeAll(tasks);
                for (int i = 0; i < results.size(); i++) {
                    assertEquals(expected[i], results.get(i).get(), "workers=" + workers + ", query=" + queries.get(i));
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void resolverDoesNotStoreMutableRandomState() {
        assertFalse(java.util.Arrays.stream(MosaicSeedResolver.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .anyMatch(field -> RandomSource.class.isAssignableFrom(field.getType())));
    }

    private static long[] resolveAll(MosaicSeedResolver resolver, List<Query> queries) {
        long[] results = new long[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            Query query = queries.get(i);
            results[i] = resolver.resolveLocalWorldSeed(query.masterSeed(), query.dimension(), query.chunkPos());
        }
        return results;
    }

    private static List<Query> queries(int count) {
        Random inputs = new Random(0x44455445524D5F31L);
        List<Query> queries = new ArrayList<>(count);
        List<ResourceKey<Level>> dimensions = List.of(Level.OVERWORLD, Level.NETHER, Level.END);
        for (int i = 0; i < count; i++) {
            queries.add(new Query(
                    inputs.nextLong(),
                    dimensions.get(i % dimensions.size()),
                    new ChunkPos(inputs.nextInt(), inputs.nextInt())));
        }
        return List.copyOf(queries);
    }
}
