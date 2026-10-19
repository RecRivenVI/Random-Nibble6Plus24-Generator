package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.LevelResource;

/** No cross-owner or cross-seed graph; all physical SavedData access is server-thread owned. */
public final class MosaicStructureOverlayStore {
    private MosaicStructureOverlayStore() {}

    public static List<StructureStart> startsForOwner(ServerLevel level, ChunkPos owner) {
        requireThread(level);
        var chunk = level.getChunkSource().getChunkNow(owner.x, owner.z);
        if (chunk == null) return List.of();
        var starts = new java.util.ArrayList<StructureStart>();
        chunk.getAllStarts().values().stream()
                .filter(start -> start != null && start.isValid())
                .forEach(starts::add);
        starts.addAll(externalStarts(level, owner));
        return List.copyOf(starts);
    }

    public static void validateAtLoad(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Off-thread overlay load");
        var path =
                server.getWorldPath(LevelResource.ROOT)
                        .resolve("data")
                        .resolve(MosaicStructureOverlayData.ID + ".dat");
        boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        var data =
                server.overworld()
                        .getDataStorage()
                        .get(MosaicStructureOverlayData.FACTORY, MosaicStructureOverlayData.ID);
        if (exists && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || data == null))
            throw new IllegalStateException("Unreadable Mosaic structure projection authority");
        if (data == null)
            server.overworld()
                    .getDataStorage()
                    .set(MosaicStructureOverlayData.ID, new MosaicStructureOverlayData());
    }

    public static void publish(
            ServerLevel level, ChunkPos owner, long seed, List<CompoundTag> starts) {
        requireThread(level);
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        if (seed != runtime.resolveLocalWorldSeed(level.dimension(), owner))
            throw new IllegalStateException("Structure projection belongs to another local seed");
        data(level)
                .replace(
                        key(level, owner),
                        new MosaicStructureOverlayData.Projection(owner.x, owner.z, seed, starts));
    }

    public static List<StructureStart> externalStarts(ServerLevel level, ChunkPos owner) {
        requireThread(level);
        var projection = data(level).get(key(level, owner));
        if (projection == null) return List.of();
        long expected =
                MosaicWorldIdentity.runtimeContext(level)
                        .orElseThrow()
                        .resolveLocalWorldSeed(level.dimension(), owner);
        if (projection.x() != owner.x || projection.z() != owner.z || projection.seed() != expected)
            throw new IllegalStateException("Structure projection provenance mismatch");
        var serialization = StructurePieceSerializationContext.fromLevel(level);
        return projection.starts().stream()
                .map(tag -> StructureStart.loadStaticStart(serialization, tag, expected))
                .map(
                        start -> {
                            if (start == null || !start.isValid())
                                throw new IllegalStateException(
                                        "Invalid stored structure projection");
                            return start;
                        })
                .toList();
    }

    private static MosaicStructureOverlayData data(ServerLevel level) {
        var data =
                level.getServer()
                        .overworld()
                        .getDataStorage()
                        .get(MosaicStructureOverlayData.FACTORY, MosaicStructureOverlayData.ID);
        if (data == null)
            throw new IllegalStateException("Overlay was not validated at world load");
        return data;
    }

    private static String key(ServerLevel level, ChunkPos owner) {
        return level.dimension().location() + ":" + Long.toUnsignedString(owner.toLong());
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Physical overlay belongs to the server thread");
    }
}
