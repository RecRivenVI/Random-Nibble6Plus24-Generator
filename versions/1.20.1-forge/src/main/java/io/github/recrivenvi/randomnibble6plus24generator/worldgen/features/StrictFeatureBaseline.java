package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Conservative B generation with exact native neighbourhood input, not a terrain fast path. */
public final class StrictFeatureBaseline {
    private StrictFeatureBaseline() {}

    public static Result generate(
            StructureGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration) {
        context.checkPreparing();
        int radius = FeatureOuterInputs.carverRadius();
        var structures = CompactStructureInputs.resolve(context);
        var target =
                new LightweightTerrainGenerator(context, structures)
                        .generate((s, c) -> {})
                        .target();
        Map<Long, ProtoChunk> terrain = new HashMap<>();
        terrain.put(context.target().toLong(), target);
        for (int z = context.target().z - radius; z <= context.target().z + radius; z++)
            for (int x = context.target().x - radius; x <= context.target().x + radius; x++) {
                var position = new ChunkPos(x, z);
                if (position.equals(context.target())) continue;
                context.checkPreparing();
                try (var neighbor =
                        new StructureGenerationContext(context.host(), context.seed(), position)) {
                    var inputs = CompactStructureInputs.resolve(neighbor);
                    terrain.put(
                            position.toLong(),
                            new LightweightTerrainGenerator(neighbor, inputs)
                                    .generate((s, c) -> {})
                                    .target());
                }
            }
        context.checkPreparing();
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
                                        drop)));
        // The native legacy region constructor also needs its local StructureManager.
        var region =
                SeedLocalRegionScope.construct(
                        context,
                        structures.manager(),
                        () ->
                                new StrictFeatureRegion(
                                        context.host(),
                                        target,
                                        guarded,
                                        drop,
                                        context.generator(),
                                        context.randomState()));
        region.bindStructureQueries(structures.manager());
        region.bindOuterReadSource((x, z) -> FeatureOuterInputs.resolve(context, x, z));
        if (region.getSeed() != context.seed())
            throw new IllegalStateException("B region escaped local seed");
        long started = System.nanoTime();
        try {
            context.checkPreparing();
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
            target.setStatus(ChunkStatus.FEATURES);
            return new Result(
                    target,
                    region,
                    Map.copyOf(terrain),
                    structures,
                    Map.copyOf(rejected),
                    System.nanoTime() - started);
        } finally {
            region.close();
        }
    }

    public record Result(
            ProtoChunk target,
            StrictFeatureRegion region,
            Map<Long, ProtoChunk> terrain,
            CompactStructureInputs structures,
            Map<String, Long> rejected,
            long decorationNanos) {}
}
