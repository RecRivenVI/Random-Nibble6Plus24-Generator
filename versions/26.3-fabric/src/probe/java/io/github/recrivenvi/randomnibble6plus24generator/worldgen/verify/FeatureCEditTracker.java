package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureContributionJournal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

/** Probe-only, world-instance-owned edit observations. No product save authority or tickets. */
public final class FeatureCEditTracker implements AutoCloseable {
    private static final Map<ServerLevel, Map<ChunkPos, FeatureCEditTracker>> ACTIVE =
            new ConcurrentHashMap<>();
    private final ServerLevel level;
    private final Ledger ledger;
    private Permit permit;
    private int observed, owned;

    private FeatureCEditTracker(ServerLevel level, Ledger ledger) {
        requireThread(level);
        this.level = level;
        this.ledger = ledger;
        if (ACTIVE.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                        .putIfAbsent(ledger.owner, this)
                != null) throw new IllegalStateException("Duplicate edit tracker");
    }

    static FeatureCEditTracker open(ServerLevel level, ChunkPos owner) {
        return new FeatureCEditTracker(level, new Ledger(world(level), level.getSeed(), owner));
    }

    static FeatureCEditTracker restore(ServerLevel level, ChunkPos owner, CompoundTag saved) {
        return new FeatureCEditTracker(
                level, Ledger.decode(saved, world(level), level.getSeed(), owner));
    }

    private static FeatureCEditTracker find(LevelChunk chunk) {
        var guards = ACTIVE.get(chunk.getLevel());
        return guards == null ? null : guards.get(chunk.getPos());
    }

    /** Claim at entry, before nested native updates can consume the one direct write permit. */
    public static boolean claim(LevelChunk chunk, BlockPos pos, BlockState state) {
        var guard = find(chunk);
        if (guard == null) return false;
        requireThread(guard.level);
        return guard.permit != null && guard.permit.claim(pos, state);
    }

    public static void changed(
            LevelChunk chunk, BlockPos pos, BlockState before, BlockState after, boolean owned) {
        var guard = find(chunk);
        if (guard == null || before == null || before.equals(after)) return;
        requireThread(guard.level);
        if (owned) guard.owned++;
        else {
            guard.ledger.record(pos);
            guard.observed++;
        }
    }

    /** Exempts only the exact direct contribution write, never its neighbour updates. */
    static boolean writeContribution(
            ServerLevel level, BlockPos pos, BlockState state, BooleanSupplier action) {
        var guards = ACTIVE.get(level);
        var guard = guards == null ? null : guards.get(ChunkPos.containing(pos));
        if (guard == null) return action.getAsBoolean();
        requireThread(level);
        if (guard.permit != null) throw new IllegalStateException("Nested C write permission");
        guard.permit = new Permit(pos, state);
        try {
            return action.getAsBoolean();
        } finally {
            guard.permit = null;
        }
    }

    void protect(FeatureContributionJournal journal) {
        requireThread(level);
        var chunk = level.getChunkSource().getChunkNow(ledger.owner.x(), ledger.owner.z());
        if (chunk == null)
            throw new IllegalStateException("Edit protection requires an already loaded receiver");
        for (var pos : ledger.positions)
            journal.protect(FeatureCPhysicalBatch.read(chunk, pos, level));
    }

    CompoundTag encode() {
        requireThread(level);
        return ledger.encode();
    }

    boolean protects(BlockPos pos) {
        return ledger.positions.contains(pos);
    }

    void protectPosition(BlockPos pos) {
        requireThread(level);
        ledger.record(pos);
    }

    int observed() {
        return observed;
    }

    int owned() {
        return owned;
    }

    public static int bindingCount() {
        return ACTIVE.values().stream().mapToInt(Map::size).sum();
    }

    @Override
    public void close() {
        requireThread(level);
        var guards = ACTIVE.get(level);
        if (guards != null) {
            guards.remove(ledger.owner, this);
            if (guards.isEmpty()) ACTIVE.remove(level, guards);
        }
        permit = null;
    }

    private static void requireThread(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("Edit tracker requires server thread");
    }

    private static String world(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                + "|"
                + level.dimension().identifier();
    }

    static final class Permit {
        private final BlockPos pos;
        private final BlockState state;
        private boolean claimed;

        Permit(BlockPos pos, BlockState state) {
            this.pos = pos.immutable();
            this.state = state;
        }

        boolean claim(BlockPos candidate, BlockState candidateState) {
            if (claimed || !pos.equals(candidate) || !state.equals(candidateState)) return false;
            claimed = true;
            return true;
        }
    }

    /** Only records that a position changed, not a replacement source of block/entity data. */
    static final class Ledger {
        private final String world;
        private final long seed;
        private final ChunkPos owner;
        private final Set<BlockPos> positions =
                new TreeSet<>(Comparator.comparingLong(BlockPos::asLong));

        Ledger(String world, long seed, ChunkPos owner) {
            this.world = Objects.requireNonNull(world);
            this.seed = seed;
            this.owner = owner;
        }

        void record(BlockPos pos) {
            if (!ChunkPos.containing(pos).equals(owner))
                throw new IllegalArgumentException("Foreign edited position");
            positions.add(pos.immutable());
        }

        CompoundTag encode() {
            var tag = new CompoundTag();
            tag.putInt("schema", 1);
            tag.putString("world", world);
            tag.putLong("seed", seed);
            tag.putInt("x", owner.x());
            tag.putInt("z", owner.z());
            tag.putLongArray("positions", positions.stream().mapToLong(BlockPos::asLong).toArray());
            return tag;
        }

        static Ledger decode(CompoundTag tag, String world, long seed, ChunkPos owner) {
            if (tag.getInt("schema").orElseThrow() != 1
                    || !tag.getString("world").orElseThrow().equals(world)
                    || tag.getLong("seed").orElseThrow() != seed
                    || tag.getInt("x").orElseThrow() != owner.x()
                    || tag.getInt("z").orElseThrow() != owner.z())
                throw new IllegalArgumentException("Foreign edit record");
            var result = new Ledger(world, seed, owner);
            for (long packed : tag.getLongArray("positions").orElseThrow())
                result.record(BlockPos.of(packed));
            if (!result.encode().equals(tag))
                throw new IllegalArgumentException("Noncanonical edit record");
            return result;
        }
    }
}
