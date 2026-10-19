package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.TerrainBiomeInputs;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.material.*;
import net.minecraft.world.level.levelgen.material.condition.*;
import net.minecraft.world.level.levelgen.material.rule.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.ticks.TickContainerAccess;

/** Experimental exact TERRAIN columns, not a production/save authority or a base-noise proxy. */
public final class LocalTerrainColumns implements AutoCloseable {
    private final IsolatedGenerationContext context;
    private final Function<ChunkPos, ChunkAccess> palettes;
    private final ScopedStructureSources structureSources;
    private final TerrainBiomeInputs retained;
    private final BiomeManager biomes;
    private final Map<Long, Batch> batches = new HashMap<>();
    private final Map<Long, Column> columns = new HashMap<>();
    private final Map<Long, String> rejected = new TreeMap<>();
    private final Set<BlockPos> reads = new HashSet<>();
    private int blockReads;
    private int proofColumns;
    private long rawSamples;
    private int heightOnlyQueries;
    private int heightCacheHits;
    private final boolean captureProofs;
    private long nanos;
    private boolean closed;
    private boolean densityInputsResolved;
    private LocalDensityInputs densityInputs;

    public LocalTerrainColumns(
            IsolatedGenerationContext context, Function<ChunkPos, ChunkAccess> palettes) {
        this(context, palettes, null);
    }

    LocalTerrainColumns(
            IsolatedGenerationContext context,
            Function<ChunkPos, ChunkAccess> palettes,
            ScopedStructureSources structureSources) {
        this(context, palettes, structureSources, true);
    }

    LocalTerrainColumns(
            IsolatedGenerationContext context,
            Function<ChunkPos, ChunkAccess> palettes,
            ScopedStructureSources structureSources,
            boolean captureProofs) {
        this(context, palettes, structureSources, captureProofs, null);
    }

    LocalTerrainColumns(
            IsolatedGenerationContext context,
            Function<ChunkPos, ChunkAccess> palettes,
            ScopedStructureSources structureSources,
            boolean captureProofs,
            TerrainBiomeInputs retained) {
        this.context = context;
        this.palettes = palettes;
        this.structureSources = structureSources;
        this.retained = retained;
        this.captureProofs = captureProofs;
        biomes =
                new BiomeManager(
                        (x, y, z) ->
                                palettes.apply(
                                                new ChunkPos(
                                                        QuartPos.toSection(x),
                                                        QuartPos.toSection(z)))
                                        .getNoiseBiome(x, y, z),
                        BiomeManager.obfuscateSeed(context.worldSeed()));
    }

    public record ColumnProof(
            int x,
            int z,
            int minY,
            List<String> states,
            int surfaceWg,
            int floorWg,
            int noiseSurface,
            int noiseFloor,
            String densityHash) {}

    public record Metrics(
            int noiseBatches,
            int columns,
            int blockReads,
            int uniqueReads,
            long nanos,
            Map<Long, String> fallbacks,
            List<ColumnProof> evidence) {}

    public record Counters(
            int noiseBatches,
            int columns,
            int blockReads,
            int uniqueReads,
            long nanos,
            Map<Long, String> fallbacks,
            int proofColumns,
            long rawSamples,
            int heightOnlyQueries,
            int heightCacheHits,
            int materialBatches,
            long densitySamples,
            int densityTiles,
            long densitySourceSamples) {}

    public Counters counters() {
        return new Counters(
                (int) batches.values().stream().filter(b -> b.noise != null).count(),
                columns.size(),
                blockReads,
                reads.size(),
                nanos,
                Map.copyOf(rejected),
                proofColumns,
                rawSamples,
                heightOnlyQueries,
                heightCacheHits,
                batches.values().stream().mapToInt(b -> b.materials.size()).sum(),
                batches.values().stream()
                        .filter(b -> b.densities != null)
                        .mapToLong(b -> b.densities.samples())
                        .sum(),
                batches.values().stream()
                        .filter(b -> b.densities != null)
                        .mapToInt(b -> b.densities.tiles())
                        .sum(),
                batches.values().stream()
                        .filter(b -> b.densities != null)
                        .mapToLong(b -> b.densities.sourceSamples())
                        .sum());
    }

