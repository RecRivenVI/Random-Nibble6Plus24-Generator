package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

class StructureReferenceCopyTest {
    @Test
    void keepsNativeIterationAndDetachesMutation() {
        var original = new LongOpenHashSet();
        original.add(-42949672876L);
        original.add(-51539607471L);
        assertFalse(
                java.util.Arrays.equals(
                        original.toLongArray(), new LongOpenHashSet(original).toLongArray()));
        var copy = CompactStructureInputs.copyReferenceSet(original);
        assertArrayEquals(original.toLongArray(), copy.toLongArray());
        copy.clear();
        assertEquals(2, original.size());
    }

    @Test
    void refusesUnknownReferenceContainers() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> CompactStructureInputs.copyReferenceSet(new LongArraySet()));
    }

    @Test
    void refusesSubclassWhoseCloneWouldRetainMutationAuthority() {
        var unknown =
                new LongOpenHashSet() {
                    @Override
                    public LongOpenHashSet clone() {
                        return this;
                    }
                };
        unknown.add(7);
        assertThrows(
                UnsupportedOperationException.class,
                () -> CompactStructureInputs.copyReferenceSet(unknown));
        assertTrue(unknown.contains(7));
    }
}
