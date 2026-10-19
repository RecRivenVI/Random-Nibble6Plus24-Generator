package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.MultiNoiseSearchAccess;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;

/** Native tie-breaking state is an input AND an output of a structure evaluation. */
public final class NativeBiomeSearchState {
    public interface Owner {
        ThreadLocal<?> mosaic$searchState();
    }

    private final ThreadLocal<Object> lastResult;

    @SuppressWarnings("unchecked")
    NativeBiomeSearchState(ThreadLocal<?> lastResult) {
        this.lastResult = (ThreadLocal<Object>) lastResult;
    }

    static boolean supports(BiomeSource source) {
        var type = source.getClass();
        return type == MultiNoiseBiomeSource.class
                || type == FixedBiomeSource.class
                || type == TheEndBiomeSource.class;
    }

    static NativeBiomeSearchState from(BiomeSource source) {
        if (!supports(source)) throw new IllegalArgumentException("Unaudited biome search state");
        if (source.getClass() != MultiNoiseBiomeSource.class)
            return new NativeBiomeSearchState(null);
        var parameters = ((MultiNoiseSearchAccess) source).mosaic$parameters();
        if (!(parameters instanceof Owner owner) || owner.mosaic$searchState() == null) return null;
        return new NativeBiomeSearchState(owner.mosaic$searchState());
    }

    boolean hasTree() {
        return lastResult != null;
    }

    Object capture() {
        return lastResult == null ? null : lastResult.get();
    }

    void replay(Object exit) {
        if (lastResult != null) lastResult.set(exit);
        else if (exit != null) throw new IllegalArgumentException("Foreign biome search token");
    }
}
