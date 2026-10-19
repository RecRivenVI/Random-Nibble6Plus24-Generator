package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

/** Persists exact generated projection inputs without consulting another physical seed. */
public final class MosaicStructureOverlayStore {
    private MosaicStructureOverlayStore() {}

    public static void validateAtLoad(MinecraftServer server) {
        if (!server.isSameThread())
            throw new IllegalStateException("Off-thread overlay validation");
        var path =
                server.getWorldPath(LevelResource.ROOT)
                        .resolve("data")
                        .resolve(MosaicStructureOverlayData.ID + ".dat");
        boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        var storage = server.overworld().getDataStorage();
        var data = storage.get(MosaicStructureOverlayData::load, MosaicStructureOverlayData.ID);
        if (exists && (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || data == null))
            throw new IllegalStateException("Unreadable Mosaic projection authority");
        if (data == null)
            storage.set(MosaicStructureOverlayData.ID, new MosaicStructureOverlayData());
    }

    public static void publish(
            ServerLevel level, ChunkPos owner, long seed, List<CompoundTag> starts) {
        requireThread(level);
        var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        if (seed != runtime.resolveLocalWorldSeed(level.dimension(), owner))
            throw new IllegalStateException("Foreign projection local seed");
        data(level)
                .replace(
                        key(level, owner),
                        new MosaicStructureOverlayData.Projection(owner.x, owner.z, seed, starts));
    }

    public static MosaicStructureOverlayData.Projection projection(
            ServerLevel level, ChunkPos owner) {
        requireThread(level);
        var value = data(level).get(key(level, owner));
        if (value != null
                && (value.x() != owner.x
                        || value.z() != owner.z
                        || value.seed()
                                != MosaicWorldIdentity.runtimeContext(level)
                                        .orElseThrow()
                                        .resolveLocalWorldSeed(level.dimension(), owner)))
            throw new IllegalStateException("Stored projection provenance mismatch");
        return value;
    }

    private static MosaicStructureOverlayData data(ServerLevel level) {
        var value =
                level.getServer()
                        .overworld()
                        .getDataStorage()
                        .get(MosaicStructureOverlayData::load, MosaicStructureOverlayData.ID);
        if (value == null) throw new IllegalStateException("Projection store was not validated");
        return value;
    }

    private static String key(ServerLevel level, ChunkPos pos) {
        return level.dimension().location() + ":" + Long.toUnsignedString(pos.toLong());
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Off-thread physical projection access");
    }
}
