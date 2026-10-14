package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.OreHeightQuery;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Captures actual native buffers, never recomputes the reference column. Serial proof scope only.
 */
public final class OreHeightOracle implements AutoCloseable {
    private static final AtomicReference<OreHeightOracle> ACTIVE = new AtomicReference<>();
    private final List<OreHeightQuery.ColumnBound> expected;
    private final Set<OreHeightQuery.ColumnBound> density = new HashSet<>(),
            noise = new HashSet<>();
    private long samples;

    public OreHeightOracle(List<OreHeightQuery.ColumnBound> expected) {
        this.expected = expected;
        if (!ACTIVE.compareAndSet(null, this))
            throw new IllegalStateException("Concurrent height oracle scope");
    }

    public static void density(
            ChunkAccess chunk, DensityVolume volume, ScopedDensityBuffer buffer) {
        var scope = ACTIVE.get();
        if (scope == null) return;
        for (var c : scope.expected)
            if (ChunkPos.containing(new net.minecraft.core.BlockPos(c.x(), 0, c.z()))
                    .equals(chunk.getPos())) {
                String actual = OreHeightQuery.densityHash(volume, buffer, c.x(), c.z());
                if (!c.densityHash().equals(actual))
                    throw new IllegalStateException(
                            "Column density bits mismatch " + c.x() + "," + c.z());
                scope.density.add(c);
                scope.samples += volume.sizeY();
            }
    }

    public static void noise(ChunkAccess chunk) {
        var scope = ACTIVE.get();
        if (scope == null) return;
        for (var c : scope.expected)
            if (ChunkPos.containing(new net.minecraft.core.BlockPos(c.x(), 0, c.z()))
                    .equals(chunk.getPos())) {
                int actual = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.x(), c.z()) + 1;
                if (actual != c.noiseHeight())
                    throw new IllegalStateException(
                            "Column aquifer/noise height mismatch "
                                    + c.x()
                                    + ","
                                    + c.z()
                                    + ": "
                                    + c.noiseHeight()
                                    + " != "
                                    + actual);
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, c.x(), c.z()) + 1;
                if (floor != c.noiseOceanFloor())
                    throw new IllegalStateException(
                            "Column noise ocean floor mismatch " + c.x() + "," + c.z());
                scope.noise.add(c);
            }
    }

    public Map<String, Object> finish() {
        if (density.size() != expected.size() || noise.size() != expected.size())
            throw new IllegalStateException("Incomplete actual column oracle");
        return Map.of("columns", expected.size(), "densitySamples", samples, "status", "PASS");
    }

    @Override
    public void close() {
        if (!ACTIVE.compareAndSet(this, null))
            throw new IllegalStateException("Height oracle scope ownership lost");
    }
}
