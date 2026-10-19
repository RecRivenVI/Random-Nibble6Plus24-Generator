package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

class ScopedStructureSourcesTest {
    @Test
    void evaluatedEmptySourcesAreReusedAndTargetOwnershipIsTransferred() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 2, 8)) {
            var first =
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> new StructureInputChunk(ChunkPos.ZERO, world));
            assertSame(
                    first,
                    scope.source(
                            world,
                            17,
                            new ChunkPos(2, -2),
                            ChunkPos.ZERO,
                            () -> {
                                throw new AssertionError("Evaluated empty was recomputed");
                            }));
            scope.handoffForDecoration(ChunkPos.ZERO, Set.of(ChunkPos.ZERO.toLong()));
            var read =
                    scope.source(
                            world,
                            17,
                            new ChunkPos(1, 0),
                            ChunkPos.ZERO,
                            () -> new StructureInputChunk(ChunkPos.ZERO, world));
            assertNotSame(first, read);
            assertEquals(2, scope.metrics().evaluations());
            assertEquals(1, scope.metrics().hits());
            assertEquals(1, scope.metrics().transferred());
            assertThrows(
                    IllegalStateException.class,
                    () -> scope.handoffForDecoration(ChunkPos.ZERO, Set.of()));
        }
    }

    @Test
    void rejectsWrongWorldSeedQuerySourceAndThreadBeforeEvaluation() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 2, 8)) {
            Supplier<StructureInputChunk> never =
                    () -> {
                        throw new AssertionError();
                    };
            assertThrows(
                    IllegalArgumentException.class,
                    () -> scope.source(world, 18, ChunkPos.ZERO, ChunkPos.ZERO, never));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            scope.source(
                                    LevelHeightAccessor.create(-64, 384),
                                    17,
                                    ChunkPos.ZERO,
                                    ChunkPos.ZERO,
                                    never));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> scope.source(world, 17, new ChunkPos(3, 0), ChunkPos.ZERO, never));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> scope.source(world, 17, ChunkPos.ZERO, new ChunkPos(9, 0), never));
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
                                                            never)))
                    .join();
            assertEquals(0, scope.metrics().queries());
        }
    }

    @Test
    void nativeFailureRemainsVisibleAndCloseReleasesAllData() {
        var world = LevelHeightAccessor.create(-64, 384);
        var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 2, 8);
        var failure = new IllegalStateException("Injected native failure");
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
        assertTrue(scope.metrics().closed());
        assertEquals(0, scope.metrics().resident());
        assertThrows(
                IllegalStateException.class,
                () -> scope.source(world, 17, ChunkPos.ZERO, ChunkPos.ZERO, () -> null));
    }

    @Test
    void exactNativeReferenceDomainHas441UniqueSourcesAndNoCrossSeedReuse() {
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 2, 8);
                var other = new ScopedStructureSources(world, 18, ChunkPos.ZERO, 2, 8)) {
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    var query = new ChunkPos(x, z);
                    for (int sx = x - 8; sx <= x + 8; sx++)
                        for (int sz = z - 8; sz <= z + 8; sz++) {
                            var pos = new ChunkPos(sx, sz);
                            scope.source(
                                    world,
                                    17,
                                    query,
                                    pos,
                                    () -> new StructureInputChunk(pos, world));
                        }
                }
            assertEquals(441, scope.metrics().evaluations());
            assertEquals(441, scope.metrics().peakResident());
            assertEquals(25 * 289 - 441, scope.metrics().hits());
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
