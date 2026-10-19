package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import com.mojang.serialization.JsonOps;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.BiomePaletteChunk;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.heightproviders.*;
import net.minecraft.world.level.levelgen.placement.*;

/**
 * Experimental A subset: B followed by Z/X ordered neighbor ore contributions. No profile dispatch.
 */
public final class NeighborOreContributions {
    private NeighborOreContributions() {}

    public static String unsupported(PlacedFeature placed) {
        if (placed.feature().value().getClass() != OreFeature.class)
            return "unsupported native feature "
                    + placed.feature().value().getClass().getSimpleName();
        var ore = (OreFeature) placed.feature().value();
        try {
            requireSafeOre(ore);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        long squares =
                placed.placement().stream()
                        .filter(p -> p.getClass() == InSquarePlacement.class)
                        .count();
        if (squares != 1) return "requires exactly one bounded InSquarePlacement";
        for (var p : placed.placement())
            if (p.getClass() != CountPlacement.class
                    && p.getClass() != InSquarePlacement.class
                    && p.getClass() != HeightRangePlacement.class
                    && p.getClass() != BiomeFilter.class
                    && p.getClass() != RarityFilter.class)
                return "unaudited modifier " + p.getClass().getSimpleName();
        return null;
    }

    public static void requireSafeOre(OreFeature ore) {
        if (ore.getClass() != OreFeature.class
                || ore.size() < 1
                || ore.size() > 64
                || OreContributionGeometry.reach(ore.size()) > 16)
            throw new IllegalArgumentException("unaudited ore geometry / reach");
        if (ore.discardChanceOnAirExposure() != 0)
            throw new IllegalArgumentException("air exposure consumes state-dependent RNG");
        for (var r : ore.targetStates()) {
            OreRuleSupport.requireSafe(r.target());
            if (r.state().hasBlockEntity())
                throw new IllegalArgumentException("ore replacement has BlockEntity");
        }
    }

    public record Slot(int step, int index, PlacedFeature feature, String key) {}

    public record Metrics(
            int sourceChunks,
            int evaluatedSlots,
            int attempts,
            int intersectingAttempts,
            int voxelWrites,
            int extraTerrain,
            int verticalSkippedSlots,
            OreHeightQuery.Metrics heightQuery,
            LocalTerrainColumns.Metrics localHeightQuery,
            int biomeCarriers,
            Map<String, String> unsupportedFeatures,
            Set<String> supportedFeatures,
            List<String> trace,
            long nanos) {}

    public static Metrics apply(
            IsolatedGenerationContext context, StrictFeatureBaseline.Result baseline) {
        return apply(context, baseline, false);
    }

    /** The exact-height variant is an explicit comparison path, not a persisted generation mode. */
    public static Metrics apply(
            IsolatedGenerationContext context,
            StrictFeatureBaseline.Result baseline,
            boolean referenceHeights) {
        return apply(context, baseline, referenceHeights, false);
    }

    /** Exact local heights are opt-in; the retained reference keeps its full-terrain reads. */
    public static Metrics apply(
            IsolatedGenerationContext context,
            StrictFeatureBaseline.Result baseline,
            boolean referenceHeights,
            boolean localHeights) {
        if (!baseline.target().getPos().equals(context.target())
                || baseline.region().getSeed() != context.worldSeed()
                || GenerationContextRegistry.find(baseline.region()).orElse(null) != context)
            throw new IllegalArgumentException(
                    "A requires the same live B context, target and local seed");
        long began = System.nanoTime();
        try (Query query = new Query(context, baseline, referenceHeights, localHeights)) {
            var steps =
                    FeatureSorter.buildFeaturesPerStep(
                            List.copyOf(context.generator().getBiomeSource().possibleBiomes()),
                            b -> context.generator().getBiomeGenerationSettings(b).features(),
                            true);
            Map<String, String> unsupported = new TreeMap<>();
            Set<String> supported = new TreeSet<>();
            List<String> trace = new ArrayList<>();
            int slots = 0, attempts = 0, intersects = 0, writes = 0, verticalSkipped = 0;
            int occupiedMax = baseline.target().getMinY() - 1;
            outer:
            for (int y = baseline.target().getMaxY() - 1; y >= baseline.target().getMinY(); y--)
                for (int z = baseline.target().getPos().getMinBlockZ();
                        z <= baseline.target().getPos().getMaxBlockZ();
                        z++)
                    for (int x = baseline.target().getPos().getMinBlockX();
                            x <= baseline.target().getPos().getMaxBlockX();
                            x++)
                        if (!baseline.target().getBlockState(new BlockPos(x, y, z)).isAir()) {
                            occupiedMax = y;
                            break outer;
                        }
            for (int z = context.target().z() - 1; z <= context.target().z() + 1; z++)
                for (int x = context.target().x() - 1; x <= context.target().x() + 1; x++) {
                    ChunkPos source = new ChunkPos(x, z);
                    if (source.equals(context.target())) continue;
                    BlockPos origin =
                            new BlockPos(
                                    source.getMinBlockX(),
                                    context.hostLevel().getMinY(),
                                    source.getMinBlockZ());
                    WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0));
                    long decoration =
                            random.setDecorationSeed(
                                    context.worldSeed(), origin.getX(), origin.getZ());
                    for (Slot slot : slots(context, query, source, steps)) {
                        String reason = unsupported(slot.feature());
                        if (reason != null) {
                            unsupported.put(slot.key(), reason);
                            continue;
                        }
                        supported.add(slot.key());
                        slots++;
                        random.setFeatureSeed(decoration, slot.index(), slot.step());
                        trace.add(
                                source.x()
                                        + ","
                                        + source.z()
                                        + ":"
                                        + slot.step()
                                        + ":"
                                        + slot.index()
                                        + ":"
                                        + slot.key());
                        // Native FeaturePlacer's depth-first modifier order; do not flatten
                        // streams:
                        // feature RNG consumption is interleaved with modifier expansion.
                        var positions = new ArrayList<BlockPos>();
                        var indices = new ArrayList<Integer>();
                        positions.add(origin);
                        indices.add(0);
                        var placement = slot.feature().placement();
                        var pc =
                                new PlacementContext(
                                        query, context.generator(), Optional.of(slot.feature()));
                        if (verticallyIrrelevant(slot.feature(), pc, occupiedMax)) {
                            verticalSkipped++;
                            continue;
                        }
                        while (!positions.isEmpty()) {
                            BlockPos position = positions.removeLast();
                            int i = indices.removeLast();
                            var modified = new ArrayList<BlockPos>();
                            placement
                                    .get(i)
                                    .modify(pc, random, position, p -> modified.add(p.immutable()));
                            if (i + 1 < placement.size()) {
                                for (int j = modified.size() - 1; j >= 0; j--) {
                                    positions.add(modified.get(j));
                                    indices.add(i + 1);
                                }
                            } else
                                for (BlockPos p : modified) {
                                    attempts++;
                                    var evaluation =
                                            OreContributionGeometry.project(
                                                    (OreFeature) slot.feature().feature().value(),
                                                    random,
                                                    p,
                                                    baseline.target(),
                                                    query::heightGate);
                                    if (evaluation.intersects()) intersects++;
                                    writes += evaluation.writes();
                                }
                        }
                    }
                }
            return new Metrics(
                    8,
                    slots,
                    attempts,
                    intersects,
                    writes,
                    query.extraTerrain(),
                    verticalSkipped,
                    query.heightMetrics(),
                    query.localHeightMetrics(),
                    query.palettes.size() - baseline.terrain().size(),
                    Map.copyOf(unsupported),
                    Set.copyOf(supported),
                    List.copyOf(trace),
                    System.nanoTime() - began);
        }
    }

    static boolean verticallyIrrelevant(
            PlacedFeature feature, PlacementContext context, int occupiedMax) {
        HeightProvider provider = null;
        for (var modifier : feature.placement())
            if (modifier instanceof HeightRangePlacement range) provider = range.height();
        if (provider == null) return false;
        int low;
        if (provider.getClass() == ConstantHeight.class)
            low = ((ConstantHeight) provider).getValue().resolveY(context);
        else if (provider.getClass() == UniformHeight.class
                || provider.getClass() == TrapezoidHeight.class) {
            // Public native codecs preserve private anchor data without reflective field access or
            // duplicating anchor resolution. Unknown providers keep the exact height-query path.
            var encoded =
                    HeightProvider.CODEC
                            .encodeStart(JsonOps.INSTANCE, provider)
                            .getOrThrow()
                            .getAsJsonObject();
            low =
                    VerticalAnchor.CODEC
                            .parse(JsonOps.INSTANCE, encoded.get("min_inclusive"))
                            .getOrThrow()
                            .resolveY(context);
            int high =
                    VerticalAnchor.CODEC
                            .parse(JsonOps.INSTANCE, encoded.get("max_inclusive"))
                            .getOrThrow()
                            .resolveY(context);
            if (low > high) return false;
        } else return false;
        int r =
                net.minecraft.util.Mth.ceil(
                        (((OreFeature) feature.feature().value()).size() / 16.0f * 2.0f + 1.0f)
                                / 2.0f);
        return (long) low - 2 - r > occupiedMax;
    }

    static List<Slot> slots(
            IsolatedGenerationContext context,
            Query query,
            ChunkPos source,
            List<FeatureSorter.StepFeatureData> steps) {
        Set<Holder<Biome>> possible = new HashSet<>();
        for (int z = source.z() - 1; z <= source.z() + 1; z++)
            for (int x = source.x() - 1; x <= source.x() + 1; x++)
                query.palette(new ChunkPos(x, z)).collectBiomesInPalette(possible);
        possible.retainAll(context.generator().getBiomeSource().possibleBiomes());
        var registry =
                context.hostLevel().registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        List<Slot> slots = new ArrayList<>();
        for (int step = 0; step < steps.size(); step++) {
            var sorted = new TreeSet<Integer>();
            for (var biome : possible) {
                var features = context.generator().getBiomeGenerationSettings(biome).features();
                if (step < features.size())
                    for (var feature : features.get(step))
                        sorted.add(steps.get(step).indexMapping().applyAsInt(feature.value()));
            }
            for (int index : sorted) {
                var feature = steps.get(step).features().get(index);
                String key = registry.getResourceKey(feature).orElseThrow().identifier().toString();
                slots.add(new Slot(step, index, feature, key));
            }
        }
        return List.copyOf(slots);
    }

    /**
     * Stored palettes and exact, on-demand height fallback; never default/fake neighboring terrain.
     */
    public static final class Query extends WorldGenRegion implements AutoCloseable {
        private final IsolatedGenerationContext context;
        private final Map<Long, ChunkAccess> palettes = new HashMap<>();
        private final Map<Long, ProtoChunk> terrain = new HashMap<>();
        private final Map<Long, StrictFeatureChunk> views = new HashMap<>();
        private int extra;
        private final OreHeightQuery heights;
        private final boolean referenceHeights;
        private final LocalTerrainColumns localHeights;
        private boolean closed;

        public Query(IsolatedGenerationContext context, StrictFeatureBaseline.Result baseline) {
            this(context, baseline, false);
        }

        public Query(
                IsolatedGenerationContext context,
                StrictFeatureBaseline.Result baseline,
                boolean referenceHeights) {
            this(context, baseline, referenceHeights, false);
        }

        public Query(
                IsolatedGenerationContext context,
                StrictFeatureBaseline.Result baseline,
                boolean referenceHeights,
                boolean localHeights) {
            this(
                    context,
                    baseline,
                    referenceHeights,
                    localHeights,
                    StaticCache2D.create(
                            context.target().x(),
                            context.target().z(),
                            0,
                            (x, z) -> StrictFeatureRegion.constructorCarrier(baseline.target())));
        }

        private Query(
                IsolatedGenerationContext context,
                StrictFeatureBaseline.Result baseline,
                boolean referenceHeights,
                boolean localHeights,
                StaticCache2D<GenerationChunkHolder> cache) {
            super(
                    context.hostLevel(),
                    bind(cache, context),
                    ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES),
                    baseline.target());
            this.context = context;
            this.referenceHeights = referenceHeights;
            terrain.putAll(baseline.terrain());
            palettes.putAll(terrain);
            heights = new OreHeightQuery(context, this::heightPalette);
            this.localHeights =
                    localHeights ? new LocalTerrainColumns(context, this::heightPalette) : null;
            GenerationContextRegistry.bind(this, context);
        }

        private static StaticCache2D<GenerationChunkHolder> bind(
                StaticCache2D<GenerationChunkHolder> cache, IsolatedGenerationContext c) {
            GenerationContextRegistry.bind(cache, c);
            return cache;
        }

        private ChunkAccess palette(ChunkPos p) {
            requireFinite(p);
            return loadPalette(p);
        }

        ChunkAccess heightPalette(ChunkPos p) {
            requireOpen();
            if (Math.abs((long) p.x() - context.target().x()) > 3
                    || Math.abs((long) p.z() - context.target().z()) > 3)
                throw new IllegalArgumentException("Outside A height biome halo " + p);
            return loadPalette(p);
        }

        private ChunkAccess loadPalette(ChunkPos p) {
            return palettes.computeIfAbsent(
                    p.pack(),
                    k -> {
                        var chunk =
                                new BiomePaletteChunk(
                                        p,
                                        context.hostLevel(),
                                        context.hostLevel().palettedContainerFactory());
                        context.generator()
                                .createBiomes(
                                        context.randomState(),
                                        Blender.empty(),
                                        context.structureManager(),
                                        chunk)
                                .join();
                        return chunk;
                    });
        }

        private void requireFinite(ChunkPos p) {
            requireOpen();
            if (Math.abs((long) p.x() - context.target().x()) > 2
                    || Math.abs((long) p.z() - context.target().z()) > 2)
                throw new IllegalArgumentException("Outside finite A ore query frontier " + p);
        }

        private void requireOpen() {
            if (closed || GenerationContextRegistry.find(this).orElse(null) != context)
                throw new IllegalStateException("Closed A contribution query/owner");
        }

        public int extraTerrain() {
            return extra;
        }

        public OreHeightQuery.Metrics heightMetrics() {
            return heights.metrics();
        }

        public boolean heightGate(int xStart, int zStart, int sizeXZ, int yStart) {
            requireFinite(ChunkPos.containing(new BlockPos(xStart, 0, zStart)));
            requireFinite(ChunkPos.containing(new BlockPos(xStart + sizeXZ, 0, zStart + sizeXZ)));
            // The native gate is an existential, RNG-free height test. A known passing column
            // proves it without fetching other columns. If no known column passes, evaluate the
            // missing exact columns as well; never infer their values from a base-noise sampler.
            for (int x = xStart; x <= xStart + sizeXZ; x++)
                for (int z = zStart; z <= zStart + sizeXZ; z++) {
                    var c = terrain.get(ChunkPos.containing(new BlockPos(x, 0, z)).pack());
                    if (c != null
                            && yStart <= c.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) + 1)
                        return true;
                }
            for (int x = xStart; x <= xStart + sizeXZ; x++)
                for (int z = zStart; z <= zStart + sizeXZ; z++) {
                    var known = terrain.get(ChunkPos.containing(new BlockPos(x, 0, z)).pack());
                    if (known != null) {
                        if (yStart <= known.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) + 1)
                            return true;
                        continue;
                    }
                    if (!referenceHeights && heights.definitelyBelow(x, z, yStart)) continue;
                    if (yStart <= oceanFloor(x, z)) return true;
                }
            return false;
        }

        public int oceanFloor(int x, int z) {
            ChunkPos p = ChunkPos.containing(new BlockPos(x, 0, z));
            requireFinite(p);
            var known = terrain.get(p.pack());
            if (known != null) return known.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) + 1;
            if (localHeights != null) {
                var h = localHeights.height(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
                if (h.isPresent()) return h.getAsInt() + 1;
            }
            return exactTerrain(ChunkPos.containing(new BlockPos(x, 0, z)))
                            .getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z)
                    + 1;
        }

        public LocalTerrainColumns.Metrics localHeightMetrics() {
            return localHeights == null ? null : localHeights.metrics();
        }

        @Override
        public void close() {
            if (closed) return;
            heights.close();
            if (localHeights != null) localHeights.close();
            closed = true;
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            if (type != Heightmap.Types.OCEAN_FLOOR_WG)
                throw new UnsupportedOperationException("Unaudited A ore height query " + type);
            // This compound query serves eight source positions, not one native writer. Its read
            // frontier is radius 2; using the inherited single-writer warning would misreport legal
            // source reads. Keep the exact native ChunkAccess height + 1 and fail outside this
            // scope.
            return oceanFloor(x, z);
        }

        private ProtoChunk exactTerrain(ChunkPos p) {
            requireFinite(p);
            return terrain.computeIfAbsent(
                    p.pack(),
                    k -> {
                        extra++;
                        try (var c =
                                IsolatedGenerationContext.create(
                                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                                        context.hostLevel(),
                                        context.worldSeed(),
                                        p)) {
                            return LightweightTerrainGenerator.generate(
                                            c, CompactStructureInputs.resolve(c), (s, v) -> {})
                                    .target();
                        }
                    });
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
            ChunkPos p = new ChunkPos(x, z);
            requireFinite(p);
            if (status.isAfter(ChunkStatus.TERRAIN))
                throw new IllegalArgumentException("A read cannot request " + status);
            if (status == ChunkStatus.BIOMES && !terrain.containsKey(p.pack())) return palette(p);
            return views.computeIfAbsent(
                    p.pack(),
                    k ->
                            new StrictFeatureChunk(
                                    exactTerrain(p),
                                    context.target(),
                                    context.hostLevel().palettedContainerFactory(),
                                    api -> {},
                                    context.hostLevel().registryAccess()));
        }

        @Override
        public boolean hasChunk(int x, int z) {
            requireOpen();
            return Math.abs((long) x - context.target().x()) <= 2
                    && Math.abs((long) z - context.target().z()) <= 2;
        }

        @Override
        public boolean ensureCanWrite(BlockPos pos) {
            return ChunkPos.containing(pos).equals(context.target());
        }

        @Override
        public boolean isWithinWriteZone(BlockPos pos) {
            return ensureCanWrite(pos);
        }

        @Override
        public boolean addFreshEntity(net.minecraft.world.entity.Entity entity) {
            throw new UnsupportedOperationException("Ore query has no entity capability");
        }

        @Override
        public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block>
                getBlockTicks() {
            throw new UnsupportedOperationException("Ore query has no block tick capability");
        }

        @Override
        public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid>
                getFluidTicks() {
            throw new UnsupportedOperationException("Ore query has no fluid tick capability");
        }

        @Override
        public net.minecraft.server.level.ServerLevel getLevel() {
            throw new UnsupportedOperationException(
                    "A ore query cannot escape into physical level");
        }
    }
}
