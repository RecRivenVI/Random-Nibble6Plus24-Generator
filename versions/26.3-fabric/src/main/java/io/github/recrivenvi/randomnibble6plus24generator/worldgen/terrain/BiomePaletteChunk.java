package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.TickContainerAccess;

/** Stored native biome palettes only; no neighbor block sections, terrain, ticks or holders. */
public final class BiomePaletteChunk extends ChunkAccess {
    private final LevelHeightAccessor height;
    private final List<PalettedContainer<Holder<Biome>>> palettes = new ArrayList<>();
    private boolean ready;

    public BiomePaletteChunk(
            ChunkPos position, LevelHeightAccessor height, PalettedContainerFactory factory) {
        super(position, UpgradeData.EMPTY, LevelHeightAccessor.create(0, 0), null, 0, null, null);
        this.height = height;
        for (int section = 0; section < height.getSectionsCount(); section++)
            palettes.add(factory.createForBiomes());
    }

    @Override
    public int getMinY() {
        return height.getMinY();
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
    public ChunkStatus getPersistedStatus() {
        return ready ? ChunkStatus.BIOMES : ChunkStatus.STRUCTURE_REFERENCES;
    }

    @Override
    public boolean canBeSerialized() {
        return false;
    }

    @Override
    public void fillBiomesFromNoise(BiomeResolver resolver) {
        if (ready) throw new IllegalStateException("Biome palette already generated");
        // Same section and x/y/z order as ChunkAccess + LevelChunkSection in 26.3. The resolver
        // is supplied by native createBiomes with its buffer arena and cached climate sampler.
        for (int sectionY = height.getMinSectionY();
                sectionY <= height.getMaxSectionY();
                sectionY++) {
            var palette = palettes.get(sectionY - height.getMinSectionY()).recreate();
            for (int x = 0; x < 4; x++)
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 4; z++)
                        palette.getAndSetUnchecked(
                                x,
                                y,
                                z,
                                resolver.getNoiseBiome(
                                        QuartPos.fromBlock(getPos().getMinBlockX()) + x,
                                        QuartPos.fromSection(sectionY) + y,
                                        QuartPos.fromBlock(getPos().getMinBlockZ()) + z));
            palettes.set(sectionY - height.getMinSectionY(), palette);
        }
        ready = true;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        requireReady();
        int min = QuartPos.fromBlock(getMinY());
        int clamped = Math.clamp(y, min, min + QuartPos.fromBlock(getHeight()) - 1);
        return palettes.get(height.getSectionIndex(QuartPos.toBlock(clamped)))
                .get(x & 3, clamped & 3, z & 3);
    }

    @Override
    public void collectBiomesInPalette(Set<Holder<Biome>> output) {
        requireReady();
        palettes.forEach(palette -> palette.forEachInPalette(output::add));
    }

    private void requireReady() {
        if (!ready) throw new IllegalStateException("Biome palette is not generated");
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("Biome-only input has no terrain: " + getPos());
    }

    @Override
    public LevelChunkSection[] getSections() {
        throw unsupported();
    }

    @Override
    public LevelChunkSection getSection(int index) {
        throw unsupported();
    }

    @Override
    public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types type, int x, int z) {
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
    public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
        throw unsupported();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
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
    public void markPosForPostProcessing(BlockPos pos) {
        throw unsupported();
    }

    @Override
    public void addEntity(Entity entity) {
        throw unsupported();
    }

    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider provider) {
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
    public PackedTicks getTicksForSerialization(long time) {
        throw unsupported();
    }
}
