package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;

/** Identity-keyed bridge for the synchronous physical SPAWN seed view. */
public final class MosaicSpawnContextRegistry {

    private static final Map<Object, MosaicSpawnGenerationContext> CONTEXTS = new IdentityHashMap<>();

    private MosaicSpawnContextRegistry() {
    }

    public static synchronized void bind(
            StaticCache2D<GenerationChunkHolder> cache,
            MosaicSpawnGenerationContext context) {
        bindIdentity(cache, context);
    }

    public static synchronized void bind(
            WorldGenRegion region,
            MosaicSpawnGenerationContext context) {
        bindIdentity(region, context);
    }

    public static synchronized Optional<MosaicSpawnGenerationContext> find(
            StaticCache2D<GenerationChunkHolder> cache) {
        return Optional.ofNullable(CONTEXTS.get(cache));
    }

    public static synchronized Optional<MosaicSpawnGenerationContext> find(
            WorldGenRegion region) {
        return Optional.ofNullable(CONTEXTS.get(region));
    }

    public static synchronized void unbind(MosaicSpawnGenerationContext context) {
        CONTEXTS.entrySet().removeIf(entry -> entry.getValue() == context);
    }

    public static synchronized int bindingCount() {
        return CONTEXTS.size();
    }

    private static void bindIdentity(Object key, MosaicSpawnGenerationContext context) {
        MosaicSpawnGenerationContext existing = CONTEXTS.put(key, context);
        if (existing != null && existing != context) {
            throw new IllegalStateException("Mosaic SPAWN context identity collision");
        }
    }
}
