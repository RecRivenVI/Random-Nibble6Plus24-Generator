package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.*;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.ticks.*;

/** Experimental B read frontier. Missing proofs use real same-seed TERRAIN, never defaults. */
public final class LocalFeatureInputs implements AutoCloseable {
    public interface Observer {
        default void block(BlockPos p, BlockState state) {}

        default void biome(int x, int y, int z, Holder<Biome> value) {}

        default void height(ChunkPos p, Heightmap.Types type, int x, int z, int value) {}

        default void fallback(ProtoChunk chunk, String reason) {}
    }

    private final IsolatedGenerationContext context;
    private final Consumer<String> rejected;
    private final Observer observer;
    private final Map<Long, ChunkAccess> palettes = new HashMap<>();
    private final Map<Long, ProtoChunk> terrain = new HashMap<>();
    private final Map<Long, StrictFeatureChunk> frozen = new HashMap<>();
    private final Map<Long, ChunkAccess> inputs = new HashMap<>();
    private final Map<Long, String> fallbackReasons = new TreeMap<>();
    private final LocalTerrainColumns columns;
    private final ScopedStructureSources structureSources;
    private int blockReads, heightReads, sectionReads;
    private long fallbackNanos;
    private boolean closed;

    public LocalFeatureInputs(
            IsolatedGenerationContext context,
            ProtoChunk target,
            Consumer<String> rejected,
            Observer observer) {
        this(context, target, rejected, observer, null);
    }

    LocalFeatureInputs(
            IsolatedGenerationContext context,
            ProtoChunk target,
            Consumer<String> rejected,
            Observer observer,
            ScopedStructureSources structureSources) {
        this(context, target, rejected, observer, structureSources, true);
    }

    LocalFeatureInputs(
            IsolatedGenerationContext context,
            ProtoChunk target,
            Consumer<String> rejected,
            Observer observer,
            ScopedStructureSources structureSources,
            boolean captureProofs) {
        this.context = context;
        this.rejected = rejected;
        this.observer = observer;
        this.structureSources = structureSources;
        terrain.put(target.getPos().pack(), target);
        palettes.put(target.getPos().pack(), target);
        columns = new LocalTerrainColumns(context, this::palette, structureSources, captureProofs);
        for (int z = context.target().z() - 1; z <= context.target().z() + 1; z++)
            for (int x = context.target().x() - 1; x <= context.target().x() + 1; x++) {
                var p = new ChunkPos(x, z);
                inputs.put(
                        p.pack(),
                        p.equals(context.target())
                                ? new StrictFeatureChunk(
                                        target,
                                        context.target(),
                                        context.hostLevel().palettedContainerFactory(),
                                        rejected,
                                        context.hostLevel().registryAccess())
                                : new Neighbor(p));
            }
    }

    public Map<Long, ChunkAccess> chunks() {
        return Map.copyOf(inputs);
    }

    public Map<Long, ProtoChunk> fullTerrain() {
        return Map.copyOf(terrain);
    }

    /** Copies for downstream SPAWN must be taken while this seed-local input is still open. */
    public ChunkAccess storedBiomeInput(ChunkPos position) {
        if (!SpawnBiomeSnapshot.neighbors(context.target()).contains(position))
            throw new IllegalArgumentException("Outside detached SPAWN frontier " + position);
        return palette(position);
    }

    public boolean isClosed() {
        return closed;
    }

    public record Metrics(
            int fullNeighbors,
            Map<Long, String> fallbackReasons,
            long fallbackNanos,
            int blockReads,
            int heightReads,
            int sectionReads,
            int biomePalettes,
            LocalTerrainColumns.Metrics columns) {}

    public record Counters(
            int fullNeighbors,
            Map<Long, String> fallbackReasons,
            long fallbackNanos,
            int blockReads,
            int heightReads,
            int sectionReads,
            int biomePalettes,
            LocalTerrainColumns.Counters columns) {}

    public Counters counters() {
        return new Counters(
                terrain.size() - 1,
                Map.copyOf(fallbackReasons),
                fallbackNanos,
                blockReads,
                heightReads,
                sectionReads,
                palettes.size() - 1,
                columns.counters());
    }

    public Metrics metrics() {
        return new Metrics(
                terrain.size() - 1,
                Map.copyOf(fallbackReasons),
                fallbackNanos,
                blockReads,
                heightReads,
                sectionReads,
                palettes.size() - 1,
                columns.metrics());
    }

    public Optional<ScopedStructureSources.Metrics> structureReuseMetrics() {
        return structureSources == null
                ? Optional.empty()
                : Optional.of(structureSources.metrics());
    }

