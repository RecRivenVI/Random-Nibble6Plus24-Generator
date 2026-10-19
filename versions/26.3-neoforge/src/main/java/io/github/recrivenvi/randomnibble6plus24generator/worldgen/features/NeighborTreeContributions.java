package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

/** Experimental A: ore pass then a serial tree-only pass, never neighboring FEATURES. */
public final class NeighborTreeContributions {
    private NeighborTreeContributions() {}

    public record Metrics(
            int sourceChunks,
            int slots,
            int attempts,
            int successful,
            List<String> trace,
            Set<String> supported,
            Map<String, String> unsupported,
            TreeProjectionRegion.Metrics state,
            long nanos) {}

    public static Metrics apply(
            IsolatedGenerationContext context, StrictFeatureBaseline.Result baseline) {
        return apply(context, baseline, (slot, random) -> {});
    }

    public static Metrics apply(
            IsolatedGenerationContext context,
            StrictFeatureBaseline.Result baseline,
            java.util.function.BiConsumer<NeighborOreContributions.Slot, WorldgenRandom>
                    testCheckpoint) {
        try (var region = new TreeProjectionRegion(context, baseline)) {
            var steps =
                    FeatureSorter.buildFeaturesPerStep(
                            List.copyOf(context.generator().getBiomeSource().possibleBiomes()),
                            b -> context.generator().getBiomeGenerationSettings(b).features(),
                            true);
            var supported = new TreeSet<String>();
            var unsupported = new TreeMap<String, String>();
            var trace = new ArrayList<String>();
            int slots = 0, attempts = 0, success = 0;
            long started = System.nanoTime();
            for (int z = context.target().z() - 1; z <= context.target().z() + 1; z++)
                for (int x = context.target().x() - 1; x <= context.target().x() + 1; x++) {
                    var source = new ChunkPos(x, z);
                    if (source.equals(context.target())) continue;
                    region.beginWriter(source);
                    var origin =
                            new BlockPos(
                                    source.getMinBlockX(),
                                    context.hostLevel().getMinY(),
                                    source.getMinBlockZ());
                    var random = new WorldgenRandom(new XoroshiroRandomSource(0));
                    long decoration =
                            random.setDecorationSeed(
                                    context.worldSeed(), origin.getX(), origin.getZ());
                    for (var slot :
                            NeighborOreContributions.slots(
                                    context, region.terrainQuery(), source, steps)) {
                        var reason =
                                TreeContributionSupport.unsupportedNeighbor(
                                        slot.feature(), context.hostLevel().registryAccess());
                        if (reason != null) {
                            unsupported.put(slot.key(), reason);
                            continue;
                        }
                        supported.add(slot.key());
                        slots++;
                        random.setFeatureSeed(decoration, slot.index(), slot.step());
                        var pc =
                                new PlacementContext(
                                        region, context.generator(), Optional.of(slot.feature()));
                        var positions = new ArrayList<BlockPos>();
                        var indices = new ArrayList<Integer>();
                        positions.add(origin);
                        indices.add(0);
                        var modifiers = slot.feature().placement();
                        if (modifiers.isEmpty()) {
                            attempts++;
                            if (slot.feature()
                                    .feature()
                                    .value()
                                    .place(region, context.generator(), random, origin)) success++;
                        } else
                            while (!positions.isEmpty()) {
                                var pos = positions.removeLast();
                                int index = indices.removeLast();
                                var next = new ArrayList<BlockPos>();
                                modifiers
                                        .get(index)
                                        .modify(pc, random, pos, p -> next.add(p.immutable()));
                                if (index + 1 < modifiers.size())
                                    for (int i = next.size() - 1; i >= 0; i--) {
                                        positions.add(next.get(i));
                                        indices.add(index + 1);
                                    }
                                else
                                    for (var p : next) {
                                        attempts++;
                                        if (slot.feature()
                                                .feature()
                                                .value()
                                                .place(region, context.generator(), random, p))
                                            success++;
                                    }
                            }
                        // A stable, post-feature RNG checkpoint, matching native loop
                        // instrumentation.
                        trace.add(
                                source.x()
                                        + ","
                                        + source.z()
                                        + ":"
                                        + slot.key()
                                        + ":"
                                        + random.getCount());
                        testCheckpoint.accept(slot, random);
                    }
                }
            return new Metrics(
                    8,
                    slots,
                    attempts,
                    success,
                    List.copyOf(trace),
                    Set.copyOf(supported),
                    Map.copyOf(unsupported),
                    region.metrics(),
                    System.nanoTime() - started);
        }
    }
}
