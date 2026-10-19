package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.TickContainerAccess;

/** Metadata carrier for native createStructures/createReferences, not an empty terrain chunk. */
public class StructureInputChunk extends ChunkAccess {
    private final LevelHeightAccessor height;

    protected StructureInputChunk(ChunkPos position, LevelHeightAccessor height) {
        // No sections or palettes are allocated. All terrain APIs fail closed; the real height
        // accessor below is what native Structure.GenerationContext receives.
        super(position, UpgradeData.EMPTY, LevelHeightAccessor.create(0, 0), null, 0, null, null);
        this.height = height;
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "Structure-only input has no terrain: " + getPos());
    }

    @Override
    public int getMinBuildHeight() {
        return height.getMinBuildHeight();
    }

    @Override
    public int getHeight() {
        return height.getHeight();
    }

    @Override
    public LevelHeightAccessor getHeightAccessorForGeneration() {
        return height;
    }

    @Override
    public ChunkStatus getStatus() {
        return ChunkStatus.STRUCTURE_STARTS;
    }

    @Override
    public LevelChunkSection[] getSections() {
        throw unsupported();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        throw unsupported();
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        throw unsupported();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, boolean moving) {
        throw unsupported();
    }

    @Override
    public void setBlockEntity(BlockEntity entity) {
        throw unsupported();
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public void setBlockEntityNbt(CompoundTag tag) {
        throw unsupported();
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public void addEntity(Entity entity) {
        throw unsupported();
    }

    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        throw unsupported();
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        throw unsupported();
    }

    @Override
    public TicksToSave getTicksForSerialization() {
        throw unsupported();
    }
}