    private void open() {
        if (closed
                || GenerationContextRegistry.find(context.worldGenContext()).orElse(null)
                        != context) throw new IllegalStateException("Closed B local input/owner");
    }

    private ChunkAccess palette(ChunkPos p) {
        open();
        // R1 FEATURES reads plus the R1 stored-biome/surface halo of each query batch.
        if (Math.abs((long) p.x() - context.target().x()) > 2
                || Math.abs((long) p.z() - context.target().z()) > 2)
            throw new IllegalArgumentException("Outside B stored biome halo " + p);
        return palettes.computeIfAbsent(
                p.pack(),
                k -> {
                    var c =
                            new BiomePaletteChunk(
                                    p,
                                    context.hostLevel(),
                                    context.hostLevel().palettedContainerFactory());
                    context.generator()
                            .createBiomes(
                                    context.randomState(),
                                    Blender.empty(),
                                    context.structureManager(),
                                    c)
                            .join();
                    return c;
                });
    }

    private StrictFeatureChunk full(ChunkPos p, String reason) {
        open();
        return frozen.computeIfAbsent(
                p.pack(),
                k -> {
                    long start = System.nanoTime();
                    ProtoChunk c;
                    try (var child =
                            IsolatedGenerationContext.create(
                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                    context.hostLevel(),
                                    context.worldSeed(),
                                    p)) {
                        c =
                                LightweightTerrainGenerator.generate(
                                                child,
                                                CompactStructureInputs.resolve(
                                                        child, structureSources),
                                                (s, chunk) -> {})
                                        .target();
                    }
                    fallbackNanos += System.nanoTime() - start;
                    terrain.put(k, c);
                    fallbackReasons.put(k, reason);
                    observer.fallback(c, reason);
                    return new StrictFeatureChunk(
                            c,
                            context.target(),
                            context.hostLevel().palettedContainerFactory(),
                            rejected,
                            context.hostLevel().registryAccess());
                });
    }

    @Override
    public void close() {
        columns.close();
        closed = true;
    }

    private final class Neighbor extends ChunkAccess {
        private final LevelChunkSection[] sections;

        Neighbor(ChunkPos p) {
            super(p, UpgradeData.EMPTY, LevelHeightAccessor.create(0, 0), null, 0, null, null);
            sections = new LevelChunkSection[context.hostLevel().getSectionsCount()];
        }

        private StrictFeatureChunk full(String reason) {
            return LocalFeatureInputs.this.full(getPos(), reason);
        }

        private void local(BlockPos p) {
            open();
            if (!ChunkPos.containing(p).equals(getPos()))
                throw new IllegalArgumentException("Foreign B local input coordinate " + p);
        }

        @Override
        public int getMinY() {
            return context.hostLevel().getMinY();
        }

        @Override
        public int getHeight() {
            return context.hostLevel().getHeight();
        }

        @Override
        public LevelHeightAccessor getHeightAccessorForGeneration() {
            return context.hostLevel();
        }

        @Override
        public ChunkStatus getPersistedStatus() {
            return ChunkStatus.TERRAIN;
        }

        @Override
        public boolean canBeSerialized() {
            return false;
        }

        @Override
        public BlockState getBlockState(BlockPos p) {
            local(p);
            blockReads++;
            var known = frozen.get(getPos().pack());
            BlockState s =
                    known != null
                            ? known.getBlockState(p)
                            : columns.block(p)
                                    .orElseGet(
                                            () ->
                                                    full("unproved local terrain column")
                                                            .getBlockState(p));
            observer.block(p, s);
            return s;
        }

        @Override
        public FluidState getFluidState(BlockPos p) {
            return getBlockState(p).getFluidState();
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            open();
            heightReads++;
            int ax = getPos().getBlockX(x & 15), az = getPos().getBlockZ(z & 15);
            var known = frozen.get(getPos().pack());
            int value;
            if (known != null) value = known.getHeight(type, x, z);
            else if (Set.of(
                            Heightmap.Types.WORLD_SURFACE_WG,
                            Heightmap.Types.OCEAN_FLOOR_WG,
                            Heightmap.Types.WORLD_SURFACE,
                            Heightmap.Types.OCEAN_FLOOR)
                    .contains(type)) {
                var h = columns.height(type, ax, az);
                value =
                        h.isPresent()
                                ? h.getAsInt()
                                : full("unproved local height column").getHeight(type, x, z);
            } else value = full("unaudited heightmap " + type).getHeight(type, x, z);
            observer.height(getPos(), type, x, z, value);
            return value;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            if (QuartPos.toSection(x) != getPos().x() || QuartPos.toSection(z) != getPos().z())
                throw new IllegalArgumentException("Foreign B stored biome coordinate");
            var value = palette(getPos()).getNoiseBiome(x, y, z);
            observer.biome(x, y, z, value);
            return value;
        }

