package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;

/**
 * Stores and rehydrates same-local-universe structure starts whose pieces
 * cross into another physical Chunk. The store is keyed by the owning target
 * Chunk, never by a physical neighbor, and is persisted as ordinary SavedData.
 */
public final class MosaicStructureOverlayStore {

    private static final Map<MinecraftServer, Map<String, List<StructureStart>>> CACHE =
            new ConcurrentHashMap<>();

    private MosaicStructureOverlayStore() {
    }

    public static synchronized void publish(
            ServerLevel level,
            ChunkPos owner,
            long localWorldSeed,
            List<CompoundTag> externalStarts) {
        String key = key(level, owner);
        if (externalStarts.isEmpty()) {
            MosaicStructureOverlayData data = level.getServer().getDataStorage()
                    .get(MosaicStructureOverlayData.TYPE);
            if (data != null) data.remove(key);
            CACHE.computeIfAbsent(level.getServer(), ignored -> new ConcurrentHashMap<>())
                    .remove(key);
            MosaicStructureIndexStore.remove(level, owner);
            return;
        }
        MosaicStructureOverlayData data = level.getServer().getDataStorage()
                .computeIfAbsent(MosaicStructureOverlayData.TYPE);
        data.put(key, new MosaicStructureOverlayData.ChunkProjection(
                owner.x(), owner.z(), localWorldSeed, externalStarts));
        CACHE.computeIfAbsent(level.getServer(), ignored -> new ConcurrentHashMap<>())
                .remove(key);
    }

    /** Publishes the physical owner's starts and its projected cross-Chunk starts as one index update. */
    public static synchronized void publishCanonical(
            ServerLevel level,
            ChunkPos owner,
            long localWorldSeed,
            ChunkAccess canonicalChunk,
            List<CompoundTag> externalStarts) {
        publish(level, owner, localWorldSeed, externalStarts);
        List<StructureStart> ownerStarts = canonicalChunk.getAllStarts().values().stream()
                .filter(start -> start != null && start.isValid())
                .toList();
        MosaicStructureIndexStore.publish(level, owner, localWorldSeed, ownerStarts, externalStarts);
    }

    public static List<StructureStart> startsForOwner(ServerLevel level, ChunkPos owner) {
        List<StructureStart> result = new ArrayList<>();
        var physical = level.getChunkSource().getChunkNow(owner.x(), owner.z());
        if (physical != null) {
            physical.getAllStarts().values().stream()
                    .filter(start -> start != null && start.isValid())
                    .forEach(result::add);
        }
        result.addAll(externalStarts(level, owner));
        return List.copyOf(result);
    }

    public static List<StructureStart> externalStarts(ServerLevel level, ChunkPos owner) {
        String key = key(level, owner);
        Map<String, List<StructureStart>> serverCache = CACHE.computeIfAbsent(
                level.getServer(), ignored -> new ConcurrentHashMap<>());
        List<StructureStart> cached = serverCache.get(key);
        if (cached != null) return cached;

        MosaicStructureOverlayData data = level.getServer().getDataStorage()
                .get(MosaicStructureOverlayData.TYPE);
        if (data == null) return List.of();
        MosaicStructureOverlayData.ChunkProjection projection = data.get(key);
        if (projection == null) return List.of();
        long expectedSeed = MosaicWorldIdentity.runtimeContext(level).orElseThrow()
                .resolveLocalWorldSeed(level.dimension(), owner);
        if (projection.chunkX() != owner.x() || projection.chunkZ() != owner.z()
                || projection.localWorldSeed() != expectedSeed) {
            throw new IllegalStateException(
                    "Mosaic structure overlay provenance mismatch for " + level.dimension().identifier()
                            + " " + owner + ": savedSeed=" + projection.localWorldSeed()
                            + ", expectedSeed=" + expectedSeed);
        }
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        List<StructureStart> starts = projection.externalStarts().stream()
                .map(tag -> StructureStart.loadStaticStart(
                        context, tag.copy(), projection.localWorldSeed()))
                .filter(start -> start != null && start.isValid())
                .toList();
        serverCache.put(key, starts);
        return starts;
    }

    public static void clear(MinecraftServer server) {
        CACHE.remove(server);
    }

    private static String key(ServerLevel level, ChunkPos owner) {
        return level.dimension().identifier() + ":" + Long.toUnsignedString(owner.pack());
    }
}
