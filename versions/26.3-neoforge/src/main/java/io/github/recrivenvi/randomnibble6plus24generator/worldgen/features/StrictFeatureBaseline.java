package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Standalone B baseline. No persisted profile dispatch or physical lifecycle calls this module. */
public final class StrictFeatureBaseline {
    private StrictFeatureBaseline() {}

    public static Result generate(
            IsolatedGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration) {
        return generate(
                context,
                beforeDecoration,
                (c, prepared) ->
                        c.generator()
                                .applyBiomeDecoration(
                                        prepared.region(),
                                        prepared.target(),
                                        prepared.structures().manager()));
    }

    /**
     * Dense, native-algorithm reference input; decoration policy supplied only by explicit callers.
     */
    public static Result generate(
            IsolatedGenerationContext context,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> beforeDecoration,
            BiConsumer<IsolatedGenerationContext, Result> decoration) {
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        int radius = step.directDependencies().getRadiusOf(ChunkStatus.TERRAIN);
        if (radius != 1 || step.blockStateWriteRadius() != 1)
            throw new IllegalStateException("Unaudited native FEATURES metadata");
        // This correctness baseline retains exact terrain for the native FEATURES read frontier.
        // It does not claim arbitrary block queries are already replaced by a lightweight sampler.
        Map<Long, ProtoChunk> terrain = new HashMap<>();
        CompactStructureInputs targetStructures = CompactStructureInputs.resolve(context);
        ProtoChunk target =
                LightweightTerrainGenerator.generate(context, targetStructures, (s, c) -> {})
                        .target();
        terrain.put(ChunkPos.pack(context.target().x(), context.target().z()), target);
        for (int z = context.target().z() - radius; z <= context.target().z() + radius; z++) {
            for (int x = context.target().x() - radius; x <= context.target().x() + radius; x++) {
                ChunkPos position = new ChunkPos(x, z);
                if (position.equals(context.target())) continue;
                try (var neighbor = context.createRelated(position)) {
                    var structures = CompactStructureInputs.resolve(neighbor);
                    ProtoChunk chunk =
                            LightweightTerrainGenerator.generate(neighbor, structures, (s, c) -> {})
                                    .target();
                    terrain.put(ChunkPos.pack(x, z), chunk);
                }
            }
        }
        Map<String, Long> rejected = new HashMap<>();
        java.util.function.Consumer<String> drop = api -> rejected.merge(api, 1L, Long::sum);
        Map<Long, StrictFeatureChunk> inputs = new HashMap<>();
        terrain.forEach(
                (pos, chunk) ->
                        inputs.put(
                                pos,
                                new StrictFeatureChunk(
                                        chunk,
                                        context.target(),
                                        context.hostLevel().palettedContainerFactory(),
                                        drop,
                                        context.hostLevel().registryAccess())));
        // One constructor carrier, no generation task/futures or dependency graph.
        StaticCache2D<GenerationChunkHolder> cache =
                StaticCache2D.create(
                        context.target().x(),
                        context.target().z(),
                        0,
                        (x, z) -> StrictFeatureRegion.constructorCarrier(target));
        GenerationContextRegistry.bind(cache, context);
        var region =
                new StrictFeatureRegion(context.hostLevel(), cache, step, target, inputs, drop);
        GenerationContextRegistry.bind(region, context);
        region.bindStructureQueries(targetStructures);
        if (region.getSeed() != context.worldSeed())
            throw new IllegalStateException("B local seed routing failed");
        beforeDecoration.accept(region, Map.copyOf(terrain));
        long started = System.nanoTime();
        decoration.accept(
                context,
                new Result(
                        target,
                        region,
                        Map.copyOf(terrain),
                        targetStructures,
                        Map.copyOf(rejected),
                        0));
        Blender.generateBorderTicks(region, target);
        target.setPersistedStatus(ChunkStatus.FEATURES);
        return new Result(
                target,
                region,
                Map.copyOf(terrain),
                targetStructures,
                Map.copyOf(rejected),
                System.nanoTime() - started);
    }

    public record Result(
            ProtoChunk target,
            StrictFeatureRegion region,
            Map<Long, ProtoChunk> terrain,
            CompactStructureInputs structures,
            Map<String, Long> rejected,
            long decorationNanos) {}
}
