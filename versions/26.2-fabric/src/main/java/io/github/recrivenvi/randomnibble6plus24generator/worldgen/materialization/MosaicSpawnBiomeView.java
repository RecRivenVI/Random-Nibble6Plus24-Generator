package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.Map;
import java.util.function.Supplier;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.TickContainerAccess;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;

/** V2's read-only BIOMES-stage view, not a view of the FEATURES neighbour terrain. */
final class MosaicSpawnBiomeView extends ChunkAccess {
    private final SpawnBiomeSnapshot snapshot;
    private final BlockState preTerrainState;

    MosaicSpawnBiomeView(ChunkPos pos, SpawnBiomeSnapshot snapshot, PalettedContainerFactory factory) {
        super(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(snapshot.minY(pos), 0), factory, 0L, null, null);
        this.snapshot = snapshot;
        this.preTerrainState = factory.defaultBlockState();
    }

    @Override public int getMinY() { return snapshot.minY(getPos()); }
    @Override public int getHeight() { return snapshot.height(getPos()); }
    @Override public ChunkStatus getPersistedStatus() { return ChunkStatus.BIOMES; }
    @Override public Holder<Biome> getNoiseBiome(int x, int y, int z) { return snapshot.biome(getPos(), x, y, z); }
    private IllegalStateException unsupported() {
        return new IllegalStateException("Unsupported access to V2 BIOMES-stage SPAWN input at " + getPos());
    }
    @Override public BiomeGenerationSettings carverBiome(Supplier<BiomeGenerationSettings> source) { throw unsupported(); }
    // Native BIOMES has not written terrain. Collision iteration reads a one-block
    // border even for a target-contained spawn AABB. Preserve the old V2 input:
    // fresh ProtoChunk's default block state (Vanilla air), never physical blocks.
    @Override public BlockState getBlockState(BlockPos pos) { return preTerrainState; }
    @Override public FluidState getFluidState(BlockPos pos) { return preTerrainState.getFluidState(); }
    @Override public int getHeight(Heightmap.Types type, int x, int z) { throw unsupported(); }
    @Override public Map<Structure, StructureStart> getAllStarts() { throw unsupported(); }
    @Override public StructureStart getStartForStructure(Structure structure) { throw unsupported(); }
    @Override public Map<Structure, LongSet> getAllReferences() { throw unsupported(); }
    @Override public LongSet getReferencesForStructure(Structure structure) { throw unsupported(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { throw unsupported(); }
    @Override public BlockState setBlockState(BlockPos pos, BlockState state, int flags) { throw unsupported(); }
    @Override public void setBlockEntity(BlockEntity entity) { throw unsupported(); }
    @Override public void removeBlockEntity(BlockPos pos) { throw unsupported(); }
    @Override public void addEntity(Entity entity) { throw unsupported(); }
    @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registries) { throw unsupported(); }
    @Override public TickContainerAccess<Block> getBlockTicks() { throw unsupported(); }
    @Override public TickContainerAccess<Fluid> getFluidTicks() { throw unsupported(); }
    @Override public PackedTicks getTicksForSerialization(long time) { throw unsupported(); }
}
