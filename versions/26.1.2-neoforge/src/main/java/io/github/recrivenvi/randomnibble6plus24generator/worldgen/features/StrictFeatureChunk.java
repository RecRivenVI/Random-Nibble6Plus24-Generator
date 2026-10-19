package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickContainerAccess;

/** Explicit B capability: a writer may mutate only its own chunk, not a returned neighbor. */
public final class StrictFeatureChunk extends ChunkAccess {
    private final ProtoChunk source;
    private final boolean writable;
    private final Consumer<String> rejected;
    private final LevelChunkSection[] views;
    private final HolderLookup.Provider registries;

    public StrictFeatureChunk(
            ProtoChunk source,
            ChunkPos writer,
            PalettedContainerFactory factory,
            Consumer<String> rejected,
            HolderLookup.Provider registries) {
        super(
                source.getPos(),
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(source.getMinY(), 0),
                factory,
                0,
                null,
                null);
        this.source = source;
        this.writable = writer.equals(source.getPos());
        this.rejected = rejected;
        this.registries = registries;
        views = new LevelChunkSection[source.getSections().length];
        for (int i = 0; i < views.length; i++) {
            LevelChunkSection section = source.getSection(i);
            views[i] = writable ? section : new FrozenSection(section);
        }
    }

    private boolean allow(String api, BlockPos pos) {
        if (writable && ChunkPos.containing(pos).equals(getPos())) return true;
        rejected.accept(api);
        return false;
    }

    private void requireLocal(BlockPos pos) {
        if (!ChunkPos.containing(pos).equals(getPos()))
            throw new IllegalArgumentException(
                    "Chunk capability used with foreign position " + pos);
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
        requireLocal(pos);
        return source.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        requireLocal(pos);
        return source.getFluidState(pos);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        if (net.minecraft.core.QuartPos.toSection(x) != getPos().x()
                || net.minecraft.core.QuartPos.toSection(z) != getPos().z())
            throw new IllegalArgumentException("Foreign stored biome coordinate");
        return source.getNoiseBiome(x, y, z);
    }

    @Override
    public LevelChunkSection[] getSections() {
        return views.clone();
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return source.getHeight(type, x, z);
    }

    @Override
    public boolean hasPrimedHeightmap(Heightmap.Types type) {
        return source.hasPrimedHeightmap(type);
    }

