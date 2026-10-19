package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.*;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.*;
import net.minecraft.world.level.levelgen.densityfunction.op.*;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/** Native volume sampling on demand, never the numerically different sampleValue shortcut. */
public final class LocalDensityTiles implements AutoCloseable {
    private static final int WIDTH = 4;
    private static final ThreadLocal<LocalDensityTiles> ACTIVE = new ThreadLocal<>();
    private final DensityVolume bounds;
    private final DensitySampler.Bound sampler;
    private final int tileHeight;
    private final Runnable release;
    private final float[][] tiles;
    private final IdentityHashMap<DensitySampler, Map<SourceGrid, Map<Long, float[]>>> sources =
            new IdentityHashMap<>();
    private long samples;
    private long sourceSamples;
    private int generatedTiles;
    private boolean closed;

    LocalDensityTiles(DensityVolume bounds, DensitySampler.Bound sampler, int tileHeight) {
        this(bounds, sampler, tileHeight, () -> {});
    }

    private LocalDensityTiles(
            DensityVolume bounds, DensitySampler.Bound sampler, int tileHeight, Runnable release) {
        if (bounds.sizeX() != 16
                || bounds.sizeZ() != 16
                || tileHeight <= 0
                || bounds.stepBlockX() != 1
                || bounds.stepBlockY() != 1
                || bounds.stepBlockZ() != 1
                || Math.floorMod(bounds.minBlockX(), 16) != 0
                || Math.floorMod(bounds.minBlockZ(), 16) != 0
                || Math.floorMod(bounds.minBlockY(), tileHeight) != 0
                || bounds.sizeY() % tileHeight != 0)
            throw new IllegalArgumentException("Unaligned native local density range");
        this.bounds = bounds;
        this.sampler = sampler;
        this.tileHeight = tileHeight;
        this.release = release;
        tiles = new float[16 / WIDTH * (16 / WIDTH) * (bounds.sizeY() / tileHeight)][];
    }

    static LocalDensityTiles open(
            DensityVolume bounds,
            RandomState state,
            Beardifier beard,
            DensityFunction function,
            int tileHeight) {
        var pool = state.acquireDensityBufferPool();
        try {
            var context =
                    SamplerContext.builder()
                            .useBufferArena(pool)
                            .enableCaches()
                            .setUserFields(
                                    ContextMap.builder().set(Beardifier.CONTEXT_KEY, beard).build())
                            .build();
            return new LocalDensityTiles(
                    bounds,
                    state.samplersWithContext(context).get(function),
                    tileHeight,
                    () -> state.releaseDensityBufferPool(pool));
        } catch (RuntimeException | Error failure) {
            state.releaseDensityBufferPool(pool);
            throw failure;
        }
    }

    float get(int x, int y, int z) {
        if (closed) throw new IllegalStateException("Closed local density tiles");
        if (x < 0 || x >= 16 || z < 0 || z >= 16 || y < 0 || y >= bounds.sizeY())
            throw new IllegalArgumentException("Outside native local density range");
        int tx = x / WIDTH, tz = z / WIDTH, ty = y / tileHeight;
        int key = tx + tz * (16 / WIDTH) + ty * (16 / WIDTH) * (16 / WIDTH);
        var values = tiles[key];
        if (values == null) tiles[key] = values = sample(tx, ty, tz);
        return values[y % tileHeight + (x % WIDTH + z % WIDTH * WIDTH) * tileHeight];
    }

    private float[] sample(int x, int y, int z) {
        var volume =
                new DensityVolume(
                        WIDTH,
                        tileHeight,
                        WIDTH,
                        bounds.blockX(x * WIDTH),
                        bounds.blockY(y * tileHeight),
                        bounds.blockZ(z * WIDTH));
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested native density query");
        ACTIVE.set(this);
        try (var buffer = sampler.sampleVolume(volume)) {
            var values = new float[volume.size()];
            for (int i = 0; i < values.length; i++) values[i] = buffer.get(i);
            samples += values.length;
            generatedTiles++;
            return values;
        } finally {
            ACTIVE.remove();
        }
    }

    /** Narrow native interpolation hook, inactive for target generation and normal Vanilla. */
    public static boolean sampleSource(
            DensitySampler input,
            SamplerContext context,
            DensityBuffer output,
            DensityVolume volume) {
        var owner = ACTIVE.get();
        if (owner == null
                || context != owner.sampler.context()
                || volume.stepBlockX() < 2
                || volume.stepBlockZ() < 2
                || volume.stepBlockY() < 2) return false;
        owner.source(input, context, output, volume);
        return true;
    }

