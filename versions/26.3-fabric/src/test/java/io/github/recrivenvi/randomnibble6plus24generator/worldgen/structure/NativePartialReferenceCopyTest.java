package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

class NativePartialReferenceCopyTest {
    @Test
    void nativeTableOrderIsPreservedWithoutMutationAuthority() {
        var nativeSet = new LongOpenHashSet();
        nativeSet.add(-42949672876L);
        nativeSet.add(-51539607471L);
        assertFalse(
                java.util.Arrays.equals(
                        nativeSet.toLongArray(), new LongOpenHashSet(nativeSet).toLongArray()));
        var copy = CompactStructureInputs.copyNativeReferenceSet(nativeSet);
        assertArrayEquals(nativeSet.toLongArray(), copy.toLongArray());
        copy.clear();
        assertEquals(2, nativeSet.size());
    }

    @Test
    void unauditedContainersAndSelfAliasingCloneAreRejected() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> CompactStructureInputs.copyNativeReferenceSet(new LongArraySet()));
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
                () -> CompactStructureInputs.copyNativeReferenceSet(unknown));
        assertTrue(unknown.contains(7));
    }
}
