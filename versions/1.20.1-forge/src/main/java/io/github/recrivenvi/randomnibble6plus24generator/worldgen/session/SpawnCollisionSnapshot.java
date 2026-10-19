package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.PalettedStateBitsAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;

/** Detached exact CARVERS block containers for population collision, never physical neighbours. */
public final class SpawnCollisionSnapshot {
    private final SpawnBiomeSnapshot provenance;
    private final Map<ChunkPos, View> sources;

    private SpawnCollisionSnapshot(SpawnBiomeSnapshot provenance, Map<ChunkPos, View> sources) {
        this.provenance = provenance;
        this.sources = Map.copyOf(sources);
    }

    public static SpawnCollisionSnapshot capture(
            SpawnBiomeSnapshot provenance, Function<ChunkPos, ? extends ChunkAccess> chunks) {
        Map<ChunkPos, View> copies = new HashMap<>();
        for (var position : provenance.positions()) {
            var source = Objects.requireNonNull(chunks.apply(position));
            if (!source.getPos().equals(position)
                    || source.getStatus() != ChunkStatus.CARVERS
                    || source.getMinBuildHeight() != provenance.minY(position)
                    || source.getHeight() != provenance.height(position)
                    || !source.getBlockEntitiesPos().isEmpty())
                throw new IllegalStateException("Unaudited same-seed population collision input");
            var sections =
                    java.util.Arrays.stream(source.getSections())
                            .map(
                                    section ->
                                            Section.capture(
                                                    section.getStates(), section.hasOnlyAir()))
                            .toList();
            copies.put(
                    position,
                    new View(position, source.getMinBuildHeight(), source.getHeight(), sections));
        }
        return new SpawnCollisionSnapshot(provenance, copies);
    }

    public void requireProvenance(SpawnBiomeSnapshot expected) {
        if (provenance != expected)
            throw new IllegalArgumentException(
                    "Population collision and biome inputs have different owners");
    }

    public BlockGetter view(ChunkPos position) {
        return Objects.requireNonNull(
                sources.get(position), "Outside population collision frontier " + position);
    }

    public int chunks() {
        return sources.size();
    }

    private record Section(boolean onlyAir, List<BlockState> palette, BitStorage indices) {
        static Section capture(PalettedContainer<BlockState> states, boolean onlyAir) {
            if (onlyAir)
                return new Section(
                        true, List.of(Blocks.AIR.defaultBlockState()), new ZeroBitStorage(4096));
            var strategy = PalettedContainer.Strategy.SECTION_STATES;
            var packed = states.pack(Block.BLOCK_STATE_REGISTRY, strategy);
            var palette = List.copyOf(packed.paletteEntries());
            int bits =
                    ((PalettedStateBitsAccess) strategy)
                            .mosaic$serializedBits(Block.BLOCK_STATE_REGISTRY, palette.size());
            BitStorage storage;
            if (bits == 0 && palette.size() == 1 && packed.storage().isEmpty())
                storage = new ZeroBitStorage(strategy.size());
            else
                storage =
                        new SimpleBitStorage(
                                bits, strategy.size(), packed.storage().orElseThrow().toArray());
            return new Section(false, palette, storage);
        }

        BlockState state(int x, int y, int z) {
            return palette.get(indices.get((y << 8) | (z << 4) | x));
        }
    }

    private record View(ChunkPos position, int minY, int height, List<Section> sections)
            implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (!new ChunkPos(pos).equals(position))
                throw new IllegalArgumentException("Collision read belongs to another source");
            if (pos.getY() < minY || pos.getY() >= minY + height)
                return Blocks.VOID_AIR.defaultBlockState();
            var section =
                    sections.get(
                            SectionPos.blockToSectionCoord(pos.getY())
                                    - SectionPos.blockToSectionCoord(minY));
            return section.onlyAir()
                    ? Blocks.AIR.defaultBlockState()
                    : section.state(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            // Capture rejects any source with BE data; no unrecorded state is fabricated.
            if (!new ChunkPos(pos).equals(position))
                throw new IllegalArgumentException(
                        "Collision BlockEntity read belongs to another source");
            return null;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getMinBuildHeight() {
            return minY;
        }
    }
}
