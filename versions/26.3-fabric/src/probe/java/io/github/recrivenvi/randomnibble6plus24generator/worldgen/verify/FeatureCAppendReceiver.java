package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Probe-only monotone physical reception. Replacement/deletion is explicitly unresolved. */
final class FeatureCAppendReceiver implements AutoCloseable {
    enum Status {
        QUEUED,
        READY,
        WAITING_LOADED,
        WAITING_BASE,
        PENDING_CONFLICT,
        UNSUPPORTED_POST_PROCESSING
    }

    record Flush(Status status, int writes, List<BlockPos> conflicts) {}

    private final ServerLevel level;
    private final ChunkPos owner;
    private final TreeContribution.Scope scope;
    private final long seed;
    private final String world;
    private final FeatureCEditTracker guard;
    private final FeatureContributionJournal pending;
    private CompoundTag applied;
    private Status status = Status.QUEUED;
    private int batches, writes;
    private boolean closed, poisoned;

    static FeatureCAppendReceiver open(
            ServerLevel level, ChunkPos owner, TreeContribution.Scope scope) {
        requireThread(level);
        return new FeatureCAppendReceiver(level, owner, scope, null);
    }

    static FeatureCAppendReceiver restore(
            ServerLevel level, ChunkPos owner, TreeContribution.Scope scope, CompoundTag record) {
        requireThread(level);
        return new FeatureCAppendReceiver(level, owner, scope, record);
    }

    private FeatureCAppendReceiver(
            ServerLevel level, ChunkPos owner, TreeContribution.Scope scope, CompoundTag record) {
        this.level = level;
        this.owner = owner;
        this.scope = scope;
        seed = level.getSeed();
        world = world(level);
        if (record == null) {
            pending =
                    new FeatureContributionJournal(
                            scope, owner, seed, level.getMinY(), level.getHeight());
            applied = pending.encode();
            guard = FeatureCEditTracker.open(level, owner);
        } else {
            if (!record.keySet()
                            .equals(
                                    Set.of(
                                            "schema", "world", "seed", "pending", "applied",
                                            "guard", "batches", "writes", "status"))
                    || record.getInt("schema").orElseThrow() != 1
                    || !record.getString("world").orElseThrow().equals(world)
                    || record.getLong("seed").orElseThrow() != seed)
                throw new IllegalArgumentException("Foreign/corrupt append receipt");
            pending = decode(record.getCompound("pending").orElseThrow());
            applied = decode(record.getCompound("applied").orElseThrow()).encode();
            if (pending.encode().getInt("min_y").orElseThrow() != level.getMinY()
                    || pending.encode().getInt("height").orElseThrow() != level.getHeight()
                    || applied.getInt("min_y").orElseThrow() != level.getMinY()
                    || applied.getInt("height").orElseThrow() != level.getHeight())
                throw new IllegalArgumentException("Foreign receiver height");
            for (String key : List.of("contributions", "base"))
                for (var item : applied.getList(key).orElseThrow())
                    if (!pending.encode().getList(key).orElseThrow().contains(item))
                        throw new IllegalArgumentException(
                                "Applied input absent/changed in pending journal");
            batches = record.getInt("batches").orElseThrow();
            writes = record.getInt("writes").orElseThrow();
            if (batches < 0 || writes < 0 || batches > writes)
                throw new IllegalArgumentException("Invalid append counters");
            status = Status.valueOf(record.getString("status").orElseThrow());
            guard =
                    FeatureCEditTracker.restore(
                            level, owner, record.getCompound("guard").orElseThrow());
        }
    }

    /** Pure publication may run on a worker; it never reads physical state or acquires tickets. */
    synchronized boolean offer(TreeContribution contribution) {
        requireOpen();
        boolean added = pending.publish(contribution);
        if (added) status = Status.QUEUED;
        return added;
    }

