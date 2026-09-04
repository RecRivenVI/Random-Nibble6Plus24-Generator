package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;

/** Read/write facade for the persisted, owner-scoped Mosaic structure index. */
public final class MosaicStructureIndexStore {

    private static final Comparator<MosaicStructureIndexData.IndexEntry> TIE_BREAK = Comparator
            .comparing(MosaicStructureIndexData.IndexEntry::structure)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::ownerZ)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::ownerX)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::anchorZ)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::anchorX)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::anchorY)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::startZ)
            .thenComparingInt(MosaicStructureIndexData.IndexEntry::startX);

    private MosaicStructureIndexStore() {
    }

    /** Replaces every indexed structure projection for one physical owner. */
    public static synchronized void publish(
            ServerLevel level,
            ChunkPos owner,
            long localWorldSeed,
            List<StructureStart> ownerStarts,
            List<CompoundTag> externalStarts) {
        long expectedSeed = MosaicWorldIdentity.runtimeContext(level).orElseThrow()
                .resolveLocalWorldSeed(level.dimension(), owner);
        if (expectedSeed != localWorldSeed) {
            throw new IllegalStateException("Mosaic structure index seed mismatch for " + owner
                    + ": supplied=" + localWorldSeed + ", expected=" + expectedSeed);
        }
        List<StructureStart> starts = new ArrayList<>(ownerStarts);
        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(level);
        externalStarts.stream()
                .map(tag -> StructureStart.loadStaticStart(context, tag.copy(), localWorldSeed))
                .filter(start -> start != null && start.isValid())
                .forEach(starts::add);

        replaceOwner(level, owner, localWorldSeed, starts);
    }

    /** Indexes a saved physical LevelChunk without loading any additional Chunk. */
    public static synchronized void indexLoadedChunk(ServerLevel level, LevelChunk chunk) {
        ChunkPos owner = chunk.getPos();
        long localWorldSeed = MosaicWorldIdentity.runtimeContext(level).orElseThrow()
                .resolveLocalWorldSeed(level.dimension(), owner);
        List<StructureStart> starts = new ArrayList<>(chunk.getAllStarts().values());
        starts.addAll(MosaicStructureOverlayStore.externalStarts(level, owner));
        replaceOwner(level, owner, localWorldSeed, starts);
    }

    private static void replaceOwner(
            ServerLevel level, ChunkPos owner, long localWorldSeed, List<StructureStart> starts) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<MosaicStructureIndexData.IndexEntry> entries = starts.stream()
                .filter(start -> start != null && start.isValid())
                .map(start -> entry(level, registry, owner, localWorldSeed, start))
                .flatMap(Optional::stream)
                .toList();
        MosaicStructureIndexData data = level.getServer().getDataStorage()
                .computeIfAbsent(MosaicStructureIndexData.TYPE);
        data.replaceOwner(level.dimension().identifier().toString(), owner.x(), owner.z(), entries);
    }

    public static synchronized void remove(ServerLevel level, ChunkPos owner) {
        MosaicStructureIndexData data = level.getServer().getDataStorage()
                .get(MosaicStructureIndexData.TYPE);
        if (data != null) {
            data.removeOwner(level.dimension().identifier().toString(), owner.x(), owner.z());
        }
    }

    /**
     * Finds the nearest indexed physical projection without loading or
     * generating any Chunk.  The radius uses Vanilla's chunk-radius meaning.
     */
    public static Optional<Pair<net.minecraft.core.BlockPos, Holder<Structure>>> findNearest(
            ServerLevel level,
            HolderSet<Structure> requested,
            net.minecraft.core.BlockPos origin,
            int radius) {
        MosaicStructureIndexData data = level.getServer().getDataStorage()
                .get(MosaicStructureIndexData.TYPE);
        if (data == null || radius < 0) return Optional.empty();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ChunkPos originChunk = ChunkPos.containing(origin);
        long maxRadius = radius;
        List<Candidate> candidates = new ArrayList<>();
        for (MosaicStructureIndexData.IndexEntry entry : data.entries().values()) {
            if (!entry.dimension().equals(level.dimension().identifier().toString())) continue;
            long expectedSeed = MosaicWorldIdentity.runtimeContext(level).orElseThrow()
                    .resolveLocalWorldSeed(level.dimension(), new ChunkPos(entry.ownerX(), entry.ownerZ()));
            if (entry.localWorldSeed() != expectedSeed) {
                throw new IllegalStateException("Mosaic structure index seed provenance mismatch at "
                        + new ChunkPos(entry.ownerX(), entry.ownerZ())
                        + ": saved=" + entry.localWorldSeed() + ", expected=" + expectedSeed);
            }
            if (!new ChunkPos(Math.floorDiv(entry.anchorX(), 16), Math.floorDiv(entry.anchorZ(), 16))
                    .equals(new ChunkPos(entry.ownerX(), entry.ownerZ()))) {
                throw new IllegalStateException("Mosaic structure index anchor escaped its physical owner at "
                        + new ChunkPos(entry.ownerX(), entry.ownerZ()));
            }
            if (Math.abs((long) entry.ownerX() - originChunk.x()) > maxRadius
                    || Math.abs((long) entry.ownerZ() - originChunk.z()) > maxRadius) continue;
            Holder.Reference<Structure> holder;
            try {
                holder = registry.get(Identifier.parse(entry.structure())).orElse(null);
            } catch (RuntimeException ignored) {
                holder = null;
            }
            if (holder == null || !requested.contains(holder)) continue;
            long dx = (long) entry.anchorX() - origin.getX();
            long dz = (long) entry.anchorZ() - origin.getZ();
            candidates.add(new Candidate(entry, holder, dx * dx + dz * dz));
        }
        return candidates.stream()
                .sorted(Comparator.comparingLong(Candidate::distanceSquared)
                        .thenComparing(candidate -> candidate.entry(), TIE_BREAK))
                .findFirst()
                .map(candidate -> Pair.of(
                        new net.minecraft.core.BlockPos(
                                candidate.entry().anchorX(),
                                candidate.entry().anchorY(),
                                candidate.entry().anchorZ()),
                        candidate.holder()));
    }

    public static int indexedEntryCount(ServerLevel level) {
        MosaicStructureIndexData data = level.getServer().getDataStorage()
                .get(MosaicStructureIndexData.TYPE);
        if (data == null) return 0;
        String dimension = level.dimension().identifier().toString();
        return (int) data.entries().values().stream()
                .filter(entry -> entry.dimension().equals(dimension))
                .count();
    }

    private static Optional<MosaicStructureIndexData.IndexEntry> entry(
            ServerLevel level,
            Registry<Structure> registry,
            ChunkPos owner,
            long localWorldSeed,
            StructureStart start) {
        Identifier structureId = registry.getKey(start.getStructure());
        if (structureId == null) return Optional.empty();
        BoundingBox ownerBox = new BoundingBox(
                owner.getMinBlockX(), level.getMinY(), owner.getMinBlockZ(),
                owner.getMaxBlockX(), level.getMaxY() - 1, owner.getMaxBlockZ());
        return start.getPieces().stream()
                .filter(piece -> piece.getBoundingBox().intersects(ownerBox))
                .sorted(Comparator.comparing(StructurePiece::getBoundingBox, MosaicStructureIndexStore::compareBoxes))
                .findFirst()
                .map(piece -> {
                    BoundingBox box = piece.getBoundingBox();
                    int minX = Math.max(box.minX(), ownerBox.minX());
                    int minY = Math.max(box.minY(), ownerBox.minY());
                    int minZ = Math.max(box.minZ(), ownerBox.minZ());
                    int maxX = Math.min(box.maxX(), ownerBox.maxX());
                    int maxY = Math.min(box.maxY(), ownerBox.maxY());
                    int maxZ = Math.min(box.maxZ(), ownerBox.maxZ());
                    return new MosaicStructureIndexData.IndexEntry(
                            level.dimension().identifier().toString(),
                            structureId.toString(),
                            owner.x(), owner.z(),
                            start.getChunkPos().x(), start.getChunkPos().z(),
                            localWorldSeed,
                            (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                            box.minX(), box.minY(), box.minZ(),
                            box.maxX(), box.maxY(), box.maxZ());
                });
    }

    private static int compareBoxes(BoundingBox left, BoundingBox right) {
        int value = Integer.compare(left.minY(), right.minY());
        if (value != 0) return value;
        value = Integer.compare(left.minZ(), right.minZ());
        if (value != 0) return value;
        value = Integer.compare(left.minX(), right.minX());
        if (value != 0) return value;
        value = Integer.compare(left.maxY(), right.maxY());
        if (value != 0) return value;
        value = Integer.compare(left.maxZ(), right.maxZ());
        if (value != 0) return value;
        return Integer.compare(left.maxX(), right.maxX());
    }

    private record Candidate(
            MosaicStructureIndexData.IndexEntry entry,
            Holder.Reference<Structure> holder,
            long distanceSquared) {
    }
}
