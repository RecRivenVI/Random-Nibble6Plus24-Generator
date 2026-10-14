package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;

/** FEATURES-only neighbor view: real same-seed terrain reads, no neighbor write authority. */
final class FeatureReadOnlyChunk extends ChunkAccess {
    private final ChunkAccess source;
    private final Runnable discarded;
    private final TickContainerAccess<Block> blockTicks;
    private final TickContainerAccess<Fluid> fluidTicks;
    private LevelChunkSection[] readSections;

    FeatureReadOnlyChunk(ChunkAccess source, PalettedContainerFactory factory, Runnable discarded) {
        super(
                source.getPos(),
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(source.getMinY(), 0),
                factory,
                0L,
                null,
                null);
        this.source = source;
        this.discarded = discarded;
        this.blockTicks = readTicks(source.getBlockTicks());
        this.fluidTicks = readTicks(source.getFluidTicks());
    }

    @Override
    public int getMinY() {
        return source.getMinY();
    }

    @Override
    public int getHeight() {
        return source.getHeight();
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return source.getPersistedStatus();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return source.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return source.getFluidState(pos);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return source.getNoiseBiome(x, y, z);
    }

    @Override
    public void collectBiomesInPalette(Set<Holder<Biome>> biomes) {
        source.collectBiomesInPalette(biomes);
    }

    @Override
    public LevelChunkSection[] getSections() {
        // Direct section users must not acquire the mutable source palettes either.
        if (readSections == null) {
            LevelChunkSection[] sections = source.getSections();
            readSections = new LevelChunkSection[sections.length];
            for (int i = 0; i < sections.length; i++)
                readSections[i] = new ReadOnlySection(sections[i], discarded);
        }
        return readSections;
    }

    @Override
    public StructureStart getStartForStructure(Structure structure) {
        return source.getStartForStructure(structure);
    }

    @Override
    public Map<Structure, StructureStart> getAllStarts() {
        return source.getAllStarts();
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return source.getReferencesForStructure(structure);
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        return source.getAllReferences();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return source.getBlockEntity(pos);
    }

    @Override
    public CompoundTag getBlockEntityNbt(BlockPos pos) {
        CompoundTag tag = source.getBlockEntityNbt(pos);
        return tag == null ? null : tag.copy();
    }

    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries) {
        CompoundTag tag = source.getBlockEntityNbtForSaving(pos, registries);
        return tag == null ? null : tag.copy();
    }

    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
        discarded.run();
        return null;
    }

    @Override
    public void setBlockEntity(BlockEntity entity) {
        discarded.run();
    }

    @Override
    public void setBlockEntityNbt(CompoundTag tag) {
        discarded.run();
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        discarded.run();
    }

    @Override
    public void addEntity(Entity entity) {
        discarded.run();
    }

    @Override
    public void markPosForPostProcessing(BlockPos pos) {
        discarded.run();
    }

    @Override
    public void addPackedPostProcess(ShortList offsets, int section) {
        discarded.run();
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return blockTicks;
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return fluidTicks;
    }

    @Override
    public PackedTicks getTicksForSerialization(long time) {
        return source.getTicksForSerialization(time);
    }

    @Override
    public boolean canBeSerialized() {
        return false;
    }

    private static final class ReadOnlySection extends LevelChunkSection {
        private final Runnable discarded;

        private ReadOnlySection(LevelChunkSection source, Runnable discarded) {
            super(source.getStates().copy(), source.getBiomes().copy());
            this.discarded = discarded;
        }

        @Override
        public BlockState setBlockState(int x, int y, int z, BlockState state, boolean lock) {
            // Native ore placement uses BulkSectionAccess rather than WorldGenRegion.setBlock.
            // Dropped writes must not become visible to later reads through this view either.
            discarded.run();
            return getBlockState(x, y, z);
        }

        @Override
        public net.minecraft.world.level.chunk.PalettedContainer<BlockState> getStates() {
            return super.getStates().copy();
        }
    }

    private <T> TickContainerAccess<T> readTicks(TickContainerAccess<T> ticks) {
        return new TickContainerAccess<>() {
            @Override
            public void schedule(ScheduledTick<T> tick) {
                discarded.run();
            }

            @Override
            public boolean hasScheduledTick(BlockPos pos, T type) {
                return ticks.hasScheduledTick(pos, type);
            }

            @Override
            public int count() {
                return ticks.count();
            }
        };
    }
}
