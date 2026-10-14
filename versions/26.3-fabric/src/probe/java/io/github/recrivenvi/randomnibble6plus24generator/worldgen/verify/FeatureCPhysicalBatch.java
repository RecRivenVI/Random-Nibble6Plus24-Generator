package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.*;

/** Probe-only sealed batch. Does not implement late conflict replacement or crash recovery. */
final class FeatureCPhysicalBatch {
    private final CompoundTag sealed;
    private final ChunkPos receiver;
    private final String world;
    private final long seed;
    private boolean applied;

    FeatureCPhysicalBatch(
            ServerLevel level, ChunkPos receiver, FeatureContributionJournal journal) {
        requireThread(level);
        this.sealed = journal.encode();
        this.receiver = receiver;
        this.world = world(level);
        this.seed = level.getSeed();
    }

    private FeatureCPhysicalBatch(ServerLevel level, CompoundTag saved) {
        requireThread(level);
        if (saved.getInt("schema").orElseThrow() != 1
                || !saved.getString("world").orElseThrow().equals(world(level))
                || saved.getLong("seed").orElseThrow() != level.getSeed()
                || !saved.getBoolean("applied").orElseThrow())
            throw new IllegalArgumentException("Foreign/incomplete physical test receipt");
        sealed = saved.getCompound("journal").orElseThrow().copy();
        receiver = new ChunkPos(sealed.getInt("x").orElseThrow(), sealed.getInt("z").orElseThrow());
        world = world(level);
        seed = level.getSeed();
        applied = true;
    }

    static FeatureCPhysicalBatch restore(ServerLevel level, CompoundTag tag) {
        return new FeatureCPhysicalBatch(level, tag);
    }

    /** -1 means not loaded; zero means already applied. Neither case acquires a chunk ticket. */
    int apply(ServerLevel level, FeatureContributionJournal journal) {
        requireThread(level);
        if (!world.equals(world(level))
                || seed != level.getSeed()
                || !sealed.equals(journal.encode()))
            throw new IllegalStateException(
                    "Changed physical batch: runtime replacement not proven");
        if (applied) return 0;
        var projection = journal.project();
        if (projection.pendingPositions() != 0 || !projection.postProcessing().isEmpty())
            throw new IllegalStateException("Unresolved base or unaudited post-FULL processing");
        // Native setBlock may inspect immediate neighbours; test preparation owns these tickets.
        for (int z = -1; z <= 1; z++)
            for (int x = -1; x <= 1; x++)
                if (level.getChunkSource().getChunkNow(receiver.x() + x, receiver.z() + z) == null)
                    return -1;
        LevelChunk chunk = level.getChunkSource().getChunkNow(receiver.x(), receiver.z());
        var cells = new ArrayList<>(projection.incoming().values());
        cells.sort(Comparator.comparingLong(c -> c.pos().asLong()));
        for (var cell : cells) {
            if (!ChunkPos.containing(cell.pos()).equals(receiver)
                    || !level.isInWorldBounds(cell.pos())
                    || !chunk.getBlockState(cell.pos()).isAir()
                    || chunk.getBlockEntity(cell.pos()) != null)
                throw new IllegalStateException(
                        "Physical base changed before batch: " + cell.pos());
            if (cell.blockEntity().isPresent()) {
                BlockEntity checked =
                        BlockEntity.loadStatic(
                                cell.pos(),
                                cell.state(),
                                cell.blockEntity().orElseThrow(),
                                level.registryAccess());
                if (checked == null) throw new IllegalStateException("Invalid C block entity");
            }
        }
        for (var cell : cells) {
            if (!FeatureCEditTracker.writeContribution(
                    level,
                    cell.pos(),
                    cell.state(),
                    () -> level.setBlock(cell.pos(), cell.state(), Block.UPDATE_ALL)))
                throw new IllegalStateException("Physical setBlock rejected " + cell.pos());
            if (cell.blockEntity().isPresent()) {
                var blockEntity = chunk.getBlockEntity(cell.pos());
                if (blockEntity == null) throw new IllegalStateException("Missing physical BE");
                var problems = new ProblemReporter.Collector();
                blockEntity.loadWithComponents(
                        TagValueInput.create(
                                problems,
                                level.registryAccess(),
                                cell.blockEntity().orElseThrow()));
                if (!problems.isEmpty()) throw new IllegalStateException(problems.getReport());
                blockEntity.setChanged();
            }
        }
        projection
                .blockTicks()
                .forEach(
                        t ->
                                level.getBlockTicks()
                                        .schedule(
                                                t.unpack(
                                                        level.getGameTime(),
                                                        level.nextSubTickCount())));
        projection
                .fluidTicks()
                .forEach(
                        t ->
                                level.getFluidTicks()
                                        .schedule(
                                                t.unpack(
                                                        level.getGameTime(),
                                                        level.nextSubTickCount())));
        applied = true;
        return cells.size();
    }

    CompoundTag receipt() {
        if (!applied)
            throw new IllegalStateException("Cannot persist an unapplied batch as complete");
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putString("world", world);
        tag.putLong("seed", seed);
        tag.putBoolean("applied", true);
        tag.put("journal", sealed.copy());
        return tag;
    }

    static TreeContribution.Cell read(LevelChunk chunk, BlockPos pos, ServerLevel level) {
        return new TreeContribution.Cell(
                pos,
                chunk.getBlockState(pos),
                Optional.ofNullable(chunk.getBlockEntityNbtForSaving(pos, level.registryAccess())));
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Physical C receiving requires the server thread");
    }

    private static String world(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                + "|"
                + level.dimension().identifier();
    }
}
