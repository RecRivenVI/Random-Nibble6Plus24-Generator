package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Conservative B port baseline, not a registered generator or an on-demand performance path. */
public final class StrictFeatureBaseline {
    private StrictFeatureBaseline() {}

    public static Result generate(
            StructureGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration) {
        return generate(context, beforeDecoration, false);
    }

    /** Same native inputs and writer, with one logical local world's state shared while alive. */
    public static Result generateSharedWorld(
            StructureGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration) {
        return generate(context, beforeDecoration, true);
    }

    private static Result generate(
            StructureGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration,
            boolean shareWorldState) {
        var reuse = shareWorldState ? ScopedStructureSources.candidate(context) : null;
        Result result;
        try (reuse) {
            result = generate(context, beforeDecoration, shareWorldState, reuse);
        }
        if (reuse == null) return result;
        return new Result(
                result.target(),
                result.region(),
                result.terrain(),
                result.structures(),
                result.rejected(),
                result.preparationNanos(),
                result.decorationNanos(),
                reuse.metrics());
    }

    private static Result generate(
            StructureGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration,
            boolean shareWorldState,
            ScopedStructureSources reuse) {
        long preparing = System.nanoTime();
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        int radius = step.directDependencies().getRadiusOf(ChunkStatus.CARVERS);
        if (radius != 1 || step.blockStateWriteRadius() != 1)
            throw new IllegalStateException("Unaudited native FEATURES metadata");
        var structures = CompactStructureInputs.resolve(context, reuse);
        var target = terrain(context, structures, shareWorldState, reuse);
        if (reuse != null) structures.handoffForDecoration(reuse);
        Map<Long, ProtoChunk> terrain = new HashMap<>();
        terrain.put(context.target().toLong(), target);
        for (int z = context.target().z - radius; z <= context.target().z + radius; z++)
            for (int x = context.target().x - radius; x <= context.target().x + radius; x++) {
                var position = new ChunkPos(x, z);
                if (position.equals(context.target())) continue;
                try (var neighbor =
                        shareWorldState
                                ? context.fork(position)
                                : new StructureGenerationContext(
                                        context.host(), context.seed(), position)) {
                    var inputs = CompactStructureInputs.resolve(neighbor, reuse);
                    terrain.put(
                            position.toLong(), terrain(neighbor, inputs, shareWorldState, reuse));
                }
            }
        Map<String, Long> rejected = new HashMap<>();
        java.util.function.Consumer<String> drop = api -> rejected.merge(api, 1L, Long::sum);
        Map<Long, StrictFeatureChunk> guarded = new HashMap<>();
        terrain.forEach(
                (key, chunk) ->
                        guarded.put(
                                key,
                                new StrictFeatureChunk(
                                        chunk,
                                        context.target(),
                                        context.host()
                                                .registryAccess()
                                                .registryOrThrow(Registries.BIOME),
                                        drop,
                                        context.host().registryAccess())));
        // Native constructor identity only. All reads resolve through explicit bounded inputs.
        StaticCache2D<GenerationChunkHolder> bridge =
                StaticCache2D.create(context.target().x, context.target().z, 0, (x, z) -> null);
        var region =
                SeedLocalRegionScope.construct(
                        context,
                        () ->
                                new StrictFeatureRegion(
                                        context.host(),
                                        bridge,
                                        step,
                                        target,
                                        guarded,
                                        drop,
                                        context.generator(),
                                        context.randomState()));
        region.bindStructureQueries(structures.manager());
        region.bindOuterReadSource((x, z) -> FeatureOuterInputs.resolve(context, x, z));
        if (region.getSeed() != context.seed())
            throw new IllegalStateException("B region escaped local seed");
        long preparationNanos = System.nanoTime() - preparing;
        long started = System.nanoTime();
        try {
            beforeDecoration.accept(region, Map.copyOf(terrain));
            Heightmap.primeHeightmaps(
                    target,
                    EnumSet.of(
                            Heightmap.Types.MOTION_BLOCKING,
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Heightmap.Types.OCEAN_FLOOR,
                            Heightmap.Types.WORLD_SURFACE));
            try (var scope =
                    FeatureExecutionScope.bind(context.host(), region, structures.manager())) {
                context.generator().applyBiomeDecoration(region, target, structures.manager());
            }
            Blender.generateBorderTicks(region, target);
            region.assertNoForbiddenEscapes();
            target.setPersistedStatus(ChunkStatus.FEATURES);
            return new Result(
                    target,
                    region,
                    Map.copyOf(terrain),
                    structures,
                    Map.copyOf(rejected),
                    preparationNanos,
                    System.nanoTime() - started,
                    reuse == null ? null : reuse.metrics());
        } finally {
            region.close();
        }
    }

    private static ProtoChunk terrain(
            StructureGenerationContext context,
            CompactStructureInputs inputs,
            boolean shareWorldState,
            ScopedStructureSources reuse) {
        var generator = new LightweightTerrainGenerator(context, inputs);
        return (shareWorldState
                        ? generator.generateSharingWorld((s, c) -> {}, reuse)
                        : generator.generate((s, c) -> {}))
                .target();
    }

    public record Result(
            ProtoChunk target,
            StrictFeatureRegion region,
            Map<Long, ProtoChunk> terrain,
            CompactStructureInputs structures,
            Map<String, Long> rejected,
            long preparationNanos,
            long decorationNanos,
            ScopedStructureSources.Metrics structureReuse) {}
}
