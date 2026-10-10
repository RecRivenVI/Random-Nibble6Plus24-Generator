package io.github.recrivenvi.randomnibble6plus24generator.research;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.material.*;
import net.minecraft.world.ticks.*;

/** Research only: no scheduler, no neighbor ProtoChunks for Pre-FEATURES. */
final class LightweightUniverse implements AutoCloseable {
    final IsolatedGenerationContext context;
    final ServerLevel host;
    final Map<ChunkPos, Map<Structure, StructureStart>> starts = new HashMap<>();
    final Map<ChunkPos, Map<Structure, LongSet>> references = new HashMap<>();
    final Map<ChunkPos, List<PalettedContainer<Holder<Biome>>>> biomes = new HashMap<>();
    final Map<ChunkPos, ProtoChunk> terrain = new LinkedHashMap<>();
    final StructureManager structures;
    final MetadataCarrier structureScratch;
    final MetadataCarrier biomeScratch;
    final MetadataCarrier carverScratch;
    long nativeStructureEvaluations;
    long carverSourceQueries;
    long discardedBlocks,
            discardedEntities,
            discardedBlockTicks,
            discardedFluidTicks,
            discardedPostProcessing;
    int featuresInvoked;

    LightweightUniverse(ServerLevel host, long seed, ChunkPos target) {
        this.host = host;
        context =
                IsolatedGenerationContext.create(
                        IsolatedGenerationMode.ISOLATED_MOSAIC, host, seed, target);
        structureScratch = new MetadataCarrier(host);
        biomeScratch = new MetadataCarrier(host);
        carverScratch = new MetadataCarrier(host);
        structures =
                new StructureManager(
                        host,
                        new WorldOptions(
                                seed,
                                host.getServer()
                                        .getWorldGenSettings()
                                        .options()
                                        .generateStructures(),
                                false),
                        context.structureCheck()) {
                    @Override
                    public List<StructureStart> startsForStructure(
                            ChunkPos pos, Predicate<Structure> predicate) {
                        List<StructureStart> result = new ArrayList<>();
                        referencesAt(pos)
                                .forEach(
                                        (structure, refs) -> {
                                            if (predicate.test(structure))
                                                fillStartsForStructure(
                                                        structure, refs, result::add);
                                        });
                        return result;
                    }

                    @Override
                    public List<StructureStart> startsForStructure(
                            SectionPos pos, Structure structure) {
                        List<StructureStart> result = new ArrayList<>();
                        LongSet refs = referencesAt(pos.chunk()).get(structure);
                        if (refs != null) fillStartsForStructure(structure, refs, result::add);
                        return result;
                    }

                    @Override
                    public void fillStartsForStructure(
                            Structure structure, LongSet refs, Consumer<StructureStart> consumer) {
                        for (long packed : refs) {
                            StructureStart start = startsAt(ChunkPos.unpack(packed)).get(structure);
                            if (start != null && start.isValid()) consumer.accept(start);
                        }
                    }
                };
        for (var set : context.structureState().possibleStructureSets()) {
            if (set.value().placement() instanceof ConcentricRingsStructurePlacement rings)
                context.structureState().getRingPositionsFor(rings);
        }
    }

    Map<Structure, StructureStart> startsAt(ChunkPos pos) {
        Map<Structure, StructureStart> found = starts.get(pos);
        if (found != null) return found;
        if (Boolean.getBoolean("mosaic.lazy.experiment")
                && Boolean.getBoolean("mosaic.lazy.enabled")) {
            var scope =
                    ((io.github.recrivenvi.randomnibble6plus24generator.lazy.LazyStateAccess)
                                    context.structureState())
                            .mosaicLazyScope();
            if (scope.forSource(
                            context.structureState(),
                            pos,
                            context.structureState().possibleStructureSets())
                    .isEmpty()) {
                starts.put(pos, Map.of());
                return Map.of();
            }
        }
        structureScratch.position = pos;
        structureScratch.setAllStarts(Map.of());
        if (structures.shouldGenerateStructures()) {
            synchronized (host.getServer().getStructureManager()) {
                context.generator()
                        .createStructures(
                                host.registryAccess(),
                                context.structureState(),
                                structures,
                                structureScratch,
                                host.getServer().getStructureManager(),
                                host.dimension());
            }
        }
        found = new HashMap<>(structureScratch.getAllStarts());
        starts.put(pos, found);
        nativeStructureEvaluations++;
        return found;
    }

