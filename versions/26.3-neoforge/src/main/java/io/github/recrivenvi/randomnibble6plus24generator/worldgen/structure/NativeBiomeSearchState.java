package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.MultiNoiseBiomeSourceAccessor;
import net.minecraft.world.level.biome.*;

/** Native climate leaf identity is both an input and an output of source evaluation. */
public final class NativeBiomeSearchState {
    public interface Owner {
        ThreadLocal<?> mosaic$nativeBiomeCursor();
    }

    private final ThreadLocal<Object> cursor;

    @SuppressWarnings("unchecked")
    NativeBiomeSearchState(ThreadLocal<?> cursor) {
        this.cursor = (ThreadLocal<Object>) cursor;
    }

    static NativeBiomeSearchState from(BiomeSource source) {
        var type = source.getClass();
        if (type == FixedBiomeSource.class
                || type == TheEndBiomeSource.class
                || type == CheckerboardColumnBiomeSource.class)
            return new NativeBiomeSearchState(null);
        if (type != MultiNoiseBiomeSource.class) return null;
        var parameters =
                ((MultiNoiseBiomeSourceAccessor) source).randomnibble6plus24generator$parameters();
        if (parameters.getClass() != Climate.ParameterList.class
                || !(parameters instanceof Owner owner)
                || owner.mosaic$nativeBiomeCursor() == null) return null;
        return new NativeBiomeSearchState(owner.mosaic$nativeBiomeCursor());
    }

    boolean hasTree() {
        return cursor != null;
    }

    Object capture() {
        return cursor == null ? null : cursor.get();
    }

    void replay(Object exit) {
        if (cursor != null) cursor.set(exit);
        else if (exit != null) throw new IllegalArgumentException("Foreign native climate token");
    }
}
