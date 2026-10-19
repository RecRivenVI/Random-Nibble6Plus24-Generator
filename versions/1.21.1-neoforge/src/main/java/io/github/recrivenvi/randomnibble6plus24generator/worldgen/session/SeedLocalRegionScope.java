package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

/** Constructor-only binding; a completed region never owns an open session through this scope. */
public final class SeedLocalRegionScope {
    private static final ThreadLocal<StructureGenerationContext> ACTIVE = new ThreadLocal<>();

    private SeedLocalRegionScope() {}

    public static <T> T construct(StructureGenerationContext context, Supplier<T> constructor) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested region construction");
        Objects.requireNonNull(context).seed();
        ACTIVE.set(context);
        try {
            return constructor.get();
        } finally {
            ACTIVE.remove();
        }
    }

    public static StructureGenerationContext current(ServerLevel level) {
        var context = ACTIVE.get();
        if (context != null && context.host() != level)
            throw new IllegalArgumentException("Region belongs to another world instance");
        return context;
    }

    public static StructureGenerationContext current(ServerChunkCache chunks) {
        var context = ACTIVE.get();
        if (context != null && context.host().getChunkSource() != chunks)
            throw new IllegalArgumentException("Region belongs to another chunk source");
        return context;
    }

    public static boolean isBound() {
        return ACTIVE.get() != null;
    }
}
