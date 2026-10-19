package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;

/** Binds only native population-region construction, never physical runtime simulation. */
public final class SpawnConstructorScope {
    public record Binding(ServerLevel level, long seed, RandomState random) {}

    private static final ThreadLocal<Binding> ACTIVE = new ThreadLocal<>();

    private SpawnConstructorScope() {}

    public static <T> T construct(
            ServerLevel level, long seed, RandomState random, Supplier<T> constructor) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested population constructor");
        ACTIVE.set(new Binding(level, seed, random));
        try {
            return constructor.get();
        } finally {
            ACTIVE.remove();
        }
    }

    public static Binding current(ServerLevel level) {
        var binding = ACTIVE.get();
        if (binding != null && binding.level() != level)
            throw new IllegalStateException("Foreign population host");
        return binding;
    }

    public static Binding current() {
        return ACTIVE.get();
    }
}
