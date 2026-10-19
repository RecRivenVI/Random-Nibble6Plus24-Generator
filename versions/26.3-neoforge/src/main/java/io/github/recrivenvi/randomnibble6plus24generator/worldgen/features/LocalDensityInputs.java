package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import java.util.*;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.densityfunction.generator.ConstantFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.*;

/** Restores native scalar-consumer cache inputs without evaluating the neighbor's 3D density. */
final class LocalDensityInputs {
    private record Input(DensityFunction function, DensityVolume volume) {}

    private final List<Input> inputs = new ArrayList<>();
    private final Map<DensityFunction, DensityVolume> cacheLayouts = new HashMap<>();
    private final Set<DensityFunction> threeDimensionalCaches = new HashSet<>();
    private final Map<DensityVolume, Set<DensityFunction>> scanned = new HashMap<>();
    private final int originX, originZ;
    private boolean supported = true;

    private LocalDensityInputs(DensityVolume volume) {
        originX = volume.minBlockX();
        originZ = volume.minBlockZ();
    }

    static LocalDensityInputs resolve(
            DensityFunction root,
            DensityVolume volume,
            List<DensityFunction> scalarConsumers,
            List<DensityFunction> materialDensities,
            DensityFunction preliminarySurface) {
        var plan = new LocalDensityInputs(volume);
        plan.collect(root, volume, false, 0);
        var consumers = caches(scalarConsumers);
        var materialCaches = caches(materialDensities);
        plan.inputs.removeIf(
                input -> Collections.disjoint(caches(List.of(input.function())), consumers));
        if (!Collections.disjoint(plan.threeDimensionalCaches, consumers)) plan.supported = false;
        if (!Collections.disjoint(plan.threeDimensionalCaches, materialCaches))
            plan.supported = false;
        if (!Collections.disjoint(plan.cacheLayouts.keySet(), materialCaches))
            plan.supported = false;
        if (!Collections.disjoint(materialCaches, consumers)) plan.supported = false;
        // Native surface compilation samples the same four chunk corners at every queried tile.
        // A foreign grid could overwrite a different cache footprint; retain exact TERRAIN instead.
        if (!Collections.disjoint(plan.cacheLayouts.keySet(), consumers)) {
            var surface = preliminarySurface;
            while (surface instanceof DensityFunctions.HolderHolder h)
                surface = h.function().value();
            if (!(surface instanceof InterpolatedFunction i)
                    || i.cellSizeXz() != 16
                    || i.cellSizeY() != 1) plan.supported = false;
        }
        plan.scanned.clear();
        plan.cacheLayouts.clear();
        plan.threeDimensionalCaches.clear();
        return plan.supported ? plan : null;
    }

    private void collect(
            DensityFunction function, DensityVolume volume, boolean interpolated, int depth) {
        if (!supported) return;
        if (depth > 256
                || function instanceof FindTopSurfaceFunction
                || function instanceof SliceFunction) {
            supported = false;
            return;
        }
        if (function instanceof DensityFunctions.HolderHolder h) {
            collect(h.function().value(), volume, interpolated, depth + 1);
            return;
        }
        if ((function.domainAxes() & DensityFunction.AXIS_Y) == 0) {
            var projected =
                    new DensityVolume(
                            volume.sizeX(),
                            1,
                            volume.sizeZ(),
                            volume.minBlockX(),
                            0,
                            volume.minBlockZ(),
                            volume.stepBlockX(),
                            volume.stepBlockY(),
                            volume.stepBlockZ());
            if (!pure2d(function, projected, depth + 1)) supported = false;
            if (!(function instanceof ConstantFunction)) inputs.add(new Input(function, projected));
            return;
        }
        if (function instanceof CacheFunction c) threeDimensionalCaches.add(c.input());
        if (function instanceof SplineFunction) {
            supported = false;
            return;
        }
        if (function instanceof InterpolatedFunction i) {
            if (interpolated) {
                supported = false;
                return;
            }
            collect(i.input(), lattice(volume, i.cellSizeXz(), i.cellSizeY()), true, depth + 1);
            return;
        }
        // These are the native compile-time MIN/MAX interval shortcuts, not approximate rejection.
        if (function instanceof BinaryFunction b) {
            if (b.type() == BinaryFunction.Type.MIN || b.type() == BinaryFunction.Type.MAX) {
                var left = b.left().range();
                var right = b.right().range();
                boolean min = b.type() == BinaryFunction.Type.MIN;
                if ((min && left.max() < right.min()) || (!min && left.min() > right.max())) {
                    collect(b.left(), volume, interpolated, depth + 1);
                    return;
                }
                if ((min && right.max() < left.min()) || (!min && right.min() > left.max())) {
                    collect(b.right(), volume, interpolated, depth + 1);
                    return;
                }
            }
        }
        function.rewriteChildren(
                child -> {
                    collect(child, volume, interpolated, depth + 1);
                    return child;
                });
    }