    /** Explicit validation evidence; the physical handoff uses counters instead. */
    public Metrics metrics() {
        if (!captureProofs)
            throw new IllegalStateException("Column proofs were not requested for this query");
        return new Metrics(
                (int) batches.values().stream().filter(b -> b.noise != null).count(),
                columns.size(),
                blockReads,
                reads.size(),
                nanos,
                Map.copyOf(rejected),
                columns.values().stream()
                        .sorted(
                                Comparator.comparingInt((Column c) -> c.z)
                                        .thenComparingInt(c -> c.x))
                        .map(
                                c ->
                                        new ColumnProof(
                                                c.x,
                                                c.z,
                                                c.getMinY(),
                                                Arrays.stream(c.states)
                                                        .map(Object::toString)
                                                        .toList(),
                                                c.getHeight(
                                                                Heightmap.Types.WORLD_SURFACE_WG,
                                                                c.x,
                                                                c.z)
                                                        + 1,
                                                c.getHeight(
                                                                Heightmap.Types.OCEAN_FLOOR_WG,
                                                                c.x,
                                                                c.z)
                                                        + 1,
                                                c.batch.surface[(c.x & 15) + (c.z & 15) * 16],
                                                c.batch.floor[(c.x & 15) + (c.z & 15) * 16],
                                                c.densityHash))
                        .toList());
    }

    public Optional<BlockState> block(BlockPos p) {
        var c = column(p.getX(), p.getZ());
        if (c == null) return Optional.empty();
        blockReads++;
        reads.add(p.immutable());
        return Optional.of(c.getBlockState(p));
    }

    public OptionalInt height(Heightmap.Types type, int x, int z) {
        if (!Set.of(
                        Heightmap.Types.WORLD_SURFACE_WG,
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        Heightmap.Types.WORLD_SURFACE,
                        Heightmap.Types.OCEAN_FLOOR)
                .contains(type))
            throw new UnsupportedOperationException("Unaudited column heightmap " + type);
        var pos = open(x, z);
        long key = BlockPos.asLong(x, 0, z);
        var known = columns.get(key);
        if (known != null) {
            heightCacheHits++;
            return OptionalInt.of(known.getHeight(type, x, z));
        }
        {
            long start = System.nanoTime();
            try {
                var batch = batch(pos, key);
                if (batch == null) return OptionalInt.empty();
                boolean surfaceHeight =
                        type == Heightmap.Types.WORLD_SURFACE_WG
                                || type == Heightmap.Types.WORLD_SURFACE;
                batch.height(x & 15, z & 15);
                int height =
                        (surfaceHeight ? batch.surface : batch.floor)[(x & 15) + (z & 15) * 16] - 1;
                // Audited material outputs preserve non-air height. A mask at/above that cell
                // may remove it or introduce fluid above it, so only a proven empty upper mask
                // permits the noise height to answer TERRAIN without building a material column.
                batch.mask();
                if ((surfaceHeight || batch.floorStable)
                        && batch.carveTop[(x & 15) + (z & 15) * 16] < height) {
                    heightOnlyQueries++;
                    return OptionalInt.of(height);
                }
            } finally {
                nanos += System.nanoTime() - start;
            }
        }
        var c = column(x, z);
        return c == null ? OptionalInt.empty() : OptionalInt.of(c.getHeight(type, x, z));
    }

    private ChunkPos open(int x, int z) {
        if (closed
                || GenerationContextRegistry.find(context.worldGenContext()).orElse(null)
                        != context)
            throw new IllegalStateException("Closed local terrain query/owner");
        ChunkPos pos = ChunkPos.containing(new BlockPos(x, 0, z));
        if (Math.abs((long) pos.x() - context.target().x()) > 2
                || Math.abs((long) pos.z() - context.target().z()) > 2)
            throw new IllegalArgumentException("Outside local column frontier " + pos);
        return pos;
    }