    Flush flush() {
        requireThread(level);
        CompoundTag start;
        synchronized (this) {
            requireOpen();
            start = pending.encode();
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(owner.x(), owner.z());
        if (chunk == null) return result(Status.WAITING_LOADED, 0, List.of());
        for (int z = -1; z <= 1; z++)
            for (int x = -1; x <= 1; x++)
                if (level.getChunkSource().getChunkNow(owner.x() + x, owner.z() + z) == null)
                    return result(Status.WAITING_LOADED, 0, List.of());
        var known = new HashSet<BlockPos>();
        for (var item : start.getList("base").orElseThrow())
            known.add(TreeContribution.Cell.CODEC.parse(NbtOps.INSTANCE, item).getOrThrow().pos());
        var missing = new TreeSet<BlockPos>(Comparator.comparingLong(BlockPos::asLong));
        for (var item : start.getList("contributions").orElseThrow())
            TreeContribution.decode((CompoundTag) item).cells().stream()
                    .map(TreeContribution.Cell::pos)
                    .filter(p -> ChunkPos.containing(p).equals(owner) && !known.contains(p))
                    .forEach(missing::add);
        var base = missing.stream().map(p -> FeatureCPhysicalBatch.read(chunk, p, level)).toList();
        FeatureContributionJournal desired;
        synchronized (this) {
            pending.supplyBase(base);
            desired = decode(pending.encode());
        }
        var previous = decode(applied).project();
        for (var cell : previous.incoming().values())
            if (!matches(chunk, cell)) guard.protectPosition(cell.pos());
        guard.protect(desired);
        var projection = desired.project();
        if (projection.pendingPositions() != 0) return result(Status.WAITING_BASE, 0, List.of());
        var plan = FeatureCAppendPlan.between(previous, projection);
        if (!plan.conflicts().isEmpty())
            return result(Status.PENDING_CONFLICT, 0, plan.conflicts());
        if (!projection.postProcessing().isEmpty())
            return result(Status.UNSUPPORTED_POST_PROCESSING, 0, List.of());
        int count = 0;
        if (!plan.additions().isEmpty()) {
            var delta = decode(desired.encode());
            previous.incoming()
                    .keySet()
                    .forEach(p -> delta.protect(FeatureCPhysicalBatch.read(chunk, p, level)));
            if (!delta.project().incoming().keySet().equals(plan.additions()))
                throw new IllegalStateException("Delta authority differs");
            try {
                count = new FeatureCPhysicalBatch(level, owner, delta).apply(level, delta);
                if (count != plan.additions().size())
                    throw new IllegalStateException("Incomplete physical append");
            } catch (RuntimeException | Error failure) {
                synchronized (this) {
                    poisoned = true;
                }
                throw failure;
            }
        }
        synchronized (this) {
            applied = desired.encode();
            if (count > 0) batches++;
            writes += count;
            // Publication during physical application remains queued for the next flush.
            status = sameInputs(pending.encode(), desired.encode()) ? Status.READY : Status.QUEUED;
            return new Flush(status, count, List.of());
        }
    }

    private boolean matches(LevelChunk chunk, TreeContribution.Cell cell) {
        if (!chunk.getBlockState(cell.pos()).equals(cell.state())) return false;
        var be = chunk.getBlockEntity(cell.pos());
        if (cell.blockEntity().isEmpty()) return be == null;
        return be != null
                && be.saveWithFullMetadata(level.registryAccess())
                        .equals(cell.blockEntity().orElseThrow());
    }

    static boolean sameInputs(CompoundTag left, CompoundTag right) {
        return left.getList("contributions")
                        .orElseThrow()
                        .equals(right.getList("contributions").orElseThrow())
                && left.getList("base").orElseThrow().equals(right.getList("base").orElseThrow());
    }

    synchronized CompoundTag receipt() {
        requireThread(level);
        requireOpen();
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putString("world", world);
        tag.putLong("seed", seed);
        tag.put("pending", pending.encode());
        tag.put("applied", applied.copy());
        tag.put("guard", guard.encode());
        tag.putInt("batches", batches);
        tag.putInt("writes", writes);
        tag.putString("status", status.name());
        return tag;
    }

    synchronized boolean protects(BlockPos pos) {
        return guard.protects(pos);
    }

    synchronized int batches() {
        return batches;
    }

    synchronized int writes() {
        return writes;
    }

    synchronized int ownedWrites() {
        return guard.owned();
    }

    private synchronized Flush result(Status state, int count, List<BlockPos> conflicts) {
        status = state;
        return new Flush(state, count, conflicts);
    }

    private FeatureContributionJournal decode(CompoundTag tag) {
        return FeatureContributionJournal.decode(tag, scope, owner, seed);
    }

    private void requireOpen() {
        if (closed || poisoned) throw new IllegalStateException("Closed/failed C append receiver");
    }

    @Override
    public synchronized void close() {
        requireThread(level);
        if (!closed) {
            closed = true;
            guard.close();
        }
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("C append requires server thread");
    }

    private static String world(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                + "|"
                + level.dimension().identifier();
    }
}