    private boolean pure2d(DensityFunction f, DensityVolume volume, int depth) {
        if (depth > 256
                || f instanceof FindTopSurfaceFunction
                || f instanceof SliceFunction
                || f instanceof InterpolatedFunction) return false;
        if (!scanned.computeIfAbsent(
                        volume,
                        unused ->
                                Collections.newSetFromMap(
                                        new IdentityHashMap<DensityFunction, Boolean>()))
                .add(f)) return true;
        if (f instanceof CacheFunction c) {
            var previous = cacheLayouts.putIfAbsent(c.input(), volume);
            if (previous != null && !previous.equals(volume)) return false;
        }
        boolean[] accepted = {true};
        f.rewriteChildren(
                child -> {
                    if (!pure2d(child, volume, depth + 1)) accepted[0] = false;
                    return child;
                });
        return accepted[0];
    }

    private static DensityVolume lattice(DensityVolume v, int xz, int y) {
        int x0 = Math.floorDiv(v.minBlockX(), xz), x1 = Math.floorDiv(v.maxBlockX(), xz);
        int y0 = Math.floorDiv(v.minBlockY(), y), y1 = Math.floorDiv(v.maxBlockY(), y);
        int z0 = Math.floorDiv(v.minBlockZ(), xz), z1 = Math.floorDiv(v.maxBlockZ(), xz);
        return new DensityVolume(
                x1 - x0 + 1 + (Math.floorMod(v.maxBlockX(), xz) == 0 ? 0 : 1),
                y1 - y0 + 1 + (Math.floorMod(v.maxBlockY(), y) == 0 ? 0 : 1),
                z1 - z0 + 1 + (Math.floorMod(v.maxBlockZ(), xz) == 0 ? 0 : 1),
                x0 * xz,
                y0 * y,
                z0 * xz,
                xz,
                y,
                xz);
    }

    private static Set<DensityFunction> caches(List<DensityFunction> roots) {
        Set<DensityFunction> result = new HashSet<>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<DensityFunction, Boolean>());
        for (var root : roots) collectCaches(root, result, seen);
        return result;
    }

    private static void collectCaches(
            DensityFunction f, Set<DensityFunction> result, Set<DensityFunction> seen) {
        if (!seen.add(f)) return;
        if (f instanceof CacheFunction c) result.add(c.input());
        f.rewriteChildren(
                child -> {
                    collectCaches(child, result, seen);
                    return child;
                });
    }

    void prepare(DensitySamplerSet samplers, int blockX, int blockZ) {
        for (var input : inputs) {
            var v = input.volume();
            var projected =
                    new DensityVolume(
                            v.sizeX(),
                            v.sizeY(),
                            v.sizeZ(),
                            v.minBlockX() + blockX - originX,
                            v.minBlockY(),
                            v.minBlockZ() + blockZ - originZ,
                            v.stepBlockX(),
                            v.stepBlockY(),
                            v.stepBlockZ());
            try (var ignored = samplers.get(input.function()).sampleVolume(projected)) {}
        }
    }
}