    private Batch batch(ChunkPos pos, long key) {
        if (rejected.containsKey(key)) return null;
        String reason = null;
        if (!solid(context.generator().generatorSettings().value().defaultBlock())
                || context.generator()
                                .generatorSettings()
                                .value()
                                .noiseSettings()
                                .clampToHeightAccessor(context.hostLevel())
                                .height()
                        <= 0) reason = "unaudited default block/empty density range";
        if (SharedConstants.DEBUG_AQUIFERS
                || SharedConstants.DEBUG_DISABLE_SURFACE
                || SharedConstants.DEBUG_DISABLE_CARVERS
                || SharedConstants.debugVoidTerrain(pos)) reason = "debug terrain configuration";
        if (reason != null) {
            rejected.put(key, reason);
            return null;
        }
        var batch = batches.computeIfAbsent(pos.pack(), p -> new Batch(pos));
        if (batch.unsupported != null) {
            rejected.put(key, batch.unsupported);
            return null;
        }
        return batch;
    }

    private Column column(int x, int z) {
        var pos = open(x, z);
        long key = BlockPos.asLong(x, 0, z);
        if (rejected.containsKey(key)) return null;
        if (columns.containsKey(key)) return columns.get(key);
        long start = System.nanoTime();
        try {
            var batch = batch(pos, key);
            if (batch == null) return null;
            var column = new Column(batch, x, z);
            batch.surface(column);
            final boolean[] topRepair = {false};
            batch.mask()
                    .visit(
                            (lx, lz, bottom, top) -> {
                                if (lx != (x & 15) || lz != (z & 15)) return;
                                for (int y = top; y >= bottom; y--) {
                                    var s = column.getBlockState(new BlockPos(x, y, z));
                                    if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.MYCELIUM))
                                        topRepair[0] = true;
                                }
                            });
            // Native topMaterial observes WG gradients modified by earlier carved columns. Until
            // that causal halo is proven, return an explicit exact full-terrain fallback.
            if (topRepair[0]) {
                rejected.put(key, "carved grass/mycelium needs prior-column topMaterial gradients");
                return null;
            }
            ((NoiseBasedChunkGeneratorAccessor) (Object) context.generator())
                    .randomnibble6plus24generator$applyColumnMask(
                            column,
                            batch.mask(),
                            context.randomState(),
                            batch.rule,
                            batch.generation,
                            batch.noise,
                            biomes.withDifferentSource(
                                            context.generator()
                                                    .getBiomeSource()
                                                    .createUncachedResolver(context.randomState()))
                                    ::getBiome,
                            (lx, y, lz) -> lx == (x & 15) && lz == (z & 15));
            column.finishHeights();
            columns.put(key, column);
            return column;
        } finally {
            nanos += System.nanoTime() - start;
        }
    }

    /** A closed capability audit, not a permissive codec-name guess for third-party rules. */
    public static String unsupportedRule(MaterialRule rule, int depth) {
        return unsupportedRule(rule, depth, null);
    }

    public static String unsupportedRule(
            MaterialRule rule, int depth, Set<Holder<Biome>> possible) {
        if (depth > 64) return "cyclic/deep material rule";
        if (rule instanceof MaterialRule.HolderHolder h)
            return unsupportedRule(h.holder().value(), depth + 1, possible);
        if (rule instanceof BlockRule b)
            return heightStable(b.resultState())
                    ? null
                    : "height-changing/BE material output " + b.resultState();
        if (rule instanceof SequenceRule s) {
            for (var r : s.sequence()) {
                var reason = unsupportedRule(r, depth + 1, possible);
                if (reason != null) return reason;
            }
            return null;
        }
        if (rule instanceof ConditionRule c) {
            MaterialCondition conditionValue = c.ifTrue();
            int unwrapped = depth;
            while (conditionValue instanceof MaterialCondition.HolderHolder h && unwrapped++ < 64)
                conditionValue = h.holder().value();
            // Same proven impossible-biome branch as native compile(possibleBiomes), not a
            // direct uncached biome sample or probabilistic rejection of a material rule.
            if (possible != null
                    && conditionValue instanceof BiomeCondition b
                    && possible.stream().noneMatch(b.biomes()::contains)) return null;
            String condition = unsupportedCondition(c.ifTrue(), depth + 1);
            return condition == null
                    ? unsupportedRule(c.thenRun(), depth + 1, possible)
                    : condition;
        }
        if (rule instanceof BandlandsRule) return null;
        if (rule instanceof OreVeinRule o)
            return heightStable(o.oreBlock())
                            && heightStable(o.rawOreBlock())
                            && heightStable(o.fillerBlock())
                    ? null
                    : "height-changing ore vein output";
        return "unaudited material rule " + rule.getClass().getName();
    }

    private static boolean solid(BlockState s) {
        return !s.hasBlockEntity()
                && Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(s)
                && !s.isAir()
                && s.getFluidState().isEmpty();
    }

    private static boolean heightStable(BlockState s) {
        // Cross-column gradients read WORLD_SURFACE_WG, not OCEAN_FLOOR_WG. A native liquid or
        // powder snow replacement keeps that surface height; the column's floor is recomputed.
        return !s.hasBlockEntity()
                && !s.isAir()
                && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(s);
    }

    /** Only used after the closed material capability audit has passed. */
    static boolean preservesFloor(MaterialRule rule, Set<Holder<Biome>> possible, int depth) {
        if (depth > 64) return false;
        if (rule instanceof MaterialRule.HolderHolder h)
            return preservesFloor(h.holder().value(), possible, depth + 1);
        if (rule instanceof BlockRule b) return solid(b.resultState());
        if (rule instanceof BandlandsRule)
            return List.of(
                            Blocks.TERRACOTTA,
                            Blocks.DYED_TERRACOTTA.white(),
                            Blocks.DYED_TERRACOTTA.orange(),
                            Blocks.DYED_TERRACOTTA.yellow(),
                            Blocks.DYED_TERRACOTTA.brown(),
                            Blocks.DYED_TERRACOTTA.red(),
                            Blocks.DYED_TERRACOTTA.lightGray())
                    .stream()
                    .allMatch(b -> solid(b.defaultBlockState()));
        if (rule instanceof OreVeinRule o)
            return solid(o.oreBlock()) && solid(o.rawOreBlock()) && solid(o.fillerBlock());
        if (rule instanceof SequenceRule s)
            return s.sequence().stream().allMatch(r -> preservesFloor(r, possible, depth + 1));
        if (rule instanceof ConditionRule c) {
            var condition = c.ifTrue();
            int nesting = depth;
            while (condition instanceof MaterialCondition.HolderHolder h && nesting++ < 64)
                condition = h.holder().value();
            if (condition instanceof BiomeCondition b
                    && possible.stream().noneMatch(b.biomes()::contains)) return true;
            return preservesFloor(c.thenRun(), possible, depth + 1);
        }
        return false;
    }

    private static String unsupportedCondition(MaterialCondition c, int depth) {
        if (depth > 64) return "cyclic/deep material condition";
        if (c instanceof MaterialCondition.HolderHolder h)
            return unsupportedCondition(h.holder().value(), depth + 1);
        if (c instanceof NotCondition n) return unsupportedCondition(n.target(), depth + 1);
        return Set.of(
                                BiomeCondition.class,
                                NoiseThresholdCondition.class,
                                YCondition.class,
                                WaterCondition.class,
                                TemperatureCondition.class,
                                VerticalGradientCondition.class,
                                AbovePreliminarySurfaceCondition.class,
                                StoneDepthCondition.class,
                                HoleCondition.class,
                                SteepCondition.class)
                        .contains(c.getClass())
                ? null
                : "unaudited material condition " + c.getClass().getName();
    }

    private static boolean supportsMaterialTiles(
            MaterialRule r, Set<Holder<Biome>> possible, int minY, int height) {
        if (r instanceof MaterialRule.HolderHolder h)
            return supportsMaterialTiles(h.holder().value(), possible, minY, height);
        if (r instanceof SequenceRule s)
            return s.sequence().stream()
                    .allMatch(c -> supportsMaterialTiles(c, possible, minY, height));
        if (r instanceof ConditionRule c) {
            MaterialCondition condition = c.ifTrue();
            while (condition instanceof MaterialCondition.HolderHolder h)
                condition = h.holder().value();
            if (condition instanceof BiomeCondition b
                    && possible.stream().noneMatch(b.biomes()::contains)) return true;
            return supportsMaterialTiles(c.thenRun(), possible, minY, height);
        }
        if (r instanceof OreVeinRule o)
            return LocalDensityTiles.tileHeight(o.density(), minY, height) > 0
                    && LocalDensityTiles.tileHeight(o.richness(), minY, height) > 0
                    && LocalDensityTiles.tileHeight(o.fillerGap(), minY, height) > 0;
        return r instanceof BlockRule || r instanceof BandlandsRule;
    }

    private static void materialDensities(
            MaterialRule r, List<DensityFunction> output, List<DensityFunction> scalar) {
        if (r instanceof MaterialRule.HolderHolder h)
            materialDensities(h.holder().value(), output, scalar);
        else if (r instanceof SequenceRule s)
            s.sequence().forEach(c -> materialDensities(c, output, scalar));
        else if (r instanceof ConditionRule c) materialDensities(c.thenRun(), output, scalar);
        else if (r instanceof OreVeinRule o) {
            output.add(o.density());
            output.add(o.richness());
            output.add(o.fillerGap());
            scalar.add(o.fillerGap());
        }
    }

    private static String densityHash(Batch batch, int x, int z) {
        var bytes = ByteBuffer.allocate(batch.volume.sizeY() * Integer.BYTES);
        for (int y = 0; y < batch.volume.sizeY(); y++)
            bytes.putInt(Float.floatToRawIntBits(batch.densities.get(x & 15, y, z & 15)));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        batches.values().forEach(Batch::close);
        closed = true;
    }

    private final class Batch implements AutoCloseable {
        final ChunkPos pos;
        final NoiseChunk noise;
        final LocalDensityTiles densities;
        final DensityVolume volume;
        final int[] surface = new int[256], floor = new int[256];
        final WorldGenerationContext generation;
        final MaterialRule rule;
        final Set<Holder<Biome>> possible = new HashSet<>();
        final Map<Long, Material> materials = new HashMap<>();
        final String unsupported;
        final boolean floorStable;
        CarvingMask mask;
        final int[] carveTop = new int[256];

        Batch(ChunkPos pos) {
            this.pos = pos;
            var settings = context.generator().generatorSettings().value();
            var range = settings.noiseSettings().clampToHeightAccessor(context.hostLevel());
            generation =
                    retained == null
                            ? new WorldGenerationContext(context.generator(), context.hostLevel())
                            : retained.generation(context);
            rule = settings.materialRule().value();
            if (retained != null) possible.addAll(retained.prepare(context, pos).possible());
            else
                for (int z = pos.z() - 1; z <= pos.z() + 1; z++)
                    for (int x = pos.x() - 1; x <= pos.x() + 1; x++)
                        palettes.apply(new ChunkPos(x, z)).collectBiomesInPalette(possible);
            String materialUnsupported =
                    possible.stream()
                                    .anyMatch(
                                            b ->
                                                    b.is(Biomes.ERODED_BADLANDS)
                                                            || b.is(Biomes.FROZEN_OCEAN)
                                                            || b.is(Biomes.DEEP_FROZEN_OCEAN))
                            ? "badlands/iceberg extension needs earlier-column WG feedback"
                            : unsupportedRule(rule, 0, possible);
            int tileHeight =
                    LocalDensityTiles.tileHeight(
                            settings.noiseRouter().finalDensity(), range.minY(), range.height());
            var preliminary = context.randomState().surfaceSystem().preliminarySurfaceFunction();
            var scalarConsumers = new ArrayList<DensityFunction>();
            scalarConsumers.add(preliminary);
            boolean ancillarySupported =
                    LocalDensityTiles.tileHeight(preliminary, range.minY(), range.height()) > 0;
            if (settings.aquifers().isPresent()) {
                var a = settings.aquifers().orElseThrow();
                scalarConsumers.addAll(
                        List.of(
                                a.barrierNoise(),
                                a.fluidLevelFloodednessNoise(),
                                a.fluidLevelSpreadNoise(),
                                a.lavaNoise(),
                                a.exclusion(),
                                a.surfaceLevel()));
                ancillarySupported &=
                        scalarConsumers.stream()
                                .allMatch(
                                        f ->
                                                LocalDensityTiles.tileHeight(
                                                                f, range.minY(), range.height())
                                                        > 0);
            }
            var materialInputs = new ArrayList<DensityFunction>();
            if (materialUnsupported == null)
                materialDensities(rule, materialInputs, scalarConsumers);
            ancillarySupported &=
                    materialInputs.stream()
                            .allMatch(
                                    f ->
                                            LocalDensityTiles.tileHeight(
                                                            f, range.minY(), range.height())
                                                    > 0);
            var nativeVolume =
                    new DensityVolume(
                            16,
                            range.height(),
                            16,
                            pos.getMinBlockX(),
                            range.minY(),
                            pos.getMinBlockZ());
            if (!densityInputsResolved
                    && materialUnsupported == null
                    && tileHeight > 0
                    && ancillarySupported) {
                densityInputs =
                        LocalDensityInputs.resolve(
                                settings.noiseRouter().finalDensity(),
                                nativeVolume,
                                scalarConsumers,
                                materialInputs,
                                preliminary);
                densityInputsResolved = true;
            }
            var cacheInputs = densityInputs;
            unsupported =
                    materialUnsupported != null
                            ? materialUnsupported
                            : tileHeight == 0
                                    ? LocalDensityTiles.unsupported(
                                            settings.noiseRouter().finalDensity(),
                                            range.minY(),
                                            range.height())
                                    : !ancillarySupported
                                                    || !supportsMaterialTiles(
                                                            rule,
                                                            possible,
                                                            range.minY(),
                                                            range.height())
                                            ? "unaudited/unaligned material density graph"
                                            : cacheInputs == null
                                                    ? "unproven density/scalar cache dependency"
                                                    : null;
            floorStable = unsupported == null && preservesFloor(rule, possible, 0);
            if (unsupported != null) {
                volume = null;
                noise = null;
                densities = null;
                return;
            }
            Beardifier beard;
            try (var child = context.createRelated(pos)) {
                beard =
                        Beardifier.forStructuresInChunk(
                                CompactStructureInputs.resolve(child, structureSources).manager(),
                                pos);
            }
            volume = nativeVolume;
            noise =
                    new NoiseChunk(
                            context.randomState(),
                            beard,
                            settings,
                            ((NoiseBasedChunkGeneratorAccessor) (Object) context.generator())
                                    .randomnibble6plus24generator$globalFluidPicker()
                                    .get(),
                            Blender.empty(),
                            volume);
            try {
                cacheInputs.prepare(
                        noise.cachingSamplers(), pos.getMinBlockX(), pos.getMinBlockZ());
                densities =
                        LocalDensityTiles.open(
                                volume,
                                context.randomState(),
                                beard,
                                settings.noiseRouter().finalDensity(),
                                tileHeight);
            } catch (RuntimeException | Error failure) {
                noise.close();
                throw failure;
            }
            Arrays.fill(surface, Integer.MIN_VALUE);
            Arrays.fill(floor, Integer.MIN_VALUE);
        }

        void height(int x, int z) {
            int i = x + z * 16;
            if (surface[i] != Integer.MIN_VALUE) return;
            int top = context.hostLevel().getMinY(), bottom = top;
            boolean foundTop = false, foundBottom = false;
            for (int y = volume.sizeY() - 1; y >= 0; y--) {
                var state = raw(x, y, z);
                if (!foundTop && !state.isAir()) {
                    top = volume.blockY(y) + 1;
                    foundTop = true;
                }
                if (!foundBottom && Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)) {
                    bottom = volume.blockY(y) + 1;
                    foundBottom = true;
                }
                if (foundTop && foundBottom) break;
            }
            surface[i] = top;
            floor[i] = bottom;
        }

        BlockState raw(int x, int y, int z) {
            rawSamples++;
            var s =
                    noise.aquifer()
                            .computeSubstance(
                                    volume.blockX(x),
                                    volume.blockY(y),
                                    volume.blockZ(z),
                                    densities.get(x, y, z));
            return s == null ? context.generator().generatorSettings().value().defaultBlock() : s;
        }

        void surface(Column c) {
            int x = c.x & 15, z = c.z & 15;
            // Audited rules are projective over this native volume. Its original lower Y bound
            // keeps interpolation prefixes; a foreign column or a guessed chunk maximum is not
            // used.
            var materialVolume = LocalDensityTiles.materialVolume(volume, x, z);
            var prepared =
                    materials.computeIfAbsent(
                            BlockPos.asLong(
                                    materialVolume.minBlockX(), 0, materialVolume.minBlockZ()),
                            ignored -> {
                                var m =
                                        MaterialRuleContextInvoker
                                                .randomnibble6plus24generator$create(
                                                        context.randomState().surfaceSystem(),
                                                        context.randomState(),
                                                        materialVolume,
                                                        noise.cachingSamplers(),
                                                        biomes::getBiome,
                                                        generation,
                                                        possible);
                                return new Material(m, rule.compile(m));
                            });
            var material = prepared.context();
            var evaluator = prepared.evaluator();
            height(x, z);
            height(Math.min(x + 1, 15), z);
            height(Math.max(x - 1, 0), z);
            height(x, Math.min(z + 1, 15));
            height(x, Math.max(z - 1, 0));
            var access = (MaterialRuleContextInvoker) (Object) material;
            access.randomnibble6plus24generator$updateXZ(
                    c.x,
                    c.z,
                    surface[Math.min(x + 1, 15) + z * 16] - surface[Math.max(x - 1, 0) + z * 16],
                    surface[x + Math.min(z + 1, 15) * 16] - surface[x + Math.max(z - 1, 0) * 16]);
            int above = 0, water = Integer.MIN_VALUE, next = Integer.MAX_VALUE;
            // Exact 26.3 MaterialSystem column traversal, native rule/compiler and stored biomes.
            for (int y = surface[x + z * 16]; y >= c.getMinY(); y--) {
                var old = c.at(y);
                if (old.isAir()) {
                    above = 0;
                    water = Integer.MIN_VALUE;
                    continue;
                }
                if (!old.getFluidState().isEmpty()) {
                    if (water == Integer.MIN_VALUE) water = y + 1;
                    continue;
                }
                if (next >= y) {
                    next = net.minecraft.world.level.dimension.DimensionType.WAY_BELOW_MIN_Y;
                    for (int look = y - 1; look >= c.getMinY() - 1; look--) {
                        var state = c.at(look);
                        if (!state.isAir() && state.getFluidState().isEmpty()) continue;
                        next = look + 1;
                        break;
                    }
                }
                access.randomnibble6plus24generator$updateY(++above, y - next + 1, water, y);
                if (y < c.getMinY() || y > c.getMaxY()) continue;
                var s = evaluator.tryApply(c.x, y, c.z);
                if (s != null) c.setBlockState(new BlockPos(c.x, y, c.z), s, 0);
            }
        }

        CarvingMask mask() {
            if (mask != null) return mask;
            mask =
                    new CarvingMask(
                            generation.getMinGenY() + 1,
                            generation.getMinGenY() + generation.getGenDepth() - 1 - 7);
            var resolver =
                    context.generator()
                            .getBiomeSource()
                            .createUncachedResolver(context.randomState());
            var random =
                    new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            for (int dx = -8; dx <= 8; dx++)
                for (int dz = -8; dz <= 8; dz++) {
                    var source = new ChunkPos(pos.x() + dx, pos.z() + dz);
                    var biome =
                            resolver.getNoiseBiome(
                                    QuartPos.fromBlock(source.getMinBlockX()),
                                    0,
                                    QuartPos.fromBlock(source.getMinBlockZ()));
                    int index = 0;
                    for (var holder :
                            context.generator().getBiomeGenerationSettings(biome).getCarvers()) {
                        var carver = holder.value();
                        random.setLargeFeatureSeed(
                                context.randomState().seed() + index++, source.x(), source.z());
                        if (carver.isStartChunk(random))
                            carver.carve(generation, random, pos, source, mask);
                    }
                }
            Arrays.fill(carveTop, Integer.MIN_VALUE);
            mask.visit(
                    (x, z, bottom, top) -> {
                        int index = x + z * 16;
                        carveTop[index] = Math.max(carveTop[index], top);
                    });
            return mask;
        }

        @Override
        public void close() {
            if (densities != null) densities.close();
            if (noise != null) noise.close();
        }

        private record Material(MaterialRuleContext context, RuleEvaluator evaluator) {}
    }

    /** One full-depth column, no block sections, holders, tasks, or neighboring TERRAIN status. */
    private final class Column extends ChunkAccess {
        final Batch batch;
        final int x, z;
        final BlockState[] states;
        final String densityHash;
        private int[] heights;

        Column(Batch batch, int x, int z) {
            super(
                    batch.pos,
                    UpgradeData.EMPTY,
                    LevelHeightAccessor.create(0, 0),
                    null,
                    0,
                    null,
                    null);
            this.batch = batch;
            this.x = x;
            this.z = z;
            batch.height(x & 15, z & 15);
            densityHash = captureProofs ? densityHash(batch, x, z) : null;
            if (captureProofs) proofColumns++;
            states = new BlockState[context.hostLevel().getHeight()];
            Arrays.fill(states, Blocks.AIR.defaultBlockState());
            for (int y = 0; y < batch.volume.sizeY(); y++)
                states[batch.volume.blockY(y) - getMinY()] = batch.raw(x & 15, y, z & 15);
        }

        BlockState at(int y) {
            return isOutsideBuildHeight(y)
                    ? Blocks.VOID_AIR.defaultBlockState()
                    : states[y - getMinY()];
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
            return ChunkStatus.BIOMES;
        }

        @Override
        public BlockState getBlockState(BlockPos p) {
            if (p.getX() != x || p.getZ() != z)
                throw new IllegalArgumentException("Foreign local column read " + p);
            return at(p.getY());
        }

        @Override
        public FluidState getFluidState(BlockPos p) {
            return getBlockState(p).getFluidState();
        }

        @Override
        public BlockState setBlockState(BlockPos p, BlockState s, int flags) {
            if (heights != null) throw new IllegalStateException("Completed local terrain column");
            var old = getBlockState(p);
            if (!isOutsideBuildHeight(p)) states[p.getY() - getMinY()] = s;
            return old;
        }

        @Override
        public int getHeight(Heightmap.Types type, int bx, int bz) {
            if (bx != x || bz != z)
                throw new IllegalArgumentException("Foreign local height column");
            if (heights != null && heights[type.ordinal()] != Integer.MIN_VALUE)
                return heights[type.ordinal()];
            int result = getMinY() - 1;
            for (int y = getMaxY(); y >= getMinY(); y--)
                if (type.isOpaque().test(at(y))) {
                    result = y;
                    break;
                }
            if (heights != null) heights[type.ordinal()] = result;
            return result;
        }

        void finishHeights() {
            heights = new int[Heightmap.Types.values().length];
            Arrays.fill(heights, Integer.MIN_VALUE);
        }

        @Override
        public void markPosForPostProcessing(BlockPos p) {
            getBlockState(p);
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "Local terrain column has no materialization capability");
        }

        @Override
        public LevelChunkSection[] getSections() {
            throw unsupported();
        }

        @Override
        public LevelChunkSection getSection(int i) {
            throw unsupported();
        }

        @Override
        public void setBlockEntity(BlockEntity e) {
            throw unsupported();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos p) {
            throw unsupported();
        }

        @Override
        public void removeBlockEntity(BlockPos p) {
            throw unsupported();
        }

        @Override
        public void addEntity(Entity e) {
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
        public PackedTicks getTicksForSerialization(long time) {
            throw unsupported();
        }
    }
}