    Map<Structure, LongSet> referencesAt(ChunkPos pos) {
        Map<Structure, LongSet> found = references.get(pos);
        if (found != null) return found;
        // Exact createReferences source-X/source-Z traversal and LongOpenHashSet insertion order.
        // No placement or RNG is reimplemented: startsAt invokes Vanilla createStructures.
        found = new HashMap<>();
        for (int x = pos.x() - 8; x <= pos.x() + 8; x++) {
            for (int z = pos.z() - 8; z <= pos.z() + 8; z++) {
                ChunkPos source = new ChunkPos(x, z);
                for (StructureStart start : startsAt(source).values()) {
                    if (start.isValid()
                            && start.getBoundingBox()
                                    .intersects(
                                            pos.getMinBlockX(),
                                            pos.getMinBlockZ(),
                                            pos.getMaxBlockX(),
                                            pos.getMaxBlockZ())) {
                        found.computeIfAbsent(
                                        start.getStructure(), ignored -> new LongOpenHashSet())
                                .add(ChunkPos.pack(x, z));
                    }
                }
            }
        }
        references.put(pos, found);
        return found;
    }

    ProtoChunk begin(ChunkPos pos) {
        ProtoChunk chunk =
                new ProtoChunk(pos, UpgradeData.EMPTY, host, host.palettedContainerFactory(), null);
        chunk.setAllStarts(startsAt(pos));
        chunk.setAllReferences(referencesAt(pos));
        chunk.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
        terrain.put(pos, chunk);
        return chunk;
    }

    void biomes(ProtoChunk chunk) {
        context.generator()
                .createBiomes(context.randomState(), Blender.empty(), structures, chunk)
                .join();
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
    }

    void noise(ProtoChunk chunk) {
        context.generator()
                .fillFromNoise(Blender.empty(), context.randomState(), structures, chunk)
                .join();
        chunk.setPersistedStatus(ChunkStatus.NOISE);
    }

    void surface(ProtoChunk chunk) {
        Set<Holder<Biome>> possible = new HashSet<>();
        for (int z = chunk.getPos().z() - 1; z <= chunk.getPos().z() + 1; z++) {
            for (int x = chunk.getPos().x() - 1; x <= chunk.getPos().x() + 1; x++) {
                ChunkPos p = new ChunkPos(x, z);
                if (p.equals(chunk.getPos())) chunk.collectBiomesInPalette(possible);
                else for (var palette : biomePalettes(p)) palette.getAll(possible::add);
            }
        }
        BiomeManager manager =
                new BiomeManager(
                        this::storedBiome, BiomeManager.obfuscateSeed(context.worldSeed()));
        context.generator()
                .buildSurface(
                        chunk,
                        new WorldGenerationContext(context.generator(), host),
                        context.randomState(),
                        structures,
                        manager,
                        Blender.empty(),
                        possible);
        chunk.setPersistedStatus(ChunkStatus.SURFACE);
    }

    void carvers(ProtoChunk chunk) {
        ProbeRegion region = new ProbeRegion(this, chunk, false);
        // Fresh same-seed universe: Vanilla's old-chunk filter is an all-false mask.
        Blender.addAroundOldChunksCarvingMaskFilter(region, chunk);
        context.generator()
                .applyCarvers(
                        region,
                        context.worldSeed(),
                        context.randomState(),
                        context.biomeManager(),
                        structures,
                        chunk);
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
    }

    ProtoChunk preFeatures(ChunkPos pos) {
        ProtoChunk chunk = terrain.get(pos);
        if (chunk != null && chunk.getPersistedStatus() == ChunkStatus.CARVERS) return chunk;
        chunk = begin(pos);
        biomes(chunk);
        noise(chunk);
        surface(chunk);
        carvers(chunk);
        return chunk;
    }

    private List<PalettedContainer<Holder<Biome>>> biomePalettes(ChunkPos pos) {
        var found = biomes.get(pos);
        if (found != null) return found;
        // Use the native per-Chunk NoiseChunk wrapper, NOT RandomState.sampler().
        // Only palette storage is allocated, no block sections or ProtoChunk.
        referencesAt(pos);
        biomeScratch.position = pos;
        NoiseChunk noise =
                invoke(
                        NoiseBasedChunkGenerator.class,
                        context.generator(),
                        "createNoiseChunk",
                        new Class<?>[] {
                            ChunkAccess.class,
                            StructureManager.class,
                            Blender.class,
                            RandomState.class
                        },
                        biomeScratch,
                        structures,
                        Blender.empty(),
                        context.randomState());
        Climate.Sampler sampler =
                invoke(
                        NoiseChunk.class,
                        noise,
                        "cachedClimateSampler",
                        new Class<?>[] {NoiseRouter.class, List.class},
                        context.randomState().router(),
                        context.generator().generatorSettings().value().spawnTarget());
        found = new ArrayList<>();
        for (int section = host.getMinSectionY(); section <= host.getMaxSectionY(); section++) {
            PalettedContainer<Holder<Biome>> palette =
                    host.palettedContainerFactory().createForBiomes();
            // Native LevelChunkSection.fillBiomesFromNoise iteration and palette insertion
            // semantics.
            for (int x = 0; x < 4; x++)
                for (int y = 0; y < 4; y++)
                    for (int z = 0; z < 4; z++) {
                        palette.getAndSetUnchecked(
                                x,
                                y,
                                z,
                                context.generator()
                                        .getBiomeSource()
                                        .getNoiseBiome(
                                                pos.x() * 4 + x,
                                                section * 4 + y,
                                                pos.z() * 4 + z,
                                                sampler));
                    }
            found.add(palette);
        }
        biomes.put(pos, found);
        return found;
    }