    private void source(
            DensitySampler input,
            SamplerContext context,
            DensityBuffer output,
            DensityVolume volume) {
        // Keep the native volume kernel and its Y prefix. Sharing sparse source columns avoids
        // re-evaluating the same interpolation corners for every neighboring output tile.
        int step = volume.stepBlockY();
        int bottom = Math.floorDiv(Math.min(bounds.minBlockY(), volume.minBlockY()), step) * step;
        int top =
                -Math.floorDiv(
                                -Math.max(bounds.maxBlockY(), volume.blockY(volume.sizeY() - 1)),
                                step)
                        * step;
        var grid =
                new SourceGrid(
                        volume.stepBlockX(),
                        step,
                        volume.stepBlockZ(),
                        bottom,
                        (top - bottom) / step + 1);
        var columns =
                sources.computeIfAbsent(input, unused -> new HashMap<>())
                        .computeIfAbsent(grid, unused -> new HashMap<>());
        for (int z = 0; z < volume.sizeZ(); z++)
            for (int x = 0; x < volume.sizeX(); x++) {
                int bx = volume.blockX(x), bz = volume.blockZ(z);
                long key = net.minecraft.world.level.ChunkPos.pack(bx, bz);
                var values = columns.get(key);
                if (values == null) {
                    var column =
                            new DensityVolume(
                                    1,
                                    grid.height(),
                                    1,
                                    bx,
                                    bottom,
                                    bz,
                                    grid.x(),
                                    grid.y(),
                                    grid.z());
                    try (var buffer = context.acquireBuffer(column)) {
                        input.sampleVolume(context, buffer, column);
                        values = new float[column.size()];
                        for (int y = 0; y < values.length; y++) values[y] = buffer.get(y);
                        sourceSamples += values.length;
                        columns.put(key, values);
                    }
                }
                for (int y = 0; y < volume.sizeY(); y++)
                    output.set(
                            volume.indexUnchecked(x, y, z),
                            values[(volume.blockY(y) - bottom) / step]);
            }
    }

    private record SourceGrid(int x, int y, int z, int bottom, int height) {}

    long samples() {
        return samples;
    }

    long sourceSamples() {
        return sourceSamples;
    }

    int tiles() {
        return generatedTiles;
    }

    static DensityVolume materialVolume(DensityVolume bounds, int x, int z) {
        return new DensityVolume(
                WIDTH,
                bounds.sizeY(),
                WIDTH,
                bounds.blockX((x & 15) / WIDTH * WIDTH),
                bounds.minBlockY(),
                bounds.blockZ((z & 15) / WIDTH * WIDTH));
    }

    /** Unknown/unaligned graphs cannot silently acquire the projective-volume capability. */
    static int tileHeight(DensityFunction root, int minY, int height) {
        var audit = new Audit(minY, height);
        return audit.visit(root, 0) ? audit.period : 0;
    }

    static String unsupported(DensityFunction root, int minY, int height) {
        var audit = new Audit(minY, height);
        return audit.visit(root, 0) ? null : audit.reason;
    }

    private static final class Audit {
        private static final Set<Class<?>> KNOWN =
                Set.of(
                        DensityFunctions.HolderHolder.class,
                        CacheFunction.class,
                        ConstantFunction.class,
                        DistanceToPointFunction.class,
                        EndIslandFunction.class,
                        GradientFunction.class,
                        NoiseFunction.class,
                        ShiftNoiseFunction.Shift.class,
                        ShiftNoiseFunction.ShiftA.class,
                        ShiftNoiseFunction.ShiftB.class,
                        SimpleDensityFunction.class,
                        BlendedNoise.class,
                        BinaryFunction.class,
                        BlendDensityFunction.class,
                        ClampFunction.class,
                        FindTopSurfaceFunction.class,
                        InterpolatedFunction.class,
                        IntervalSelectFunction.class,
                        LerpFunction.class,
                        PowFunction.class,
                        RangeChoiceFunction.class,
                        RoundFunction.class,
                        SliceFunction.class,
                        SplineFunction.class,
                        UnaryFunction.class);
        final IdentityHashMap<DensityFunction, Boolean> visited = new IdentityHashMap<>();
        final int minY, height;
        int period = 32;
        String reason = "cyclic/unaligned density graph";

        Audit(int minY, int height) {
            this.minY = minY;
            this.height = height;
        }

        boolean visit(DensityFunction value, int depth) {
            if (depth > 256 || !KNOWN.contains(value.getClass())) {
                reason = "unaudited density " + value.getClass().getName();
                return false;
            }
            var known = visited.get(value);
            if (known != null) return known;
            visited.put(value, false);
            if (value instanceof InterpolatedFunction i) {
                int xy = i.cellSizeXz(), y = i.cellSizeY();
                if (xy < 2
                        || xy > 16
                        || (xy & (xy - 1)) != 0
                        || y <= 0
                        || y > 64
                        || (y & (y - 1)) != 0
                        || Math.floorMod(minY, y) != 0
                        || height % y != 0) {
                    reason = "unaligned interpolation " + xy + "x" + y;
                    return false;
                }
                period = Math.max(period, y);
            }
            boolean[] supported = {true};
            value.rewriteChildren(
                    child -> {
                        if (!visit(child, depth + 1)) supported[0] = false;
                        return child;
                    });
            visited.put(value, supported[0]);
            return supported[0] && height % period == 0 && Math.floorMod(minY, period) == 0;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Arrays.fill(tiles, null);
        sources.clear();
        release.run();
    }
}
