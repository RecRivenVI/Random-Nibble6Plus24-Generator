package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;
import org.junit.jupiter.api.Test;

class LocalDensityTilesTest {
    private static DensityFunction function() {
        var y =
                new GradientFunction(
                        Direction.Axis.Y,
                        TilingMode.CLAMP_TO_EDGE,
                        -64,
                        320,
                        1.2345678f,
                        -0.654321f);
        var x =
                new GradientFunction(
                        Direction.Axis.X,
                        TilingMode.CLAMP_TO_EDGE,
                        -128,
                        128,
                        -0.1234567f,
                        0.9876543f);
        var z =
                new GradientFunction(
                        Direction.Axis.Z,
                        TilingMode.CLAMP_TO_EDGE,
                        -128,
                        128,
                        0.1478523f,
                        -0.7412589f);
        return new InterpolatedFunction(y.add(x.mul(z)), 4, 8);
    }

    @Test
    void nativeVolumeBitsSurviveShuffledTilesAndNegativeCoordinates() {
        for (int origin : new int[] {-32, -16, 0, 16}) {
            var bounds = new DensityVolume(16, 384, 16, origin, -64, -16);
            var sampler = function().compileSampler(null).bind(SamplerContext.EMPTY_UNCACHED);
            try (var expected = sampler.sampleVolume(bounds);
                    var actual = new LocalDensityTiles(bounds, sampler, 8)) {
                var positions = new ArrayList<Integer>();
                for (int i = 0; i < bounds.size(); i++) positions.add(i);
                Collections.shuffle(positions, new Random(12345));
                boolean scalarDifference = false;
                for (int index : positions) {
                    int y = index % 384, x = index / 384 % 16, z = index / (384 * 16);
                    var value = actual.get(x, y, z);
                    assertEquals(
                            Float.floatToRawIntBits(expected.get(index)),
                            Float.floatToRawIntBits(value));
                    scalarDifference |=
                            Float.floatToRawIntBits(value)
                                    != Float.floatToRawIntBits(
                                            sampler.sampleValue(
                                                    bounds.blockX(x),
                                                    bounds.blockY(y),
                                                    bounds.blockZ(z)));
                }
                assertTrue(scalarDifference, "Test must expose the forbidden scalar shortcut");
                assertEquals(bounds.size(), actual.samples());
            }
        }
    }

