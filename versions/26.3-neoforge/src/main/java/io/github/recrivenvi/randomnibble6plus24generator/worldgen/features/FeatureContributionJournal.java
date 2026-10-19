package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;

/** C receiving experiment. Pure replay, no ServerLevel, chunk loading or physical mutation. */
public final class FeatureContributionJournal {
    private final TreeContribution.Scope scope;
    private final ChunkPos receiver;
    private final long receiverSeed;
    private final int minY, height;
    private final NavigableMap<TreeContribution.Key, TreeContribution> contributions =
            new TreeMap<>();
    private final Map<BlockPos, TreeContribution.Cell> base = new HashMap<>(),
            protectedEdits = new HashMap<>();

    public FeatureContributionJournal(
            TreeContribution.Scope scope,
            ChunkPos receiver,
            long receiverSeed,
            int minY,
            int height) {
        this.scope = Objects.requireNonNull(scope);
        this.receiver = Objects.requireNonNull(receiver);
        this.receiverSeed = receiverSeed;
        if (height <= 0 || height > 4096 || (long) minY + height > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid receiver height");
        this.minY = minY;
        this.height = height;
    }

    public synchronized boolean publish(TreeContribution contribution) {
        if (!scope.equals(contribution.key().scope())
                || contribution.minY() != minY
                || contribution.height() != height)
            throw new IllegalArgumentException("Foreign C world/dimension/contract");
        if (contribution.cells().stream().noneMatch(c -> owns(c.pos())))
            throw new IllegalArgumentException("No projection in this receiver");
        for (var key : contributions.keySet())
            if (key.source().equals(contribution.key().source())
                    && key.seed() != contribution.key().seed())
                throw new IllegalArgumentException("Conflicting source local seed");
        var old = contributions.get(contribution.key());
        if (old != null) {
            if (!old.equals(contribution))
                throw new IllegalStateException("Same contribution key with changed content");
            return false;
        }
        contributions.put(contribution.key(), contribution);
        return true;
    }

    /**
     * The caller supplies immutable receiver-native base data; an absent position stays pending.
     */
    public synchronized void supplyBase(Collection<TreeContribution.Cell> cells) {
        var additions = new HashMap<BlockPos, TreeContribution.Cell>();
        for (var cell : cells) {
            requireOwned(cell.pos());
            var old = base.get(cell.pos());
            var duplicate = additions.putIfAbsent(cell.pos(), cell);
            if (old != null && !old.equals(cell) || duplicate != null && !duplicate.equals(cell))
                throw new IllegalArgumentException("Receiver base cannot be replaced");
        }
        base.putAll(additions);
    }

    /** AIR is also a persistent edit: player-dug space must not be refilled by a late tree. */
    public synchronized void protect(TreeContribution.Cell edit) {
        requireOwned(edit.pos());
        protectedEdits.put(edit.pos(), edit);
    }

    public synchronized void remove(TreeContribution.Key key) {
        contributions.remove(key);
    }

    public record Projection(
            Map<BlockPos, TreeContribution.Cell> incoming,
            Map<BlockPos, TreeContribution.Cell> protectedEdits,
            List<SavedTick<Block>> blockTicks,
            List<SavedTick<Fluid>> fluidTicks,
            List<BlockPos> postProcessing,
            int pendingPositions,
            int rejectedPositions,
            int unattachedEffects) {}

    public synchronized Projection project() {
        Map<BlockPos, TreeContribution.Cell> selected = new HashMap<>();
        var blocks = new ArrayList<SavedTick<Block>>();
        var fluids = new ArrayList<SavedTick<Fluid>>();
        var post = new ArrayList<BlockPos>();
        Set<BlockPos> pending = new HashSet<>(), rejected = new HashSet<>();
        int unattached = 0;
        // First key wins each position; same source's native intra-attempt result is already
        // sealed.
        for (var contribution : contributions.values()) {
            var accepted = new HashSet<BlockPos>();
            var declared = new HashSet<BlockPos>();
            contribution.cells().forEach(c -> declared.add(c.pos()));
            unattached +=
                    (int)
                            contribution.blockTicks().stream()
                                    .filter(t -> owns(t.pos()) && !declared.contains(t.pos()))
                                    .count();
            unattached +=
                    (int)
                            contribution.fluidTicks().stream()
                                    .filter(t -> owns(t.pos()) && !declared.contains(t.pos()))
                                    .count();
            unattached +=
                    (int)
                            contribution.postProcessing().stream()
                                    .filter(p -> owns(p) && !declared.contains(p))
                                    .count();
            for (var cell : contribution.cells()) {
                var pos = cell.pos();
                if (!owns(pos)) continue;
                if (protectedEdits.containsKey(pos)) {
                    rejected.add(pos);
                    continue;
                }
                var original = base.get(pos);
                if (original == null) {
                    pending.add(pos);
                    continue;
                }
                if (!original.state().isAir()
                        || original.blockEntity().isPresent()
                        || selected.containsKey(pos)) {
                    rejected.add(pos);
                    continue;
                }
                selected.put(pos, cell);
                accepted.add(pos);
            }
            contribution.blockTicks().stream()
                    .filter(t -> accepted.contains(t.pos()))
                    .forEach(blocks::add);
            contribution.fluidTicks().stream()
                    .filter(t -> accepted.contains(t.pos()))
                    .forEach(fluids::add);
            contribution.postProcessing().stream().filter(accepted::contains).forEach(post::add);
        }
        return new Projection(
                Map.copyOf(selected),
                Map.copyOf(protectedEdits),
                List.copyOf(blocks),
                List.copyOf(fluids),
                List.copyOf(post),
                pending.size(),
                rejected.size(),
                unattached);
    }

    private boolean owns(BlockPos pos) {
        return ChunkPos.containing(pos).equals(receiver)
                && pos.getY() >= minY
                && pos.getY() < (long) minY + height;
    }

    private void requireOwned(BlockPos pos) {
        if (!owns(pos)) throw new IllegalArgumentException("Foreign receiver position " + pos);
    }

    /** An experimental standalone record, never stored in an existing Mosaic save. */
    public synchronized CompoundTag encode() {
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.put(
                "scope",
                TreeContribution.Scope.CODEC.encodeStart(NbtOps.INSTANCE, scope).getOrThrow());
        tag.putInt("x", receiver.x());
        tag.putInt("z", receiver.z());
        tag.putLong("seed", receiverSeed);
        tag.putInt("min_y", minY);
        tag.putInt("height", height);
        tag.put("base", encodeCells(base.values()));
        tag.put("edits", encodeCells(protectedEdits.values()));
        var entries = new ListTag();
        contributions.values().forEach(c -> entries.add(c.encode()));
        tag.put("contributions", entries);
        return tag;
    }

    private static ListTag encodeCells(Collection<TreeContribution.Cell> cells) {
        var tags = new ListTag();
        cells.stream()
                .sorted(Comparator.comparingLong(c -> c.pos().asLong()))
                .forEach(
                        c ->
                                tags.add(
                                        TreeContribution.Cell.CODEC
                                                .encodeStart(NbtOps.INSTANCE, c)
                                                .getOrThrow()));
        return tags;
    }

    public static FeatureContributionJournal decode(
            CompoundTag tag, TreeContribution.Scope scope, ChunkPos receiver, long seed) {
        if (tag.getInt("schema").orElseThrow() != 1
                || !TreeContribution.Scope.CODEC
                        .parse(NbtOps.INSTANCE, tag.getCompound("scope").orElseThrow())
                        .getOrThrow()
                        .equals(scope)
                || tag.getInt("x").orElseThrow() != receiver.x()
                || tag.getInt("z").orElseThrow() != receiver.z()
                || tag.getLong("seed").orElseThrow() != seed)
            throw new IllegalArgumentException("Foreign/corrupt C journal");
        var journal =
                new FeatureContributionJournal(
                        scope,
                        receiver,
                        seed,
                        tag.getInt("min_y").orElseThrow(),
                        tag.getInt("height").orElseThrow());
        journal.supplyBase(
                TreeContribution.Cell.CODEC
                        .listOf()
                        .parse(NbtOps.INSTANCE, tag.getList("base").orElseThrow())
                        .getOrThrow());
        TreeContribution.Cell.CODEC
                .listOf()
                .parse(NbtOps.INSTANCE, tag.getList("edits").orElseThrow())
                .getOrThrow()
                .forEach(journal::protect);
        for (var entry : tag.getList("contributions").orElseThrow())
            if (!journal.publish(TreeContribution.decode((CompoundTag) entry)))
                throw new IllegalArgumentException("Duplicate persisted contribution");
        if (!journal.encode().equals(tag))
            throw new IllegalArgumentException("Noncanonical C journal");
        return journal;
    }
}
