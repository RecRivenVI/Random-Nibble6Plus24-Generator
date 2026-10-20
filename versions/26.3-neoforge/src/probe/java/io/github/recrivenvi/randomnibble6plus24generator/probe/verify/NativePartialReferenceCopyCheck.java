package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/** Native library/container checks in the probe module, not a split-package test module. */
final class NativePartialReferenceCopyCheck {
    private NativePartialReferenceCopyCheck() {}

    static void verify() {
        try {
            var copy =
                    CompactStructureInputs.class.getDeclaredMethod(
                            "copyNativeReferenceSet", LongSet.class);
            if (!copy.trySetAccessible())
                throw new IllegalStateException("Cannot check native reference copy");
            var source = new LongOpenHashSet();
            source.add(-42949672876L);
            source.add(-51539607471L);
            if (Arrays.equals(source.toLongArray(), new LongOpenHashSet(source).toLongArray()))
                throw new IllegalStateException("Native reference reordering positive was not hit");
            var cloned = (LongOpenHashSet) copy.invoke(null, source);
            if (!Arrays.equals(source.toLongArray(), cloned.toLongArray()))
                throw new IllegalStateException("Native iterator changed on clone");
            cloned.clear();
            if (source.size() != 2) throw new IllegalStateException("Mutable reference alias");
            var alias =
                    new LongOpenHashSet() {
                        @Override
                        public LongOpenHashSet clone() {
                            return this;
                        }
                    };
            for (LongSet unsupported : java.util.List.of(new LongArraySet(), alias)) {
                try {
                    copy.invoke(null, unsupported);
                    throw new IllegalStateException("Unaudited container accepted");
                } catch (InvocationTargetException expected) {
                    if (!(expected.getCause() instanceof UnsupportedOperationException))
                        throw expected;
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native reference regression failed", failure);
        }
    }
}
