package io.github.recrivenvi.randomnibble6plus24generator.probe.features;

import java.util.*;
import net.minecraft.nbt.*;

/** Diagnostics only: a bounded report never changes strict equality or its hash. */
final class NativeFeatureNbtDiff {
    private NativeFeatureNbtDiff() {}

    record Difference(String path, String before, String after) {}

    static List<Difference> compare(Tag before, Tag after) {
        var result = new ArrayList<Difference>();
        collect("$", before, after, result);
        return List.copyOf(result);
    }

    private static void collect(String path, Tag before, Tag after, List<Difference> result) {
        if (Objects.equals(before, after) || result.size() >= 100) return;
        if (before instanceof CompoundTag left && after instanceof CompoundTag right) {
            var keys = new TreeSet<>(left.getAllKeys());
            keys.addAll(right.getAllKeys());
            for (String key : keys)
                collect(path + "." + key, left.get(key), right.get(key), result);
        } else if (before instanceof ListTag left && after instanceof ListTag right) {
            for (int index = 0; index < Math.max(left.size(), right.size()); index++)
                collect(
                        path + "[" + index + "]",
                        index < left.size() ? left.get(index) : null,
                        index < right.size() ? right.get(index) : null,
                        result);
        } else
            result.add(
                    new Difference(
                            path,
                            Objects.toString(before, "<absent>"),
                            Objects.toString(after, "<absent>")));
    }
}
