package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.status.WorldGenContext;

/** Identity-keyed bridge for APIs that do not carry session state explicitly. */
public final class GenerationContextRegistry {

    private static final Map<Object, SurfaceGenerationContext> CONTEXTS = new IdentityHashMap<>();

    private GenerationContextRegistry() {
    }

    public static synchronized void bind(WorldGenContext worldGenContext, SurfaceGenerationContext context) {
        bindIdentity(worldGenContext, context);
    }

    public static synchronized void bind(
            StaticCache2D<GenerationChunkHolder> cache,
            SurfaceGenerationContext context) {
        bindIdentity(cache, context);
    }

    public static synchronized Optional<SurfaceGenerationContext> find(WorldGenContext worldGenContext) {
        return Optional.ofNullable(CONTEXTS.get(worldGenContext));
    }

    public static synchronized Optional<SurfaceGenerationContext> find(
            StaticCache2D<GenerationChunkHolder> cache) {
        return Optional.ofNullable(CONTEXTS.get(cache));
    }

    public static synchronized void unbind(SurfaceGenerationContext context) {
        CONTEXTS.entrySet().removeIf(entry -> entry.getValue() == context);
    }

    public static synchronized int bindingCount() {
        return CONTEXTS.size();
    }

    private static void bindIdentity(Object key, SurfaceGenerationContext context) {
        SurfaceGenerationContext existing = CONTEXTS.put(key, context);
        if (existing != null && existing != context) {
            throw new IllegalStateException("Generation bridge identity collision");
        }
    }
}