    Holder<Biome> storedBiome(int qx, int qy, int qz) {
        ChunkPos p = new ChunkPos(Math.floorDiv(qx, 4), Math.floorDiv(qz, 4));
        ProtoChunk chunk = terrain.get(p);
        if (chunk != null && chunk.getPersistedStatus().isOrAfter(ChunkStatus.BIOMES))
            return chunk.getNoiseBiome(qx, qy, qz);
        int y = Math.clamp(qy, host.getMinY() / 4, (host.getMaxY() - 1) / 4);
        return biomePalettes(p)
                .get(Math.floorDiv(y, 4) - host.getMinSectionY())
                .get(qx & 3, y & 3, qz & 3);
    }

    void targetFeatures(ProtoChunk target) {
        for (int z = target.getPos().z() - 1; z <= target.getPos().z() + 1; z++)
            for (int x = target.getPos().x() - 1; x <= target.getPos().x() + 1; x++)
                preFeatures(new ChunkPos(x, z));
        Heightmap.primeHeightmaps(
                target,
                EnumSet.of(
                        Heightmap.Types.MOTION_BLOCKING,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Heightmap.Types.OCEAN_FLOOR,
                        Heightmap.Types.WORLD_SURFACE));
        ProbeRegion region = new ProbeRegion(this, target, true);
        context.generator().applyBiomeDecoration(region, target, structures);
        Blender.generateBorderTicks(region, target);
        target.setPersistedStatus(ChunkStatus.FEATURES);
        featuresInvoked++;
    }

    @Override
    public void close() {
        context.close();
    }

