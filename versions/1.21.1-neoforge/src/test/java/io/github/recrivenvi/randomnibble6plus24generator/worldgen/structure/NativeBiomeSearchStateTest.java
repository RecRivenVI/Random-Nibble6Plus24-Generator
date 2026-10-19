package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Climate;
import org.junit.jupiter.api.Test;

class NativeBiomeSearchStateTest {
    private static Climate.ParameterList<String> nativeList() {
        return new Climate.ParameterList<>(
                List.of(
                        Pair.of(Climate.parameters(-1, 0, 0, 0, 0, 0, 0), "A"),
                        Pair.of(Climate.parameters(1, 0, 0, 0, 0, 0, 0), "B")));
    }

    private static Climate.TargetPoint at(float value) {
        return Climate.target(value, 0, 0, 0, 0, 0);
    }

    private static NativeBiomeSearchState nativeState(Climate.ParameterList<?> list)
            throws Exception {
        // Unit JVMs do not load Mixins. Read the same native fields; never replace native search.
        var field = Climate.ParameterList.class.getDeclaredField("index");
        field.setAccessible(true);
        var tree = field.get(list);
        var last = tree.getClass().getDeclaredField("lastResult");
        last.setAccessible(true);
        return new NativeBiomeSearchState((ThreadLocal<?>) last.get(tree));
    }

    @Test
    void nativeEntryLeafChangesTieResultSoMismatchMustEvaluateExactly() throws Exception {
        var list = nativeList();
        var world = LevelHeightAccessor.create(-64, 384);
        try (var scope =
                new ScopedStructureSources(world, 17, ChunkPos.ZERO, 2, 8, nativeState(list))) {
            var value = new AtomicReference<String>();
            list.findValue(at(-1));
            var first =
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> {
                                value.set(list.findValue(at(0)));
                                return new StructureInputChunk(ChunkPos.ZERO, world);
                            });
            assertEquals("A", value.get());
            list.findValue(at(1));
            var second =
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> {
                                value.set(list.findValue(at(0)));
                                return new StructureInputChunk(ChunkPos.ZERO, world);
                            });
            assertEquals("B", value.get());
            assertNotSame(first, second);
            assertEquals(1, scope.metrics().stateMismatches());
            assertEquals(2, scope.metrics().evaluations());
            assertSame(
                    second,
                    scope.source(
                            world,
                            17,
                            ChunkPos.ZERO,
                            ChunkPos.ZERO,
                            () -> {
                                throw new AssertionError();
                            }));
            assertEquals("B", list.findValue(at(0)));
            assertEquals(1, scope.metrics().stateReplays());
        }
    }

    @Test
    void aHitReplaysNativeExitLeafAndTheNextQueryInBothDirections() throws Exception {
        for (int sign : new int[] {-1, 1}) {
            var list = nativeList();
            var state = nativeState(list);
            var world = LevelHeightAccessor.create(-64, 384);
            try (var scope = new ScopedStructureSources(world, 17, ChunkPos.ZERO, 2, 8, state)) {
                list.findValue(at(-sign));
                Object entry = state.capture();
                var cached =
                        scope.source(
                                world,
                                17,
                                ChunkPos.ZERO,
                                ChunkPos.ZERO,
                                () -> {
                                    list.findValue(at(sign));
                                    return new StructureInputChunk(ChunkPos.ZERO, world);
                                });
                Object exit = state.capture();
                String expectedNext = list.findValue(at(0));
                list.findValue(at(-sign));
                assertSame(entry, state.capture());
                assertNotEquals(expectedNext, list.findValue(at(0)));
                list.findValue(at(-sign));
                assertSame(
                        cached,
                        scope.source(
                                world,
                                17,
                                ChunkPos.ZERO,
                                ChunkPos.ZERO,
                                () -> {
                                    throw new AssertionError();
                                }));
                assertSame(exit, state.capture());
                assertEquals(expectedNext, list.findValue(at(0)));
                assertEquals(1, scope.metrics().stateReplays());
                assertEquals(0, scope.metrics().stateMismatches());
            }
            assertEquals(sign < 0 ? "A" : "B", list.findValue(at(0)));
        }
    }
}
