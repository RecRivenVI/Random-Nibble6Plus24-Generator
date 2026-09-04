package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;

/**
 * Runtime structure view for one already-loaded physical Mosaic Chunk.
 *
 * <p>Vanilla's {@code StructureManager} follows structure-reference entries
 * into other Chunks. That is correct for one ordinary world seed, but would
 * merge independent Mosaic universes at a physical boundary. This view only
 * observes starts and pieces stored by the queried physical Chunk itself; it
 * never loads a Chunk and therefore cannot silently import another local seed.
 */
public final class MosaicStructureOverlay {

    private MosaicStructureOverlay() {
    }

    public static boolean active(LevelAccessor level) {
        return level instanceof ServerLevel serverLevel
                && MosaicPhysicalMaterializer.isPhysicalMosaic(serverLevel);
    }

    public static List<StructureStart> startsForStructure(
            LevelAccessor level,
            ChunkPos chunkPos,
            Predicate<Structure> matcher) {
        ServerLevel server = server(level).orElseThrow();
        LevelChunk chunk = loadedChunk(server, chunkPos);
        if (chunk == null) return List.of();
        List<StructureStart> result = new ArrayList<>();
        for (StructureStart start : startsForLoadedOwner(server, chunkPos)) {
            if (matcher.test(start.getStructure())) result.add(start);
        }
        trace("startsForStructure", server, chunkPos);
        return List.copyOf(result);
    }

    public static List<StructureStart> startsForStructure(
            LevelAccessor level,
            SectionPos sectionPos,
            Structure structure) {
        ServerLevel server = server(level).orElseThrow();
        ChunkPos chunkPos = sectionPos.chunk();
        LevelChunk chunk = loadedChunk(server, chunkPos);
        if (chunk == null) return List.of();
        StructureStart start = startsForLoadedOwner(server, chunkPos).stream()
                .filter(candidate -> candidate.getStructure() == structure)
                .findFirst()
                .orElse(null);
        trace("startsForStructureSection", server, chunkPos);
        return start != null && start.isValid() ? List.of(start) : List.of();
    }

    public static StructureStart structureAt(LevelAccessor level, BlockPos blockPos, Structure structure) {
        ServerLevel server = server(level).orElseThrow();
        ChunkPos chunkPos = ChunkPos.containing(blockPos);
        LevelChunk chunk = loadedChunk(server, chunkPos);
        if (chunk == null) return StructureStart.INVALID_START;
        StructureStart start = startsForLoadedOwner(server, chunkPos).stream()
                .filter(candidate -> candidate.getStructure() == structure)
                .filter(candidate -> candidate.getBoundingBox().isInside(blockPos))
                .findFirst()
                .orElse(null);
        trace("getStructureAt", server, chunkPos);
        return start != null && start.isValid()
                ? start
                : StructureStart.INVALID_START;
    }

    public static StructureStart structureWithPieceAt(
            LevelAccessor level,
            BlockPos blockPos,
            Structure structure) {
        return structureWithPieceAt(level, blockPos, holder -> holder.value() == structure);
    }

    public static StructureStart structureWithPieceAt(
            LevelAccessor level,
            BlockPos blockPos,
            TagKey<Structure> tag) {
        return structureWithPieceAt(level, blockPos, holder -> holder.is(tag));
    }

    public static StructureStart structureWithPieceAt(
            LevelAccessor level,
            BlockPos blockPos,
            HolderSet<Structure> structures) {
        return structureWithPieceAt(level, blockPos, structures::contains);
    }

    public static StructureStart structureWithPieceAt(
            LevelAccessor level,
            BlockPos blockPos,
            Predicate<Holder<Structure>> matcher) {
        ServerLevel server = server(level).orElseThrow();
        ChunkPos chunkPos = ChunkPos.containing(blockPos);
        LevelChunk chunk = loadedChunk(server, chunkPos);
        if (chunk == null) return StructureStart.INVALID_START;
        Registry<Structure> registry = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (StructureStart start : startsForLoadedOwner(server, chunkPos)) {
            Holder<Structure> holder = registry.wrapAsHolder(start.getStructure());
            if (start != null && start.isValid() && matcher.test(holder) && hasPiece(start, blockPos)) {
                trace("getStructureWithPieceAt", server, chunkPos);
                return start;
            }
        }
        trace("getStructureWithPieceAt", server, chunkPos);
        return StructureStart.INVALID_START;
    }

