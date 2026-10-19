package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import it.unimi.dsi.fastutil.shorts.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.*;
import net.minecraft.world.ticks.*;

/** Transient same-seed causal scratch state; only the real target is a persistent writer. */
public final class TreeProjectionRegion extends WorldGenRegion implements AutoCloseable {
    private final IsolatedGenerationContext context;
    private final StrictFeatureBaseline.Result baseline;
    private final NeighborOreContributions.Query terrain;
    private final Map<Long, ChunkAccess> scratch = new HashMap<>();
    private final java.util.function.Function<ChunkAccess, ChunkAccess> control;
    private final WorldGenRegion auxiliaryOwner;
    private final OreHeightQuery airQueries;
    private final LocalTerrainColumns localTerrain;
    private final Set<BlockPos> certifiedAir = new HashSet<>();
    private RandomSource writerRandom;
    private ChunkPos writer;
    private long subTick;
    private boolean closed;
    private boolean foreignWriterUsed;
    private int outsideBlockWrites, targetBlockWrites, outsideBeWrites, outsidePostWrites;

    public TreeProjectionRegion(
            IsolatedGenerationContext context, StrictFeatureBaseline.Result baseline) {
        this(context, baseline, null);
    }

    public TreeProjectionRegion(
            IsolatedGenerationContext context,
            StrictFeatureBaseline.Result baseline,
            java.util.function.Function<ChunkAccess, ChunkAccess> control) {
        this(context, baseline, control, null);
    }

