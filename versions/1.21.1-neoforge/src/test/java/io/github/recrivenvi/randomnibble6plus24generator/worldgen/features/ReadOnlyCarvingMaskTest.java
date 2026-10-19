package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarvingMask;
import org.junit.jupiter.api.Test;

class ReadOnlyCarvingMaskTest {
    @Test
    void nativeBitsStreamsAndAdditionalPredicateArePreserved() {
        var source = new CarvingMask(384, -64);
        source.set(1, -4, 2);
        source.set(4, 20, 7);
        source.setAdditionalMask((x, y, z) -> x == 3 && y == 30 && z == 5);
        var view = new ReadOnlyCarvingMask(source, api -> fail(api));
        assertArrayEquals(source.toArray(), view.toArray());
        assertTrue(view.get(1, -4, 2));
        assertTrue(view.get(3, 30, 5));
        assertFalse(view.get(2, -4, 2));
        var pos = new ChunkPos(-12, 17);
        assertEquals(source.stream(pos).toList(), view.stream(pos).toList());
        var detached = view.toArray();
        java.util.Arrays.fill(detached, 0L);
        assertTrue(source.get(1, -4, 2));
    }

    @Test
    void neitherBitsNorAdditionalPredicateCanMutateNeighbor() {
        var source = new CarvingMask(384, -64);
        source.set(1, -4, 2);
        var before = source.toArray();
        var rejected = new ArrayList<String>();
        var view = new ReadOnlyCarvingMask(source, rejected::add);
        view.set(3, 30, 5);
        view.setAdditionalMask((x, y, z) -> true);
        assertArrayEquals(before, source.toArray());
        assertFalse(source.get(3, 30, 5));
        assertFalse(view.get(3, 30, 5));
        assertEquals(
                java.util.List.of("chunk_carving_mask", "chunk_carving_mask_filter"), rejected);
    }
}