    public static boolean hasAnyStructureAt(LevelAccessor level, BlockPos blockPos) {
        ServerLevel server = server(level).orElseThrow();
        ChunkPos chunkPos = ChunkPos.containing(blockPos);
        LevelChunk chunk = loadedChunk(server, chunkPos);
        if (chunk == null) return false;
        boolean result = startsForLoadedOwner(server, chunkPos).stream()
                .anyMatch(start -> start.isValid() && hasPiece(start, blockPos));
        trace("hasAnyStructureAt", server, chunkPos);
        return result;
    }

    /**
     * Returns only target-local starts. The reference set intentionally
     * contains the start's own ChunkPos so Vanilla callers such as
     * ChunkGenerator#getMobsAt can continue using fillStartsForStructure
     * without following physical-neighbor references.
     */
    public static Map<Structure, LongSet> allStructuresAt(LevelAccessor level, BlockPos blockPos) {
        ServerLevel server = server(level).orElseThrow();
        ChunkPos chunkPos = ChunkPos.containing(blockPos);
        LevelChunk chunk = loadedChunk(server, chunkPos);
        if (chunk == null) return Map.of();
        Map<Structure, LongSet> result = new LinkedHashMap<>();
        for (StructureStart start : startsForLoadedOwner(server, chunkPos)) {
            if (start.isValid()) {
                LongOpenHashSet references = new LongOpenHashSet();
                references.add(chunkPos.pack());
                result.computeIfAbsent(start.getStructure(), ignored -> references)
                        .add(chunkPos.pack());
            }
        }
        trace("getAllStructuresAt", server, chunkPos);
        return Map.copyOf(result);
    }

    /** Resolves only references produced by {@link #allStructuresAt}. */
    public static void fillStartsForStructure(
            LevelAccessor level,
            Structure structure,
            LongSet references,
            Consumer<StructureStart> consumer) {
        ServerLevel server = server(level).orElseThrow();
        for (long reference : references) {
            ChunkPos owner = ChunkPos.unpack(reference);
            if (loadedChunk(server, owner) == null) continue;
            startsForLoadedOwner(server, owner).stream()
                    .filter(start -> start.getStructure() == structure)
                    .filter(StructureStart::isValid)
                    .forEach(consumer);
            trace("fillStartsForStructure", server, owner);
        }
    }

    /** Unindexed remote structure checks stay closed; locate uses the persisted index instead. */
    public static StructureCheckResult refuseLocate(LevelAccessor level, ChunkPos chunkPos) {
        ServerLevel server = server(level).orElseThrow();
        trace("locateRefused", server, chunkPos);
        return StructureCheckResult.START_NOT_PRESENT;
    }

    private static boolean hasPiece(StructureStart start, BlockPos blockPos) {
        return start.getPieces().stream()
                .anyMatch(piece -> piece.getBoundingBox().isInside(blockPos));
    }

    private static Optional<ServerLevel> server(LevelAccessor level) {
        return level instanceof ServerLevel server && active(server)
                ? Optional.of(server)
                : Optional.empty();
    }

    private static LevelChunk loadedChunk(ServerLevel level, ChunkPos chunkPos) {
        return level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
    }

    private static List<StructureStart> startsForLoadedOwner(ServerLevel level, ChunkPos owner) {
        if (loadedChunk(level, owner) == null) return List.of();
        return MosaicStructureOverlayStore.startsForOwner(level, owner);
    }

    private static void trace(String operation, ServerLevel level, ChunkPos chunkPos) {
        MosaicStructureOverlayTrace.record(operation, level.dimension(), chunkPos);
    }
}
