package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.StructureManager;

/** Only native region construction is bound; normal physical gameplay never enters this scope. */
public final class SeedLocalRegionScope {
    public record Binding(StructureGenerationContext context, StructureManager structures) {}

    private static final ThreadLocal<Binding> ACTIVE = new ThreadLocal<>();

    private SeedLocalRegionScope() {}

    public static <T> T construct(
            StructureGenerationContext context,
            StructureManager structures,
            Supplier<T> constructor) {
        if (ACTIVE.get() != null)
            throw new IllegalStateException("Nested local region construction");
        Objects.requireNonNull(context).seed();
        ACTIVE.set(new Binding(context, Objects.requireNonNull(structures)));
        try {
            return constructor.get();
        } finally {
            ACTIVE.remove();
        }
    }

    public static Binding current(ServerLevel host) {
        var binding = ACTIVE.get();
        if (binding != null && binding.context().host() != host)
            throw new IllegalArgumentException("Foreign region host");
        return binding;
    }

    public static Binding current(ServerChunkCache chunks) {
        var binding = ACTIVE.get();
        if (binding != null && binding.context().host().getChunkSource() != chunks)
            throw new IllegalArgumentException("Foreign region chunk source");
        return binding;
    }

    public static Binding current() {
        return ACTIVE.get();
    }

    public static boolean isBound() {
        return ACTIVE.get() != null;
    }
}