        @Override
        public void collectBiomesInPalette(Set<Holder<Biome>> output) {
            palette(getPos()).collectBiomesInPalette(output);
        }

        @Override
        public LevelChunkSection getSection(int index) {
            open();
            sectionReads++;
            if (sections[index] == null) sections[index] = new QuerySection(index);
            return sections[index];
        }

        @Override
        public LevelChunkSection[] getSections() {
            for (int i = 0; i < sections.length; i++) getSection(i);
            return sections.clone();
        }

        @Override
        public BlockState setBlockState(BlockPos p, BlockState s, int flags) {
            rejected.accept("block");
            return null;
        }

        @Override
        public void setBlockEntity(BlockEntity e) {
            rejected.accept("block_entity");
        }

        @Override
        public void setBlockEntityNbt(CompoundTag tag) {
            rejected.accept("pending_block_entity");
        }

        @Override
        public void removeBlockEntity(BlockPos p) {
            rejected.accept("remove_block_entity");
        }

        @Override
        public void addEntity(Entity e) {
            rejected.accept("entity");
        }

        @Override
        public void markPosForPostProcessing(BlockPos p) {
            rejected.accept("post_processing");
        }

        @Override
        public void addPackedPostProcess(ShortList list, int section) {
            rejected.accept("packed_post_processing");
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos p) {
            local(p);
            return full("BlockEntity read").getBlockEntity(p);
        }

        @Override
        public CompoundTag getBlockEntityNbt(BlockPos p) {
            local(p);
            return full("pending NBT read").getBlockEntityNbt(p);
        }

        @Override
        public CompoundTag getBlockEntityNbtForSaving(BlockPos p, HolderLookup.Provider r) {
            local(p);
            return full("saving NBT read").getBlockEntityNbtForSaving(p, r);
        }

        @Override
        public Set<BlockPos> getBlockEntitiesPos() {
            return full("BlockEntity positions").getBlockEntitiesPos();
        }

        @Override
        public ShortList[] getPostProcessing() {
            return full("post-processing read").getPostProcessing();
        }

        @Override
        public TickContainerAccess<Block> getBlockTicks() {
            return lazyTicks(true);
        }

        @Override
        public TickContainerAccess<Fluid> getFluidTicks() {
            return lazyTicks(false);
        }

        @SuppressWarnings("unchecked")
        private <T> TickContainerAccess<T> lazyTicks(boolean block) {
            return new TickContainerAccess<>() {
                @Override
                public void schedule(ScheduledTick<T> tick) {
                    open();
                    rejected.accept(block ? "block_tick" : "fluid_tick");
                }

                private TickContainerAccess<T> source() {
                    var c = full("tick container read");
                    return (TickContainerAccess<T>) (block ? c.getBlockTicks() : c.getFluidTicks());
                }

                @Override
                public boolean hasScheduledTick(BlockPos p, T t) {
                    return source().hasScheduledTick(p, t);
                }

                @Override
                public int count() {
                    return source().count();
                }
            };
        }

        @Override
        public PackedTicks getTicksForSerialization(long time) {
            return full("tick serialization").getTicksForSerialization(time);
        }

        @Override
        public LongSet getReferencesForStructure(Structure s) {
            return full("structure references read").getReferencesForStructure(s);
        }

        @Override
        public Map<Structure, LongSet> getAllReferences() {
            return full("structure references read").getAllReferences();
        }

        @Override
        public boolean hasPrimedHeightmap(Heightmap.Types type) {
            return full("heightmap presence").hasPrimedHeightmap(type);
        }

        @Override
        public Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
            throw new UnsupportedOperationException("No borrowed B heightmaps");
        }

