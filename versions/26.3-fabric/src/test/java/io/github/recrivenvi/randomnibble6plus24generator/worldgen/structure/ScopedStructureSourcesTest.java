package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScopedStructureSourcesTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void nativeEntryIsPartOfTheCacheKeyAndHitReplaysExactExit() {
        var world = LevelHeightAccessor.create(-64, 384);
        var cursor = new ThreadLocal<Object>();
        Object entry = new Object(), exit = new Object(), foreign = new Object();
        cursor.set(entry);
        try (var scope =
                new ScopedStructureSources(
                        world, 17, ChunkPos.ZERO, 1, new NativeBiomeSearchState(cursor))) {
            var first =
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> {
                                cursor.set(exit);
                                return new StructureInputChunk(ChunkPos.ZERO, world);
                            });
            cursor.set(entry);
            assertSame(
                    first,
                    scope.source(
                            world,
                            17,
                            new ChunkPos(1, 0),
                            ChunkPos.ZERO,
                            () -> {
                                throw new AssertionError("Compatible input reevaluated");
                            }));
            assertSame(exit, cursor.get());
            assertEquals(1, scope.metrics().stateReplays());
            cursor.set(foreign);
            var changed =
                    scope.source(
                            world,
                            17,
                            new ChunkPos(0, 1),
                            ChunkPos.ZERO,
                            () -> {
                                cursor.set(foreign);
                                return new StructureInputChunk(ChunkPos.ZERO, world);
                            });
            assertNotSame(first, changed);
            assertEquals(1, scope.metrics().stateMismatches());
            assertEquals(2, scope.metrics().evaluations());
            assertTrue(scope.metrics().climateState());
        } finally {
            cursor.remove();
        }
    }

    @Test
    void climateFailureIsNotCachedAndThreadLocalContinuationIsOwnedByTheCallerThread() {
        var world = LevelHeightAccessor.create(-64, 384);
        var cursor = new ThreadLocal<Object>();
        Object entered = new Object(), partial = new Object();
        cursor.set(entered);
        try (var scope =
                new ScopedStructureSources(
                        world, 17, ChunkPos.ZERO, 1, new NativeBiomeSearchState(cursor))) {
            var error = new IllegalArgumentException("Native structure failed");
            assertSame(
                    error,
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    scope.source(
                                            world,
                                            17,
                                            ChunkPos.ZERO,
                                            ChunkPos.ZERO,
                                            () -> {
                                                cursor.set(partial);
                                                throw error;
                                            })));
            assertSame(partial, cursor.get());
            assertEquals(0, scope.metrics().resident());
            CompletableFuture.runAsync(
                            () -> {
                                assertNull(cursor.get());
                                assertThrows(
                                        IllegalStateException.class,
                                        () ->
                                                scope.source(
                                                        world,
                                                        17,
                                                        ChunkPos.ZERO,
                                                        ChunkPos.ZERO,
                                                        () -> null));
                            })
                    .join();
            assertSame(partial, cursor.get());
        } finally {
            cursor.remove();
        }
    }

    @Test
    void unprovedModsCannotEnableSourceReuse() {
        assertTrue(
                ScopedStructureSources.auditedEnvironment(
                        Set.of("minecraft", "fabricloader", "fabric-registry-sync-v0")));
        assertFalse(ScopedStructureSources.auditedEnvironment(Set.of("minecraft", "terrablender")));
        assertFalse(
                ScopedStructureSources.auditedEnvironment(
                        Set.of("minecraft", "unproved_worldgen")));
    }

    @Test
    void emptyNativeResultsAreReusedAcrossOverlappingQueries() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 1)) {
            var calls = new AtomicInteger();
            var pos = new ChunkPos(7, -7);
            var first =
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            pos,
                            () -> {
                                calls.incrementAndGet();
                                return new StructureInputChunk(pos, world);
                            });
            assertSame(
                    first,
                    scope.source(
                            world,
                            17,
                            new ChunkPos(1, -1),
                            pos,
                            () -> {
                                fail("Empty result was reevaluated");
                                return null;
                            }));
            assertEquals(1, calls.get());
            assertEquals(1, scope.metrics().hits());
            assertEquals(1, scope.metrics().evaluations());
        }
    }

    @Test
    void decorationOwnershipTransferCannotExposeTheTargetGraphToLaterReads() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 1)) {
            var first =
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> new StructureInputChunk(ChunkPos.ZERO, world));
            scope.handoffForDecoration(ChunkPos.ZERO, Set.of(ChunkPos.ZERO.pack()));
            var pristine =
                    scope.source(
                            world,
                            17,
                            new ChunkPos(1, 0),
                            ChunkPos.ZERO,
                            () -> new StructureInputChunk(ChunkPos.ZERO, world));
            assertNotSame(first, pristine);
            assertEquals(2, scope.metrics().evaluations());
            assertEquals(1, scope.metrics().transferred());
            assertSame(
                    pristine,
                    scope.source(
                            world,
                            17,
                            new ChunkPos(0, 1),
                            ChunkPos.ZERO,
                            () -> {
                                throw new AssertionError();
                            }));
            assertThrows(
                    IllegalStateException.class,
                    () -> scope.handoffForDecoration(ChunkPos.ZERO, Set.of()));
        }
    }

    @Test
    void rejectsWrongSeedWorldQuerySourceAndThread() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 1)) {
            var calls = new AtomicInteger();
            java.util.function.Supplier<StructureInputChunk> factory =
                    () -> {
                        calls.incrementAndGet();
                        return new StructureInputChunk(ChunkPos.ZERO, world);
                    };
            assertThrows(
                    IllegalArgumentException.class,
                    () -> scope.source(world, 18, ChunkPos.ZERO, ChunkPos.ZERO, factory));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            scope.source(
                                    LevelHeightAccessor.create(-64, 384),
                                    17,
                                    ChunkPos.ZERO,
                                    ChunkPos.ZERO,
                                    factory));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> scope.source(world, 17, new ChunkPos(2, 0), ChunkPos.ZERO, factory));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> scope.source(world, 17, ChunkPos.ZERO, new ChunkPos(9, 0), factory));
            CompletableFuture.runAsync(
                            () ->
                                    assertThrows(
                                            IllegalStateException.class,
                                            () ->
                                                    scope.source(
                                                            world,
                                                            17,
                                                            ChunkPos.ZERO,
                                                            ChunkPos.ZERO,
                                                            factory)))
                    .join();
            assertEquals(0, calls.get());
        }
    }

    @Test
    void failedEvaluationsDoNotBecomeAuthorityAndCloseReleasesAllSources() {
        var world = LevelHeightAccessor.create(-64, 384);
        var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 1);
        var failure = new IllegalStateException("Real native failure");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                scope.source(
                                        world,
                                        17,
                                        ChunkPos.ZERO,
                                        ChunkPos.ZERO,
                                        () -> {
                                            throw failure;
                                        })));
        assertEquals(0, scope.metrics().resident());
        scope.source(
                world,
                17,
                ChunkPos.ZERO,
                ChunkPos.ZERO,
                () -> new StructureInputChunk(ChunkPos.ZERO, world));
        scope.close();
        assertEquals(0, scope.metrics().resident());
        assertTrue(scope.metrics().closed());
        assertThrows(
                IllegalStateException.class,
                () -> scope.source(world, 17, ChunkPos.ZERO, ChunkPos.ZERO, () -> null));
    }

    @Test
    void residentBoundIsFiniteAndSeparateScopesNeverShare() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 1);
                var other = new ScopedStructureSources(world, 18, ChunkPos.ZERO, 1)) {
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) {
                    var query = new ChunkPos(x, z);
                    for (int sx = x - 8; sx <= x + 8; sx++)
                        for (int sz = z - 8; sz <= z + 8; sz++) {
                            var source = new ChunkPos(sx, sz);
                            scope.source(
                                    world,
                                    17,
                                    query,
                                    source,
                                    () -> new StructureInputChunk(source, world));
                        }
                }
            assertEquals(361, scope.metrics().peakResident());
            assertEquals(361, scope.metrics().evaluations());
            assertEquals(9 * 289 - 361, scope.metrics().hits());
            assertNotSame(
                    scope.source(world, 17, ChunkPos.ZERO, ChunkPos.ZERO, () -> null),
                    other.source(
                            world,
                            18,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> new StructureInputChunk(ChunkPos.ZERO, world)));
        }
    }
}
