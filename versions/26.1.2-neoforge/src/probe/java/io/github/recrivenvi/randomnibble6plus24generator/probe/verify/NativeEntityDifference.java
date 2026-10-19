package io.github.recrivenvi.randomnibble6plus24generator.probe.verify;

import java.util.*;
import net.minecraft.nbt.*;

/** Full NBT diagnostic; equality exceptions are owned exclusively by NativeEntityAcceptance. */
final class NativeEntityDifference {
    private NativeEntityDifference() {}

    record Difference(String path, String before, String after) {}

    static CompoundTag attributesById(CompoundTag entity) {
        var result = new CompoundTag();
        for (var attribute : entity.getListOrEmpty("attributes")) {
            if (!(attribute instanceof CompoundTag compound))
                throw new IllegalArgumentException("Malformed diagnostic attribute");
            String id = compound.getStringOr("id", "");
            if (id.isEmpty() || result.contains(id))
                throw new IllegalArgumentException("Missing/duplicate attribute identity");
            result.put(id, compound.copy());
        }
        return result;
    }

    static List<Difference> differences(Tag before, Tag after) {
        var result = new ArrayList<Difference>();
        collect("$", before, after, result);
        return List.copyOf(result);
    }

    private static void collect(String path, Tag a, Tag b, List<Difference> result) {
        if (Objects.equals(a, b)) return;
        if (a instanceof CompoundTag left && b instanceof CompoundTag right) {
            var keys = new TreeSet<>(left.keySet());
            keys.addAll(right.keySet());
            for (var key : keys) collect(path + "." + key, left.get(key), right.get(key), result);
        } else if (a instanceof ListTag left && b instanceof ListTag right) {
            for (int i = 0; i < Math.max(left.size(), right.size()); i++)
                collect(
                        path + "[" + i + "]",
                        i < left.size() ? left.get(i) : null,
                        i < right.size() ? right.get(i) : null,
                        result);
        } else
            result.add(
                    new Difference(
                            path,
                            Objects.toString(a, "<absent>"),
                            Objects.toString(b, "<absent>")));
    }
}
