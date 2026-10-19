package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;

/** Constructor-only seed routing; no retained isolated universe or structure-state rebuild. */
public record SpawnConstructorScope(ServerLevel host, long seed, RandomState randomState) {
    private static final ThreadLocal<SpawnConstructorScope> ACTIVE = new ThreadLocal<>();

    public static <T> T construct(
            ServerLevel host, long seed, RandomState random, Supplier<T> action) {
        if (ACTIVE.get() != null)
            throw new IllegalStateException("Nested physical SPAWN construction");
        ACTIVE.set(new SpawnConstructorScope(host, seed, random));
        try {
            return action.get();
        } finally {
            ACTIVE.remove();
        }
    }

    public static SpawnConstructorScope current() {
        return ACTIVE.get();
    }
}
