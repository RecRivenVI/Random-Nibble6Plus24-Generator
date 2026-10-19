package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.TerrainBiomeInputs;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** B with exact on-demand reads; independent experiment, not persisted production dispatch. */
public final class LightweightFeatureBaseline {
    private LightweightFeatureBaseline() {}

    public static Result generate(
            IsolatedGenerationContext context,
            LocalFeatureInputs.Observer observer,
            BiConsumer<StrictFeatureRegion, LocalFeatureInputs> beforeDecoration) {
        return generate(context, observer, beforeDecoration, null, true);
    }

    public static Result generateWithStructureReuse(
            IsolatedGenerationContext context,
            LocalFeatureInputs.Observer observer,
            BiConsumer<StrictFeatureRegion, LocalFeatureInputs> beforeDecoration) {
        try (var reuse = ScopedStructureSources.create(context)) {
            return generate(context, observer, beforeDecoration, reuse, true);
        }
    }

    /** Same generation algorithm, without full column evidence on the physical hot path. */
    public static Result generateForHandoff(
            IsolatedGenerationContext context,
            LocalFeatureInputs.Observer observer,
            BiConsumer<StrictFeatureRegion, LocalFeatureInputs> beforeDecoration,
            boolean reuseStructures) {
        if (!reuseStructures) return generate(context, observer, beforeDecoration, null, false);
        try (var reuse = ScopedStructureSources.create(context)) {
            return generate(context, observer, beforeDecoration, reuse, false);
        }
    }

    private static Result generate(
            IsolatedGenerationContext context,
            LocalFeatureInputs.Observer observer,
            BiConsumer<StrictFeatureRegion, LocalFeatureInputs> beforeDecoration,
            ScopedStructureSources reuse,
            boolean captureProofs) {
        try (var biomes = reuse == null ? null : new TerrainBiomeInputs(context)) {
            return generate(context, observer, beforeDecoration, reuse, captureProofs, biomes);
        }
    }

    private static Result generate(
            IsolatedGenerationContext context,
            LocalFeatureInputs.Observer observer,
            BiConsumer<StrictFeatureRegion, LocalFeatureInputs> beforeDecoration,
            ScopedStructureSources reuse,
            boolean captureProofs,
            TerrainBiomeInputs biomes) {
        long begin = System.nanoTime();
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        if (step.directDependencies().getRadiusOf(ChunkStatus.TERRAIN) != 1
                || step.blockStateWriteRadius() != 1)
            throw new IllegalStateException("Unaudited B FEATURES metadata");
        var structures = CompactStructureInputs.resolve(context, reuse);
        var target =
                LightweightTerrainGenerator.generate(context, structures, (s, c) -> {}, biomes)
                        .target();
        Map<String, Long> rejected = new HashMap<>();
        java.util.function.Consumer<String> drop = api -> rejected.merge(api, 1L, Long::sum);
        if (reuse != null) structures.handoffForDecoration(reuse);
        try (var inputs =
                new LocalFeatureInputs(
                        context, target, drop, observer, reuse, captureProofs, biomes)) {
            StaticCache2D<GenerationChunkHolder> cache =
                    StaticCache2D.create(
                            context.target().x(),
                            context.target().z(),
                            0,
                            (x, z) -> StrictFeatureRegion.constructorCarrier(target));
            GenerationContextRegistry.bind(cache, context);
            var region =
                    new StrictFeatureRegion(
                            context.hostLevel(), cache, step, target, inputs.chunks(), drop);
            GenerationContextRegistry.bind(region, context);
            region.bindStructureQueries(structures);
            if (region.getSeed() != context.worldSeed())
                throw new IllegalStateException("B local seed routing failed");
            beforeDecoration.accept(region, inputs);
            long prepared = System.nanoTime();
            context.generator().applyBiomeDecoration(region, target, structures.manager());
            Blender.generateBorderTicks(region, target);
            target.setPersistedStatus(ChunkStatus.FEATURES);
            long decorated = System.nanoTime();
            return new Result(
                    target,
                    region,
                    inputs,
                    structures,
                    Map.copyOf(rejected),
                    prepared - begin,
                    decorated - prepared);
        }
    }

    public record Result(
            ProtoChunk target,
            StrictFeatureRegion region,
            LocalFeatureInputs inputs,
            CompactStructureInputs structures,
            Map<String, Long> rejected,
            long preparationNanos,
            long decorationNanos) {
        /** Transfer only materialized data, never the closed local-query capability. */
        public StrictFeatureBaseline.Result contributionInputs(IsolatedGenerationContext context) {
            if (GenerationContextRegistry.find(region).orElse(null) != context
                    || !target.getPos().equals(context.target())
                    || region.getSeed() != context.worldSeed())
                throw new IllegalArgumentException("A input transfer requires its live B owner");
            var terrain = inputs.fullTerrain();
            Map<Long, StrictFeatureChunk> chunks = new HashMap<>();
            terrain.forEach(
                    (p, c) ->
                            chunks.put(
                                    p,
                                    new StrictFeatureChunk(
                                            c,
                                            context.target(),
                                            context.hostLevel().palettedContainerFactory(),
                                            api -> {
                                                throw new UnsupportedOperationException(
                                                        "A input transfer is read-only outside target: "
                                                                + api);
                                            },
                                            context.hostLevel().registryAccess())));
            var cache =
                    StaticCache2D.create(
                            context.target().x(),
                            context.target().z(),
                            0,
                            (x, z) -> StrictFeatureRegion.constructorCarrier(target));
            GenerationContextRegistry.bind(cache, context);
            var view =
                    new StrictFeatureRegion(
                            context.hostLevel(),
                            cache,
                            ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES),
                            target,
                            chunks,
                            api -> {
                                throw new UnsupportedOperationException(
                                        "No A input transfer write: " + api);
                            });
            GenerationContextRegistry.bind(view, context);
            return new StrictFeatureBaseline.Result(
                    target, view, terrain, structures, rejected, decorationNanos);
        }
    }
}