        @Override
        public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types t) {
            throw new UnsupportedOperationException("No borrowed B heightmaps");
        }

        @Override
        public void setHeightmap(Heightmap.Types t, long[] data) {
            throw new UnsupportedOperationException("No B neighbor height mutation");
        }

        @Override
        public StructureStart getStartForStructure(Structure s) {
            throw new UnsupportedOperationException("Use compact structure manager");
        }

        @Override
        public Map<Structure, StructureStart> getAllStarts() {
            throw new UnsupportedOperationException("Use compact structure manager");
        }

        @Override
        public void setAllStarts(Map<Structure, StructureStart> starts) {
            throw new UnsupportedOperationException("No B structure mutation");
        }

        @Override
        public void setStartForStructure(Structure s, StructureStart start) {
            throw new UnsupportedOperationException("No B structure mutation");
        }

        @Override
        public void setAllReferences(Map<Structure, LongSet> refs) {
            throw new UnsupportedOperationException("No B structure mutation");
        }

        @Override
        public void addReferenceForStructure(Structure s, long p) {
            throw new UnsupportedOperationException("No B structure mutation");
        }

        @Override
        public void fillBiomesFromNoise(net.minecraft.world.level.biome.BiomeResolver r) {
            throw new UnsupportedOperationException("Stored B biomes are immutable");
        }

        @Override
        public UpgradeData getUpgradeData() {
            throw new UnsupportedOperationException("B has no upgrade authority");
        }

        @Override
        public void initializeLightSources() {
            throw new UnsupportedOperationException("B stops before LIGHT");
        }

        @Override
        public net.minecraft.world.level.lighting.ChunkSkyLightSources getSkyLightSources() {
            throw new UnsupportedOperationException("B stops before LIGHT");
        }

        @Override
        public void setLightCorrect(boolean value) {
            throw new UnsupportedOperationException("B stops before LIGHT");
        }

        private final class QuerySection extends LevelChunkSection {
            private final int index;
            private PalettedContainer<Holder<Biome>> storedBiomes;

            QuerySection(int index) {
                super(context.hostLevel().palettedContainerFactory());
                this.index = index;
            }

            private BlockPos pos(int x, int y, int z) {
                return new BlockPos(
                        getPos().getBlockX(x),
                        getSectionYFromSectionIndex(index) * 16 + y,
                        getPos().getBlockZ(z));
            }

            private LevelChunkSection exact() {
                return full("bulk section capability").getSections()[index];
            }

            // The constructor containers are not terrain authority. Every read is routed.
            @Override
            public BlockState getBlockState(int x, int y, int z) {
                return Neighbor.this.getBlockState(pos(x, y, z));
            }

            @Override
            public FluidState getFluidState(int x, int y, int z) {
                return getBlockState(x, y, z).getFluidState();
            }

            @Override
            public Holder<Biome> getNoiseBiome(int x, int y, int z) {
                return Neighbor.this.getNoiseBiome(
                        QuartPos.fromBlock(getPos().getMinBlockX()) + x,
                        QuartPos.fromSection(getSectionYFromSectionIndex(index)) + y,
                        QuartPos.fromBlock(getPos().getMinBlockZ()) + z);
            }

            @Override
            public void acquire() {
                open();
            }

            @Override
            public void release() {
                open();
            }

            @Override
            public BlockState setBlockState(int x, int y, int z, BlockState s, boolean lock) {
                rejected.accept("section_block");
                return getBlockState(x, y, z);
            }

            @Override
            public BlockState setBlockState(int x, int y, int z, BlockState s) {
                return setBlockState(x, y, z, s, true);
            }

            @Override
            public boolean hasOnlyAir() {
                return exact().hasOnlyAir();
            }

            @Override
            public boolean hasFluid() {
                return exact().hasFluid();
            }

            @Override
            public boolean isRandomlyTicking() {
                return exact().isRandomlyTicking();
            }

            @Override
            public boolean isRandomlyTickingBlocks() {
                return exact().isRandomlyTickingBlocks();
            }

            @Override
            public boolean isRandomlyTickingFluids() {
                return exact().isRandomlyTickingFluids();
            }

            @Override
            public PalettedContainer<BlockState> getStates() {
                return exact().getStates();
            }

            @Override
            public PalettedContainerRO<Holder<Biome>> getBiomes() {
                open();
                if (storedBiomes == null) {
                    storedBiomes = context.hostLevel().palettedContainerFactory().createForBiomes();
                    for (int x = 0; x < 4; x++)
                        for (int y = 0; y < 4; y++)
                            for (int z = 0; z < 4; z++)
                                storedBiomes.getAndSetUnchecked(x, y, z, getNoiseBiome(x, y, z));
                }
                return storedBiomes.copy();
            }

            @Override
            public LevelChunkSection copy() {
                return exact().copy();
            }

            @Override
            public boolean maybeHas(java.util.function.Predicate<BlockState> p) {
                return exact().maybeHas(p);
            }

            @Override
            public int getSerializedSize() {
                return exact().getSerializedSize();
            }

            @Override
            public void write(FriendlyByteBuf b) {
                exact().write(b);
            }

            @Override
            public void read(FriendlyByteBuf b) {
                throw new UnsupportedOperationException("No B neighbor replacement");
            }

            @Override
            public void readBiomes(FriendlyByteBuf b) {
                throw new UnsupportedOperationException("No B biome replacement");
            }

            @Override
            public void fillBiomesFromNoise(
                    net.minecraft.world.level.biome.BiomeResolver r, int x, int y, int z) {
                throw new UnsupportedOperationException("No B biome replacement");
            }
        }
    }
}
