package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.SavedTick;

/** Detached C experiment data. Not an Artifact, a world format or a physical write capability. */
public record TreeContribution(
        Key key,
        int minY,
        int height,
        List<Cell> cells,
        List<SavedTick<Block>> blockTicks,
        List<SavedTick<Fluid>> fluidTicks,
        List<BlockPos> postProcessing) {
    public record Scope(String world, String dimension, String contract) {
        public Scope {
            if (world.isBlank() || dimension.isBlank() || contract.isBlank())
                throw new IllegalArgumentException("Missing C provenance");
        }

        public static final Codec<Scope> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Codec.STRING
                                                        .fieldOf("world")
                                                        .forGetter(Scope::world),
                                                Codec.STRING
                                                        .fieldOf("dimension")
                                                        .forGetter(Scope::dimension),
                                                Codec.STRING
                                                        .fieldOf("contract")
                                                        .forGetter(Scope::contract))
                                        .apply(i, Scope::new));
    }

    public record Key(
            Scope scope, int x, int z, long seed, String feature, int step, int index, int attempt)
            implements Comparable<Key> {
        public Key {
            Objects.requireNonNull(scope);
            if (feature.isBlank() || step < 0 || index < 0 || attempt < 0)
                throw new IllegalArgumentException("Invalid tree contribution key");
        }

        public ChunkPos source() {
            return new ChunkPos(x, z);
        }

        @Override
        public int compareTo(Key other) {
            return Comparator.comparing((Key k) -> k.scope.world)
                    .thenComparing(k -> k.scope.dimension)
                    .thenComparing(k -> k.scope.contract)
                    .thenComparingInt(Key::z)
                    .thenComparingInt(Key::x)
                    .thenComparingLong(Key::seed)
                    .thenComparingInt(Key::step)
                    .thenComparingInt(Key::index)
                    .thenComparingInt(Key::attempt)
                    .thenComparing(Key::feature)
                    .compare(this, other);
        }

        public static final Codec<Key> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                Scope.CODEC.fieldOf("scope").forGetter(Key::scope),
                                                Codec.INT.fieldOf("x").forGetter(Key::x),
                                                Codec.INT.fieldOf("z").forGetter(Key::z),
                                                Codec.LONG.fieldOf("seed").forGetter(Key::seed),
                                                Codec.STRING
                                                        .fieldOf("feature")
                                                        .forGetter(Key::feature),
                                                Codec.INT.fieldOf("step").forGetter(Key::step),
                                                Codec.INT.fieldOf("index").forGetter(Key::index),
                                                Codec.INT
                                                        .fieldOf("attempt")
                                                        .forGetter(Key::attempt))
                                        .apply(i, Key::new));
    }

    public record Cell(BlockPos pos, BlockState state, Optional<CompoundTag> blockEntity) {
        public Cell {
            pos = pos.immutable();
            Objects.requireNonNull(state);
            blockEntity = blockEntity.map(CompoundTag::copy);
            if (blockEntity.isPresent()) {
                var tag = blockEntity.get();
                if (!state.hasBlockEntity()
                        || tag.getInt("x").orElseThrow() != pos.getX()
                        || tag.getInt("y").orElseThrow() != pos.getY()
                        || tag.getInt("z").orElseThrow() != pos.getZ())
                    throw new IllegalArgumentException("Detached BE state/position mismatch");
            }
        }

        @Override
        public Optional<CompoundTag> blockEntity() {
            return blockEntity.map(CompoundTag::copy);
        }

        public static final Codec<Cell> CODEC =
                RecordCodecBuilder.create(
                        i ->
                                i.group(
                                                BlockPos.CODEC.fieldOf("pos").forGetter(Cell::pos),
                                                BlockState.CODEC
                                                        .fieldOf("state")
                                                        .forGetter(Cell::state),
                                                CompoundTag.CODEC
                                                        .optionalFieldOf("block_entity")
                                                        .forGetter(Cell::blockEntity))
                                        .apply(i, Cell::new));
    }

    public TreeContribution {
        Objects.requireNonNull(key);
        if (height <= 0 || height > 4096 || (long) minY + height > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid contribution height");
        cells = List.copyOf(cells);
        blockTicks = immutableTicks(blockTicks);
        fluidTicks = immutableTicks(fluidTicks);
        postProcessing = postProcessing.stream().map(BlockPos::immutable).toList();
        var positions = new HashSet<BlockPos>();
        for (var cell : cells) {
            var p = cell.pos();
            frontier(key, minY, height, p);
            if (!positions.add(p)) throw new IllegalArgumentException("Duplicate C cell " + p);
            if (!supportedState(cell.state()))
                throw new IllegalArgumentException("Unaudited C output " + cell.state());
            if (cell.state().hasBlockEntity()
                    && (cell.blockEntity().isEmpty()
                            || !cell.blockEntity()
                                    .get()
                                    .getString("id")
                                    .orElseThrow()
                                    .equals("minecraft:beehive")))
                throw new IllegalArgumentException("Missing/unknown C hive NBT");
        }
        // Native edge shape updates also schedule ticks without a block write. Keep their raw
        // evidence, but receiving grants authority only to side effects of accepted cells.
        for (var tick : blockTicks) frontier(key, minY, height, tick.pos());
        for (var tick : fluidTicks) frontier(key, minY, height, tick.pos());
        for (var p : postProcessing) frontier(key, minY, height, p);
    }

    static boolean supportedState(BlockState state) {
        return Set.of(
                        Blocks.OAK_LOG,
                        Blocks.BIRCH_LOG,
                        Blocks.SPRUCE_LOG,
                        Blocks.OAK_LEAVES,
                        Blocks.BIRCH_LEAVES,
                        Blocks.SPRUCE_LEAVES,
                        Blocks.BEE_NEST,
                        Blocks.BROWN_MUSHROOM,
                        Blocks.RED_MUSHROOM,
                        Blocks.DIRT)
                .contains(state.getBlock());
    }

    private static void frontier(Key key, int minY, int height, BlockPos p) {
        var receiver = ChunkPos.containing(p);
        if (receiver.equals(key.source())
                || Math.abs((long) receiver.x() - key.x()) > 1
                || Math.abs((long) receiver.z() - key.z()) > 1
                || p.getY() < minY
                || p.getY() >= (long) minY + height)
            throw new IllegalArgumentException(
                    "Outside C source " + key.source() + " frontier " + p);
    }

    public long unattachedEffects() {
        var positions = new HashSet<BlockPos>();
        cells.forEach(c -> positions.add(c.pos()));
        return blockTicks.stream().filter(t -> !positions.contains(t.pos())).count()
                + fluidTicks.stream().filter(t -> !positions.contains(t.pos())).count()
                + postProcessing.stream().filter(p -> !positions.contains(p)).count();
    }

    private static <T> List<SavedTick<T>> immutableTicks(List<SavedTick<T>> ticks) {
        return ticks.stream()
                .map(t -> new SavedTick<>(t.type(), t.pos().immutable(), t.delay(), t.priority()))
                .toList();
    }

    public static final Codec<TreeContribution> CODEC =
            RecordCodecBuilder.create(
                    i ->
                            i.group(
                                            Key.CODEC
                                                    .fieldOf("key")
                                                    .forGetter(TreeContribution::key),
                                            Codec.INT
                                                    .fieldOf("min_y")
                                                    .forGetter(TreeContribution::minY),
                                            Codec.INT
                                                    .fieldOf("height")
                                                    .forGetter(TreeContribution::height),
                                            Cell.CODEC
                                                    .listOf()
                                                    .fieldOf("cells")
                                                    .forGetter(TreeContribution::cells),
                                            SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec())
                                                    .listOf()
                                                    .fieldOf("block_ticks")
                                                    .forGetter(TreeContribution::blockTicks),
                                            SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec())
                                                    .listOf()
                                                    .fieldOf("fluid_ticks")
                                                    .forGetter(TreeContribution::fluidTicks),
                                            BlockPos.CODEC
                                                    .listOf()
                                                    .fieldOf("post")
                                                    .forGetter(TreeContribution::postProcessing))
                                    .apply(i, TreeContribution::new));

    public CompoundTag encode() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    public static TreeContribution decode(CompoundTag tag) {
        var value = CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        // Reject silently defaulted block properties, unknown fields and partial recovery.
        if (!value.encode().equals(tag)) throw new IllegalArgumentException("Noncanonical C data");
        return value;
    }
}