    @SuppressWarnings("unchecked")
    static <T> T invoke(
            Class<?> owner, Object receiver, String name, Class<?>[] signature, Object... args) {
        try {
            Method m = owner.getDeclaredMethod(name, signature);
            m.setAccessible(true);
            return (T) m.invoke(receiver, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(name, e);
        }
    }

    /** Metadata-only API carrier. No voxel, biome, tick, entity, or lighting reads are legal. */
    static class MetadataCarrier extends ChunkAccess {
        ChunkPos position = ChunkPos.ZERO;
        final LevelHeightAccessor realHeight;

        MetadataCarrier(ServerLevel host) {
            super(
                    ChunkPos.ZERO,
                    UpgradeData.EMPTY,
                    LevelHeightAccessor.create(host.getMinY(), 0),
                    host.palettedContainerFactory(),
                    0L,
                    null,
                    null);
            realHeight = host;
        }

        @Override
        public ChunkPos getPos() {
            return position;
        }

        @Override
        public int getMinY() {
            return realHeight.getMinY();
        }

        @Override
        public int getHeight() {
            return realHeight.getHeight();
        }

        @Override
        public BiomeGenerationSettings carverBiome(Supplier<BiomeGenerationSettings> source) {
            return source.get();
        }

        @Override
        public ChunkStatus getPersistedStatus() {
            return ChunkStatus.STRUCTURE_STARTS;
        }

        private IllegalStateException unsupported() {
            return new IllegalStateException(
                    "Unexpected full-chunk read/write on metadata carrier at " + position);
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
        public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
            throw unsupported();
        }

        @Override
        public void setBlockEntity(BlockEntity be) {
            throw unsupported();
        }

        @Override
        public void addEntity(Entity entity) {
            throw unsupported();
        }

        @Override
        public void removeBlockEntity(BlockPos pos) {
            throw unsupported();
        }

        @Override
        public CompoundTag getBlockEntityNbtForSaving(BlockPos p, HolderLookup.Provider r) {
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
        public PackedTicks getTicksForSerialization(long tick) {
            throw unsupported();
        }
    }

    /** Reads exact Pre-FEATURES data. Lazy heightmap caches belong to the view, not its source. */
    static final class ReadOnlyNeighbor extends MetadataCarrier {
        final ProtoChunk source;
        final LightweightUniverse universe;

        ReadOnlyNeighbor(LightweightUniverse u, ProtoChunk source) {
            super(u.host);
            this.source = source;
            this.universe = u;
            this.position = source.getPos();
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
        public void collectBiomesInPalette(Set<Holder<Biome>> result) {
            source.collectBiomesInPalette(result);
        }

        @Override
        public LevelChunkSection[] getSections() {
            return source.getSections();
        }

        @Override
        public Map<Structure, StructureStart> getAllStarts() {
            return source.getAllStarts();
        }

        @Override
        public Map<Structure, LongSet> getAllReferences() {
            return source.getAllReferences();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            if (source.getBlockEntity(pos) != null)
                throw new IllegalStateException("Unexpected BE in Pre-FEATURES neighbor");
            return null;
        }

        @Override
        public CompoundTag getBlockEntityNbt(BlockPos pos) {
            CompoundTag nbt = source.getBlockEntityNbt(pos);
            return nbt == null ? null : nbt.copy();
        }

        @Override
        public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
            universe.discardedBlocks++;
            return null;
        }

        @Override
        public void markPosForPostProcessing(BlockPos pos) {
            universe.discardedPostProcessing++;
        }

        @Override
        public void setBlockEntity(BlockEntity be) {
            universe.discardedBlocks++;
        }

        @Override
        public void setBlockEntityNbt(CompoundTag nbt) {
            universe.discardedBlocks++;
        }

        @Override
        public void removeBlockEntity(BlockPos pos) {
            universe.discardedBlocks++;
        }

        @Override
        public void addEntity(Entity entity) {
            universe.discardedEntities++;
        }

        @Override
        public TickContainerAccess<Block> getBlockTicks() {
            return discardingTicks(source.getBlockTicks(), () -> universe.discardedBlockTicks++);
        }

        @Override
        public TickContainerAccess<Fluid> getFluidTicks() {
            return discardingTicks(source.getFluidTicks(), () -> universe.discardedFluidTicks++);
        }

        private static <T> TickContainerAccess<T> discardingTicks(
                TickContainerAccess<T> source, Runnable discarded) {
            return new TickContainerAccess<>() {
                @Override
                public void schedule(ScheduledTick<T> tick) {
                    discarded.run();
                }

                @Override
                public boolean hasScheduledTick(BlockPos pos, T type) {
                    return source.hasScheduledTick(pos, type);
                }

                @Override
                public int count() {
                    return source.count();
                }
            };
        }
    }

    /** Native region API, with an explicit data provider instead of holders/scheduler. */
    static final class ProbeRegion extends WorldGenRegion {
        final LightweightUniverse u;
        final ProtoChunk target;
        final boolean features;
        final Map<ChunkPos, ReadOnlyNeighbor> readViews = new HashMap<>();

        ProbeRegion(LightweightUniverse u, ProtoChunk target, boolean features) {
            super(
                    u.host,
                    cache(u, target),
                    ChunkPyramid.GENERATION_PYRAMID.getStepTo(
                            features ? ChunkStatus.FEATURES : ChunkStatus.CARVERS),
                    target);
            this.u = u;
            this.target = target;
            this.features = features;
            GenerationContextRegistry.bind(this, u.context);
        }

        private static StaticCache2D<GenerationChunkHolder> cache(
                LightweightUniverse u, ProtoChunk target) {
            StaticCache2D<GenerationChunkHolder> cache =
                    StaticCache2D.create(
                            target.getPos().x(), target.getPos().z(), 0, (x, z) -> null);
            GenerationContextRegistry.bind(cache, u.context);
            return cache;
        }

        @Override
        public boolean isOldChunkAround(ChunkPos pos, int radius) {
            return false;
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
            ChunkPos p = new ChunkPos(x, z);
            if (features) {
                ProtoChunk found = u.terrain.get(p);
                if (found == null)
                    throw new IllegalStateException("FEATURES terrain read beyond 3x3 at " + p);
                return p.equals(target.getPos())
                        ? found
                        : readViews.computeIfAbsent(p, ignored -> new ReadOnlyNeighbor(u, found));
            }
            if (p.equals(target.getPos())) return target;
            u.carverSourceQueries++;
            return u.carverScratch;
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            return u.storedBiome(x, y, z);
        }

        @Override
        public boolean ensureCanWrite(BlockPos pos) {
            boolean inside = ChunkPos.containing(pos).equals(target.getPos());
            if (!inside) u.discardedBlocks++;
            return inside;
        }

        @Override
        public boolean addFreshEntity(Entity entity) {
            if (!ChunkPos.containing(entity.blockPosition()).equals(target.getPos())) {
                u.discardedEntities++;
                return false;
            }
            return super.addFreshEntity(entity);
        }
    }
}