    /** A C slot borrows its target loop's region RNG and tick sequence, never resets them. */
    public TreeProjectionRegion(
            IsolatedGenerationContext context,
            StrictFeatureBaseline.Result baseline,
            java.util.function.Function<ChunkAccess, ChunkAccess> control,
            WorldGenRegion auxiliaryOwner) {
        super(
                context.hostLevel(),
                carriers(context, baseline),
                ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES),
                baseline.target());
        if (GenerationContextRegistry.find(baseline.region()).orElse(null) != context)
            throw new IllegalArgumentException("Tree A requires its own live B context");
        this.context = context;
        this.baseline = baseline;
        this.control = control;
        if (auxiliaryOwner != null && auxiliaryOwner != baseline.region())
            throw new IllegalArgumentException("Tree slot auxiliary state belongs to another loop");
        this.auxiliaryOwner = auxiliaryOwner;
        terrain = new NeighborOreContributions.Query(context, baseline);
        airQueries = new OreHeightQuery(context, terrain::heightPalette);
        localTerrain = new LocalTerrainColumns(context, terrain::heightPalette);
        scratch.put(
                context.target().pack(),
                new StrictFeatureChunk(
                        baseline.target(),
                        context.target(),
                        context.hostLevel().palettedContainerFactory(),
                        api -> {
                            throw new IllegalArgumentException(
                                    "Foreign target tree capability " + api);
                        },
                        context.hostLevel().registryAccess()));
        GenerationContextRegistry.bind(this, context);
        beginWriter(context.target());
    }

    private static StaticCache2D<GenerationChunkHolder> carriers(
            IsolatedGenerationContext c, StrictFeatureBaseline.Result b) {
        var cache =
                StaticCache2D.create(
                        c.target().x(),
                        c.target().z(),
                        0,
                        (x, z) -> StrictFeatureRegion.constructorCarrier(b.target()));
        GenerationContextRegistry.bind(cache, c);
        return cache;
    }

    public void beginWriter(ChunkPos writer) {
        ensureOpen();
        if (auxiliaryOwner != null && !writer.equals(context.target()))
            throw new IllegalArgumentException("C cannot borrow another writer's auxiliary state");
        if (Math.abs((long) writer.x() - context.target().x()) > 1
                || Math.abs((long) writer.z() - context.target().z()) > 1)
            throw new IllegalArgumentException("Outside tree source frontier");
        this.writer = writer;
        foreignWriterUsed |= !writer.equals(context.target());
        subTick = 0;
        writerRandom =
                context.randomState()
                        .getOrCreateRandomFactory(
                                Identifier.withDefaultNamespace("worldgen_region_random"))
                        .at(writer.getWorldPosition());
    }

    public record Metrics(
            int scratchChunks,
            int sparseBlocks,
            int extraTerrain,
            int targetBlockWrites,
            int outsideBlockWrites,
            int outsideBlockEntityWrites,
            int outsidePostProcessing,
            boolean denseOracle,
            OreHeightQuery.Metrics airQuery,
            LocalTerrainColumns.Metrics localTerrain,
            List<BlockPos> certifiedAir) {}

    public Metrics metrics() {
        return new Metrics(
                scratch.size() - 1,
                scratch.values().stream()
                        .filter(Sparse.class::isInstance)
                        .map(Sparse.class::cast)
                        .mapToInt(c -> c.states.size())
                        .sum(),
                terrain.extraTerrain(),
                targetBlockWrites,
                outsideBlockWrites,
                outsideBeWrites,
                outsidePostWrites,
                control != null,
                airQueries.metrics(),
                localTerrain.metrics(),
                certifiedAir.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList());
    }

    /** Seal a single audited source-tree evaluation before its scratch state is closed. */
    public TreeContribution contribution(TreeContribution.Key key) {
        ensureOpen();
        if (control != null
                || foreignWriterUsed
                || !writer.equals(context.target())
                || !key.source().equals(writer)
                || key.seed() != context.worldSeed()
                || !key.scope()
                        .dimension()
                        .equals(context.hostLevel().dimension().identifier().toString()))
            throw new IllegalArgumentException("C export requires the live source owner");
        var cells = new ArrayList<TreeContribution.Cell>();
        var blocks = new ArrayList<SavedTick<Block>>();
        var fluids = new ArrayList<SavedTick<Fluid>>();
        var positions = new ArrayList<BlockPos>();
        scratch.values().stream()
                .filter(Sparse.class::isInstance)
                .map(Sparse.class::cast)
                .sorted(
                        Comparator.comparingInt((Sparse c) -> c.getPos().z())
                                .thenComparingInt(c -> c.getPos().x()))
                .forEach(
                        c -> {
                            c.states.entrySet().stream()
                                    .sorted(Comparator.comparingLong(e -> e.getKey().asLong()))
                                    .forEach(
                                            e ->
                                                    cells.add(
                                                            new TreeContribution.Cell(
                                                                    e.getKey(),
                                                                    e.getValue(),
                                                                    Optional.ofNullable(
                                                                            c
                                                                                    .getBlockEntityNbtForSaving(
                                                                                            e
                                                                                                    .getKey(),
                                                                                            context.hostLevel()
                                                                                                    .registryAccess())))));
                            var ticks = c.getTicksForSerialization(0);
                            blocks.addAll(ticks.blocks());
                            fluids.addAll(ticks.fluids());
                            for (int section = 0; section < c.post.length; section++) {
                                if (c.post[section] == null) continue;
                                for (short p : c.post[section])
                                    positions.add(
                                            new BlockPos(
                                                    c.getPos().getMinBlockX() + (p & 15),
                                                    c.getMinY() + section * 16 + ((p >>> 4) & 15),
                                                    c.getPos().getMinBlockZ() + ((p >>> 8) & 15)));
                            }
                        });
        return new TreeContribution(
                key,
                baseline.target().getMinY(),
                baseline.target().getHeight(),
                cells,
                blocks,
                fluids,
                positions);
    }

    @Override
    public void close() {
        airQueries.close();
        localTerrain.close();
        terrain.close();
        closed = true;
    }

    public NeighborOreContributions.Query terrainQuery() {
        return terrain;
    }

    private void ensureOpen() {
        if (closed || GenerationContextRegistry.find(this).orElse(null) != context)
            throw new IllegalStateException("Closed tree A query");
    }

    @Override
    public RandomSource getRandom() {
        ensureOpen();
        return auxiliaryOwner == null ? writerRandom : auxiliaryOwner.getRandom();
    }

    @Override
    public long nextSubTickCount() {
        ensureOpen();
        return auxiliaryOwner == null ? subTick++ : auxiliaryOwner.nextSubTickCount();
    }

    @Override
    public ChunkPos getCenter() {
        return writer;
    }

    // The finite multi-source scratch region has a derived radius-2 read frontier; the native
    // single-writer warning uses the constructor center and would mislabel these legal reads.
    @Override
    public BlockState getBlockState(BlockPos p) {
        return getChunk(Math.floorDiv(p.getX(), 16), Math.floorDiv(p.getZ(), 16)).getBlockState(p);
    }

    @Override
    public FluidState getFluidState(BlockPos p) {
        return getBlockState(p).getFluidState();
    }

    @Override
    public boolean hasChunk(int x, int z) {
        return terrain.hasChunk(x, z);
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        ensureOpen();
        if (!hasChunk(ChunkPos.containing(pos).x(), ChunkPos.containing(pos).z()))
            throw new IllegalArgumentException("Outside tree scratch frontier " + pos);
        return !isOutsideBuildHeight(pos);
    }

    @Override
    public boolean isWithinWriteZone(BlockPos pos) {
        return ensureCanWrite(pos);
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
        ensureOpen();
        if (status.isAfter(ChunkStatus.TERRAIN))
            throw new IllegalArgumentException("Tree A cannot request " + status);
        long pos = ChunkPos.pack(x, z);
        if (!hasChunk(x, z)) throw new IllegalArgumentException("Outside tree read frontier");
        if (status == ChunkStatus.BIOMES) return terrain.getChunk(x, z, status, required);
        return scratch.computeIfAbsent(
                pos,
                p -> {
                    if (control == null)
                        return new Sparse(terrain.getChunk(x, z, ChunkStatus.BIOMES, true));
                    ChunkAccess base = terrain.getChunk(x, z, ChunkStatus.TERRAIN, true);
                    // The probe supplies its full-chunk comparison representation; A itself has
                    // only
                    // sparse state and exact read fallback, not a hidden neighbor decoration
                    // runner.
                    return control.apply(base);
                });
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        if (!Set.of(
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        Heightmap.Types.OCEAN_FLOOR,
                        Heightmap.Types.WORLD_SURFACE)
                .contains(type))
            throw new UnsupportedOperationException("Unaudited tree heightmap " + type);
        return getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)).getHeight(type, x, z) + 1;
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int limit) {
        boolean result = super.setBlock(pos, state, flags, limit);
        if (result) {
            if (ChunkPos.containing(pos).equals(context.target())) targetBlockWrites++;
            else outsideBlockWrites++;
        }
        return result;
    }

    @Override
    public ServerLevel getLevel() {
        throw new UnsupportedOperationException("Tree scratch cannot escape to physical level");
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        throw new UnsupportedOperationException(
                "Audited trees have no standalone entity capability");
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drops, Entity entity, int limit) {
        throw new UnsupportedOperationException("Tree scratch has no drops/destruction capability");
    }

    /** No block sections, no generation holders/tasks, only touched same-seed locations. */
    private final class Sparse extends ChunkAccess {
        private final ChunkAccess base;
        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
        private final Map<BlockPos, CompoundTag> nbt = new HashMap<>();
        private final Map<Long, Integer> runtimeHeights = new HashMap<>();
        private final ProtoChunkTicks<Block> blockTicks = new ProtoChunkTicks<>();
        private final ProtoChunkTicks<Fluid> fluidTicks = new ProtoChunkTicks<>();
        private final ShortList[] post;

        Sparse(ChunkAccess base) {
            super(
                    base.getPos(),
                    UpgradeData.EMPTY,
                    LevelHeightAccessor.create(base.getMinY(), 0),
                    context.hostLevel().palettedContainerFactory(),
                    0,
                    null,
                    null);
            this.base = base;
            post = new ShortList[base.getHeight() / 16];
        }

        private void local(BlockPos p) {
            if (!ChunkPos.containing(p).equals(getPos()))
                throw new IllegalArgumentException("Foreign tree chunk coordinate");
        }

        @Override
        public int getMinY() {
            return base.getMinY();
        }

        @Override
        public int getHeight() {
            return base.getHeight();
        }

        @Override
        public ChunkStatus getPersistedStatus() {
            return ChunkStatus.TERRAIN;
        }

        @Override
        public BlockState getBlockState(BlockPos p) {
            local(p);
            if (isOutsideBuildHeight(p)) return Blocks.VOID_AIR.defaultBlockState();
            var state = states.get(p);
            if (state != null) return state;
            var known = baseline.terrain().get(getPos().pack());
            if (known != null) return known.getBlockState(p);
            if (!isOutsideBuildHeight(p) && airQueries.definitelyAir(p)) {
                certifiedAir.add(p.immutable());
                return Blocks.AIR.defaultBlockState();
            }
            var value = localTerrain.block(p);
            if (value.isPresent()) return value.get();
            return terrain.getChunk(getPos().x(), getPos().z(), ChunkStatus.TERRAIN, true)
                    .getBlockState(p);
        }

        @Override
        public FluidState getFluidState(BlockPos p) {
            return getBlockState(p).getFluidState();
        }

        @Override
        public BlockState setBlockState(BlockPos p, BlockState s, int flags) {
            local(p);
            if (isOutsideBuildHeight(p)) return null;
            BlockState old = getBlockState(p);
            states.put(p.immutable(), s);
            runtimeHeights.clear();
            return old;
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            if (type == Heightmap.Types.OCEAN_FLOOR || type == Heightmap.Types.WORLD_SURFACE) {
                int lx = x & 15, lz = z & 15;
                long key = ((long) type.ordinal() << 8) | lx | (lz << 4);
                return runtimeHeights.computeIfAbsent(
                        key,
                        k -> {
                            var p =
                                    new BlockPos.MutableBlockPos(
                                            getPos().getMinBlockX() + lx,
                                            0,
                                            getPos().getMinBlockZ() + lz);
                            for (int y = getMaxY(); y >= getMinY(); y--)
                                if (type.isOpaque().test(getBlockState(p.setY(y)))) return y;
                            return getMinY() - 1;
                        });
            }
            if (type != Heightmap.Types.OCEAN_FLOOR_WG && type != Heightmap.Types.WORLD_SURFACE_WG)
                throw new UnsupportedOperationException("Unaudited sparse heightmap");
            // Native TERRAIN/FEATURES writes update runtime maps, not retained WG maps. Keep
            // placement's stored pre-FEATURES authority separate from collision/read feedback.
            var known = baseline.terrain().get(getPos().pack());
            if (known != null) return known.getHeight(type, x, z);
            var height =
                    localTerrain.height(
                            type, getPos().getBlockX(x & 15), getPos().getBlockZ(z & 15));
            if (height.isPresent()) return height.getAsInt();
            return terrain.getChunk(getPos().x(), getPos().z(), ChunkStatus.TERRAIN, true)
                    .getHeight(type, x, z);
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z) {
            return base.getNoiseBiome(x, y, z);
        }

        @Override
        public void collectBiomesInPalette(Set<Holder<Biome>> biomes) {
            base.collectBiomesInPalette(biomes);
        }

        @Override
        public LevelChunkSection[] getSections() {
            throw new UnsupportedOperationException("No borrowed mutable tree sections");
        }

        @Override
        public LevelChunkSection getSection(int index) {
            throw new UnsupportedOperationException("No sparse tree sections");
        }

        @Override
        public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types type) {
            throw new UnsupportedOperationException("No borrowed mutable tree heightmap");
        }

        @Override
        public java.util.Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
            throw new UnsupportedOperationException("Use audited tree height queries");
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
            return new PackedTicks(blockTicks.pack(time), fluidTicks.pack(time));
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos p) {
            local(p);
            return entities.get(p);
        }

        @Override
        public CompoundTag getBlockEntityNbt(BlockPos p) {
            local(p);
            var tag = nbt.get(p);
            return tag == null ? null : tag.copy();
        }

        @Override
        public CompoundTag getBlockEntityNbtForSaving(BlockPos p, HolderLookup.Provider registry) {
            local(p);
            var e = entities.get(p);
            return e == null ? getBlockEntityNbt(p) : e.saveWithFullMetadata(registry);
        }

        @Override
        public void setBlockEntity(BlockEntity e) {
            local(e.getBlockPos());
            entities.put(e.getBlockPos().immutable(), e);
            nbt.remove(e.getBlockPos());
            outsideBeWrites++;
        }

        @Override
        public void setBlockEntityNbt(CompoundTag tag) {
            var p = new BlockPos(tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
            local(p);
            nbt.put(p, tag.copy());
            outsideBeWrites++;
        }

        @Override
        public void removeBlockEntity(BlockPos p) {
            local(p);
            entities.remove(p);
            nbt.remove(p);
        }

        @Override
        public void markPosForPostProcessing(BlockPos p) {
            local(p);
            if (isOutsideBuildHeight(p)) return;
            int section = getSectionIndex(p.getY());
            if (post[section] == null) post[section] = new ShortArrayList();
            post[section].add(ProtoChunk.packOffsetCoordinates(p));
            outsidePostWrites++;
        }

        @Override
        public ShortList[] getPostProcessing() {
            return Arrays.stream(post)
                    .map(l -> l == null ? null : new ShortArrayList(l))
                    .toArray(ShortList[]::new);
        }

        @Override
        public void addEntity(Entity e) {
            throw new UnsupportedOperationException("No tree entity registration");
        }
    }
}