    @Override
    public java.util.Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        throw new UnsupportedOperationException("Use immutable height queries");
    }

    @Override
    public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
        throw new UnsupportedOperationException("No borrowed mutable heightmap");
    }

    @Override
    public void setHeightmap(Heightmap.Types type, long[] values) {
        throw new UnsupportedOperationException("Block writes update native heightmaps");
    }

    @Override
    public UpgradeData getUpgradeData() {
        throw new UnsupportedOperationException("FEATURES does not own upgrade state");
    }

    @Override
    public void initializeLightSources() {
        throw new UnsupportedOperationException("B stops before light");
    }

    @Override
    public net.minecraft.world.level.lighting.ChunkSkyLightSources getSkyLightSources() {
        throw new UnsupportedOperationException("B has no initialized light sources");
    }

    @Override
    public void setLightCorrect(boolean correct) {
        throw new UnsupportedOperationException("B stops before light");
    }

    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
        return allow("block", pos) ? source.setBlockState(pos, state, flags) : null;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        requireLocal(pos);
        if (writable) return source.getBlockEntity(pos);
        BlockEntity entity = source.getBlockEntity(pos);
        // Returned objects must not provide neighbor mutation authority through setters.
        return entity == null
                ? null
                : BlockEntity.loadStatic(
                        pos,
                        source.getBlockState(pos),
                        entity.saveWithFullMetadata(registries),
                        registries);
    }

    @Override
    public CompoundTag getBlockEntityNbt(BlockPos pos) {
        requireLocal(pos);
        var tag = source.getBlockEntityNbt(pos);
        return tag == null ? null : tag.copy();
    }

    @Override
    public CompoundTag getBlockEntityNbtForSaving(BlockPos pos, HolderLookup.Provider registry) {
        requireLocal(pos);
        var tag = source.getBlockEntityNbtForSaving(pos, registry);
        return tag == null ? null : tag.copy();
    }

    @Override
    public Set<BlockPos> getBlockEntitiesPos() {
        return Set.copyOf(source.getBlockEntitiesPos());
    }

    @Override
    public void setBlockEntity(BlockEntity entity) {
        if (allow("block_entity", entity.getBlockPos())) source.setBlockEntity(entity);
    }

    @Override
    public void setBlockEntityNbt(CompoundTag tag) {
        BlockPos pos =
                new BlockPos(
                        tag.getInt("x").orElseThrow(),
                        tag.getInt("y").orElseThrow(),
                        tag.getInt("z").orElseThrow());
        if (allow("pending_block_entity", pos)) source.setBlockEntityNbt(tag.copy());
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        if (allow("remove_block_entity", pos)) source.removeBlockEntity(pos);
    }

    @Override
    public void addEntity(Entity entity) {
        if (!allow("entity", entity.blockPosition())) return;
        // Vanilla's ProtoChunk captures NBT, not physical registration. Do not retain Entity
        // objects.
        if (entity.isPassenger()) return;
        var output =
                net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess());
        entity.save(output);
        addEntityNbt(output.buildResult());
    }

    public void addEntityNbt(CompoundTag input) {
        CompoundTag tag = input.copy();
        if (!clipEntity(tag)) return;
        source.addEntity(tag);
    }

    private boolean clipEntity(CompoundTag tag) {
        var position = tag.getListOrEmpty("Pos");
        if (position.size() != 3) throw new IllegalArgumentException("Entity NBT has no position");
        double x = position.getDoubleOr(0, Double.NaN),
                y = position.getDoubleOr(1, Double.NaN),
                z = position.getDoubleOr(2, Double.NaN);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Invalid entity position");
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!allow("entity_nbt", pos)) return false;
        if (tag.contains("Passengers")) {
            var clipped = new net.minecraft.nbt.ListTag();
            for (var passenger : tag.getListOrEmpty("Passengers")) {
                if (!(passenger instanceof CompoundTag child))
                    throw new IllegalArgumentException("Invalid passenger NBT");
                if (clipEntity(child)) clipped.add(child);
            }
            tag.put("Passengers", clipped);
        }
        return true;
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        if (allow("post_processing", pos)) source.markPosForPostprocessing(pos);
    }

    @Override
    public void addPackedPostProcess(ShortList offsets, int section) {
        if (writable)
            source.addPackedPostProcess(
                    new it.unimi.dsi.fastutil.shorts.ShortArrayList(offsets), section);
        else rejected.accept("packed_post_processing");
    }

    @Override
    public ShortList[] getPostProcessing() {
        ShortList[] input = source.getPostProcessing();
        if (writable) return input;
        ShortList[] copy = new ShortList[input.length];
        for (int i = 0; i < input.length; i++)
            if (input[i] != null)
                copy[i] = new it.unimi.dsi.fastutil.shorts.ShortArrayList(input[i]);
        return copy;
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return guardedTicks(source.getBlockTicks(), "block_tick");
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks() {
        return guardedTicks(source.getFluidTicks(), "fluid_tick");
    }

    private <T> TickContainerAccess<T> guardedTicks(TickContainerAccess<T> input, String api) {
        return new TickContainerAccess<>() {
            @Override
            public void schedule(ScheduledTick<T> tick) {
                if (allow(api, tick.pos())) input.schedule(tick);
            }

            @Override
            public boolean hasScheduledTick(BlockPos pos, T type) {
                return input.hasScheduledTick(pos, type);
            }

            @Override
            public int count() {
                return input.count();
            }
        };
    }

    @Override
    public PackedTicks getTicksForSerialization(long time) {
        return source.getTicksForSerialization(time);
    }

    @Override
    public boolean canBeSerialized() {
        return false;
    }

    @Override
    public LongSet getReferencesForStructure(Structure structure) {
        return LongSets.unmodifiable(
                new it.unimi.dsi.fastutil.longs.LongOpenHashSet(
                        source.getReferencesForStructure(structure)));
    }

    @Override
    public Map<Structure, LongSet> getAllReferences() {
        Map<Structure, LongSet> copy = new java.util.HashMap<>();
        source.getAllReferences()
                .forEach(
                        (key, value) ->
                                copy.put(
                                        key,
                                        LongSets.unmodifiable(
                                                new it.unimi.dsi.fastutil.longs.LongOpenHashSet(
                                                        value))));
        return Map.copyOf(copy);
    }

    @Override
    public StructureStart getStartForStructure(Structure structure) {
        throw new UnsupportedOperationException(
                "Use compact structure manager, not mutable chunk starts");
    }

    @Override
    public Map<Structure, StructureStart> getAllStarts() {
        throw new UnsupportedOperationException(
                "Use compact structure manager, not mutable chunk starts");
    }

    @Override
    public void setStartForStructure(Structure structure, StructureStart start) {
        throw new UnsupportedOperationException("FEATURES cannot replace structure inputs");
    }

    @Override
    public void setAllStarts(Map<Structure, StructureStart> starts) {
        throw new UnsupportedOperationException("FEATURES cannot replace structure inputs");
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> references) {
        throw new UnsupportedOperationException("FEATURES cannot replace structure inputs");
    }

    @Override
    public void addReferenceForStructure(Structure structure, long reference) {
        throw new UnsupportedOperationException("FEATURES cannot replace structure inputs");
    }

    @Override
    public void fillBiomesFromNoise(
            net.minecraft.world.level.biome.BiomeResolver resolver,
            net.minecraft.world.level.biome.Climate.Sampler sampler) {
        throw new UnsupportedOperationException(
                "Stored biome authority is immutable during FEATURES");
    }

    private final class FrozenSection extends LevelChunkSection {
        FrozenSection(LevelChunkSection section) {
            super(section.getStates().copy(), section.getBiomes().copy());
        }

        @Override
        public BlockState setBlockState(int x, int y, int z, BlockState state, boolean lock) {
            rejected.accept("section_block");
            return getBlockState(x, y, z);
        }

        @Override
        public PalettedContainer<BlockState> getStates() {
            return super.getStates().copy();
        }

        @Override
        public PalettedContainerRO<Holder<Biome>> getBiomes() {
            return super.getBiomes().copy();
        }

        @Override
        public void fillBiomesFromNoise(
                net.minecraft.world.level.biome.BiomeResolver resolver,
                net.minecraft.world.level.biome.Climate.Sampler sampler,
                int x,
                int y,
                int z) {
            throw new UnsupportedOperationException("Neighbor stored biome input is immutable");
        }

        @Override
        public void read(net.minecraft.network.FriendlyByteBuf buffer) {
            throw new UnsupportedOperationException("No neighbor section replacement");
        }

        @Override
        public void readBiomes(net.minecraft.network.FriendlyByteBuf buffer) {
            throw new UnsupportedOperationException("No neighbor biome replacement");
        }
    }
}
