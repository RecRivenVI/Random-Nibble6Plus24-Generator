package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConcentricRingScopeTest {
    private static ConcentricRingScope scope(ChunkPos pos) {
        return new ConcentricRingScope(pos, 123L, 11, ConcentricRingScope.Fallback.NONE);
    }

    @Test void keepsTheProvenVanillaV2Radius() {
        assertEquals(11, ConcentricRingScope.v2QueryRadius());
    }

    @Test void searchEnvelopeIncludesAllSevenChunkAdjustments() {
        var scope = scope(new ChunkPos(-120, 73));
        for (int x = -140; x <= -100; x++) for (int z = 53; z <= 93; z++) {
            boolean possible = false;
            for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                possible |= scope.contains(x + dx, z + dz);
            }
            assertEquals(possible, scope.mayReachQueryRange(x, z));
        }
    }

    @Test void subtractionDoesNotOverflowAtIntegerLimits() {
        var scope = scope(new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertTrue(scope.mayReachQueryRange(Integer.MIN_VALUE + 18, Integer.MAX_VALUE - 18));
        assertFalse(scope.mayReachQueryRange(Integer.MAX_VALUE, Integer.MIN_VALUE));
    }

    @Test void skipsOnlyProvenIrrelevantSuppliersAndFiltersTheirSentinels() {
        var scope = scope(ChunkPos.ZERO);
        AtomicInteger calls = new AtomicInteger();
        var outside = scope.search(19, 0, () -> { calls.incrementAndGet(); return ChunkPos.ZERO; }, Runnable::run);
        var boundary = scope.search(18, 0, () -> { calls.incrementAndGet(); return new ChunkPos(11, 0); }, Runnable::run);
        assertEquals(1, calls.get());
        assertEquals(List.of(new ChunkPos(11, 0)), scope.finish(List.of(outside.join(), boundary.join()), 1));
        assertEquals(2, scope.metrics().slotsConsidered());
        assertEquals(1, scope.metrics().biomeSearches());
        assertEquals(1, scope.metrics().searchesSkipped());
    }

    @Test void preservesNativeSlotOrderAndDuplicates() {
        var scope = scope(ChunkPos.ZERO);
        var first = new ChunkPos(1, 2);
        var second = new ChunkPos(-3, 4);
        assertEquals(List.of(first, second, first), scope.finish(List.of(first, new ChunkPos(100, 0), second, first), 1));
    }

    @Test void unexpectedOutOfScopeQueryFailsClosed() {
        var scope = scope(ChunkPos.ZERO);
        assertDoesNotThrow(() -> scope.requireQueryInRange(11, -11));
        assertThrows(IllegalStateException.class, () -> scope.requireQueryInRange(12, 0));
    }

    @Test void unprovenInputsKeepFullNativeSearchAndResults() {
        var scope = new ConcentricRingScope(ChunkPos.ZERO, 1L, 11, ConcentricRingScope.Fallback.CONCENTRIC_EXCLUSION);
        scope.beginRing();
        var far = new ChunkPos(500, -500);
        assertEquals(far, scope.search(500, -500, () -> far, Runnable::run).join());
        assertEquals(List.of(far), scope.finish(List.of(far), 4));
        assertDoesNotThrow(() -> scope.requireQueryInRange(500, -500));
        assertEquals(1, scope.metrics().fullRingFallbacks());
        assertFalse(ConcentricRingScope.supportsBiomeSource(BiomeSource.class));
        assertTrue(ConcentricRingScope.supportsBiomeSource(MultiNoiseBiomeSource.class));
    }

    @Test void exceptionsAreNeverConvertedToMissingStructures() {
        var scope = scope(ChunkPos.ZERO);
        assertThrows(CompletionException.class, () -> scope.search(0, 0, () -> {
            throw new IllegalStateException("native failure");
        }, Runnable::run).join());
    }
}
