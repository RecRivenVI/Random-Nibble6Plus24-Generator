package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.status.WorldGenContext;

/** Identity-keyed bridge for APIs that do not carry session state explicitly. */
public final class GenerationContextRegistry {

    private static final Map<Object, IsolatedGenerationContext> CONTEXTS = new IdentityHashMap<>();

    private GenerationContextRegistry() {
    }

    public static synchronized void bind(WorldGenContext worldGenContext, IsolatedGenerationContext context) {
        bindIdentity(worldGenContext, context);
    }

    public static synchronized void bind(
            StaticCache2D<GenerationChunkHolder> cache,
            IsolatedGenerationContext context) {
        bindIdentity(cache, context);
    }

    public static synchronized Optional<IsolatedGenerationContext> find(WorldGenContext worldGenContext) {
        return Optional.ofNullable(CONTEXTS.get(worldGenContext));
    }

    public static synchronized Optional<IsolatedGenerationContext> find(
            StaticCache2D<GenerationChunkHolder> cache) {
        return Optional.ofNullable(CONTEXTS.get(cache));
    }

    public static synchronized void unbind(IsolatedGenerationContext context) {
        CONTEXTS.entrySet().removeIf(entry -> entry.getValue() == context);
    }

    public static synchronized int bindingCount() {
        return CONTEXTS.size();
    }

    private static void bindIdentity(Object key, IsolatedGenerationContext context) {
        IsolatedGenerationContext existing = CONTEXTS.put(key, context);
        if (existing != null && existing != context) {
            throw new IllegalStateException("Generation bridge identity collision");
        }
    }
}