    @Test
    void oneReadDoesNotFillNeighborVolumeAndCleanupRejectsReads() {
        var bounds = new DensityVolume(16, 384, 16, -16, -64, 0);
        var actual =
                new LocalDensityTiles(
                        bounds,
                        function().compileSampler(null).bind(SamplerContext.EMPTY_UNCACHED),
                        8);
        actual.get(15, 383, 0);
        assertEquals(128, actual.samples());
        assertEquals(1, actual.tiles());
        actual.get(14, 382, 1);
        assertEquals(128, actual.samples());
        assertThrows(IllegalArgumentException.class, () -> actual.get(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> actual.get(0, -1, 0));
        actual.close();
        assertEquals(128, actual.samples());
        assertEquals(1, actual.tiles());
        assertThrows(IllegalStateException.class, () -> actual.get(0, 0, 0));
    }

    @Test
    void unauditedGridCannotAcquireLazyCapability() {
        assertEquals(32, LocalDensityTiles.tileHeight(function(), -64, 384));
        assertEquals(0, LocalDensityTiles.tileHeight(function(), -63, 384));
        assertEquals(0, LocalDensityTiles.tileHeight(function(), -64, 383));
        assertEquals(
                0,
                LocalDensityTiles.tileHeight(
                        new InterpolatedFunction(DensityFunctions.constant(1), 3, 8), -64, 384));
        assertEquals(
                0,
                LocalDensityTiles.tileHeight(
                        new InterpolatedFunction(DensityFunctions.constant(1), 4, 3), -64, 384));
        assertEquals(
                0,
                LocalDensityTiles.tileHeight(
                        new InterpolatedFunction(DensityFunctions.constant(1), 4, 128), -64, 384));
        var unknown =
                (DensityFunction)
                        java.lang.reflect.Proxy.newProxyInstance(
                                DensityFunction.class.getClassLoader(),
                                new Class<?>[] {DensityFunction.class},
                                (proxy, method, arguments) -> {
                                    throw new AssertionError("Unknown graph executed");
                                });
        assertEquals(0, LocalDensityTiles.tileHeight(unknown, -64, 384));
    }

    private static DensitySampler probe(DensitySampler input) {
        return new DensitySampler() {
            @Override
            public void sampleVolume(
                    SamplerContext context, DensityBuffer output, DensityVolume v) {
                var corners =
                        new DensityVolume(
                                2, 2, 2, v.minBlockX(), v.minBlockY(), v.minBlockZ(), 4, 8, 4);
                try (var buffer = context.acquireBuffer(corners)) {
                    assertTrue(LocalDensityTiles.sampleSource(input, context, buffer, corners));
                    for (int z = 0; z < 2; z++)
                        for (int x = 0; x < 2; x++)
                            for (int y = 0; y < 2; y++)
                                assertEquals(
                                        corners.blockX(x) * 13
                                                + corners.blockY(y) * 5
                                                + corners.blockZ(z) * 7,
                                        buffer.get(corners.indexUnchecked(x, y, z)));
                }
                output.fill(1);
            }

            @Override
            public float sampleValue(SamplerContext context, int x, int y, int z) {
                throw new AssertionError("Scalar shortcut");
            }
        };
    }

    @Test
    void nativeCornerColumnsAreScopedAndReusedWithoutScalarReads() {
        var input =
                new DensitySampler() {
                    @Override
                    public void sampleVolume(
                            SamplerContext context, DensityBuffer output, DensityVolume v) {
                        for (int z = 0; z < v.sizeZ(); z++)
                            for (int x = 0; x < v.sizeX(); x++)
                                for (int y = 0; y < v.sizeY(); y++)
                                    output.set(
                                            v.indexUnchecked(x, y, z),
                                            v.blockX(x) * 13 + v.blockY(y) * 5 + v.blockZ(z) * 7);
                    }

                    @Override
                    public float sampleValue(SamplerContext context, int x, int y, int z) {
                        throw new AssertionError("Scalar shortcut");
                    }
                };
        var bounds = new DensityVolume(16, 384, 16, 0, -64, 0);
        try (var query =
                new LocalDensityTiles(
                        bounds, probe(input).bind(SamplerContext.EMPTY_UNCACHED), 32)) {
            assertEquals(1, query.get(0, 0, 0));
            assertEquals(4 * 49, query.sourceSamples());
            assertEquals(1, query.get(4, 0, 0));
            assertEquals(6 * 49, query.sourceSamples());
            assertEquals(1, query.get(4, 32, 0));
            assertEquals(6 * 49, query.sourceSamples());
        }
        assertFalse(
                LocalDensityTiles.sampleSource(
                        input,
                        SamplerContext.EMPTY_UNCACHED,
                        null,
                        new DensityVolume(2, 2, 2, 0, -64, 0, 4, 8, 4)));
    }

    @Test
    void sourceFailureStillPropagatesAndAlwaysClearsThreadBinding() {
        var failure = new IllegalStateException("injected density failure");
        var input =
                new DensitySampler() {
                    @Override
                    public void sampleVolume(
                            SamplerContext context, DensityBuffer output, DensityVolume v) {
                        throw failure;
                    }

                    @Override
                    public float sampleValue(SamplerContext context, int x, int y, int z) {
                        throw new AssertionError("Scalar shortcut");
                    }
                };
        try (var query =
                new LocalDensityTiles(
                        new DensityVolume(16, 384, 16, 0, -64, 0),
                        probe(input).bind(SamplerContext.EMPTY_UNCACHED),
                        32)) {
            assertSame(
                    failure, assertThrows(IllegalStateException.class, () -> query.get(0, 0, 0)));
        }
        assertFalse(
                LocalDensityTiles.sampleSource(
                        input,
                        SamplerContext.EMPTY_UNCACHED,
                        null,
                        new DensityVolume(2, 2, 2, 0, -64, 0, 4, 8, 4)));
    }
}
