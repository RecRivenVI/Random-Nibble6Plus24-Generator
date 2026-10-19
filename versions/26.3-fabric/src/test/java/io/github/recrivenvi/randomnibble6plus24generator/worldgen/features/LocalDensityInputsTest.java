package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.GradientFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.*;
import org.junit.jupiter.api.Test;

class LocalDensityInputsTest {
    private static final DensityVolume BOUNDS = new DensityVolume(16, 384, 16, 0, -64, 0);
    private static final DensityFunction X =
            new CacheFunction(
                    new GradientFunction(
                            Direction.Axis.X, TilingMode.CLAMP_TO_EDGE, -64, 64, -0.25f, 0.75f));
    private static final DensityFunction Y =
            new GradientFunction(Direction.Axis.Y, TilingMode.CLAMP_TO_EDGE, -64, 320, 1, -1);
    private static final DensityFunction SURFACE = new InterpolatedFunction(X, 16, 1);

    @Test
    void restoresOnlyNative2dInputsAtTheTranslatedNativeLattice() {
        var plan =
                LocalDensityInputs.resolve(
                        new InterpolatedFunction(X.add(Y), 4, 8),
                        BOUNDS,
                        List.of(X),
                        List.of(),
                        SURFACE);
        assertNotNull(plan);
        var volumes = new ArrayList<DensityVolume>();
        var compiler = new DensityFunctionCompiler(null);
        DensitySamplerSet samplers =
                f ->
                        new DensitySampler() {
                            @Override
                            public void sampleVolume(
                                    SamplerContext context,
                                    DensityBuffer output,
                                    DensityVolume volume) {
                                volumes.add(volume);
                                compiler.getSampler(f).sampleVolume(context, output, volume);
                            }

                            @Override
                            public float sampleValue(SamplerContext context, int x, int y, int z) {
                                throw new AssertionError(
                                        "Restoration must use the native volume path");
                            }
                        }.bind(SamplerContext.builder().enableCaches().build());
        plan.prepare(samplers, -16, 48);
        assertFalse(volumes.isEmpty());
        for (var v : volumes) {
            assertEquals(5, v.sizeX());
            assertEquals(1, v.sizeY());
            assertEquals(5, v.sizeZ());
            assertEquals(-16, v.minBlockX());
            assertEquals(0, v.minBlockY());
            assertEquals(48, v.minBlockZ());
            assertEquals(4, v.stepBlockX());
            assertEquals(8, v.stepBlockY());
            assertEquals(4, v.stepBlockZ());
        }
    }

    @Test
    void unprovenScalarOrMaterialCacheCouplingFallsBack() {
        var three = new CacheFunction(X.add(Y));
        assertNull(
                LocalDensityInputs.resolve(
                        new InterpolatedFunction(three, 4, 8),
                        BOUNDS,
                        List.of(three),
                        List.of(),
                        SURFACE));
        assertNull(
                LocalDensityInputs.resolve(
                        new InterpolatedFunction(X.add(Y), 4, 8),
                        BOUNDS,
                        List.of(X),
                        List.of(X),
                        SURFACE));
        assertNull(
                LocalDensityInputs.resolve(
                        new InterpolatedFunction(Y, 4, 8),
                        BOUNDS,
                        List.of(X),
                        List.of(X),
                        SURFACE));
        assertNull(
                LocalDensityInputs.resolve(
                        new InterpolatedFunction(X.add(Y), 4, 8),
                        BOUNDS,
                        List.of(X),
                        List.of(),
                        new InterpolatedFunction(X, 8, 1)));
    }

    @Test
    void mixedCacheLatticesAndNestedInterpolationAreNotGuessed() {
        var first = new InterpolatedFunction(X.add(Y), 4, 8);
        var second = new InterpolatedFunction(X.add(Y), 8, 8);
        assertNull(
                LocalDensityInputs.resolve(
                        first.add(second), BOUNDS, List.of(X), List.of(), SURFACE));
        assertNull(
                LocalDensityInputs.resolve(
                        new InterpolatedFunction(first, 4, 8),
                        BOUNDS,
                        List.of(),
                        List.of(),
                        SURFACE));
        assertNotNull(LocalDensityInputs.resolve(first, BOUNDS, List.of(), List.of(), SURFACE));
    }
}
