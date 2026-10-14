package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.LightweightTerrainGenerator;
import java.util.*;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.blending.Blender;

/** One complete native target FEATURES call with selective tree output; experiment only. */
public final class SelectiveFeatureGeneration {
    private SelectiveFeatureGeneration() {}

    public static Result generate(IsolatedGenerationContext context, TreeContribution.Scope scope) {
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        if (step.directDependencies().getRadiusOf(ChunkStatus.TERRAIN) != 1
                || step.blockStateWriteRadius() != 1)
            throw new IllegalStateException("Unaudited C FEATURES metadata");
        var structures = CompactStructureInputs.resolve(context);
        var target =
                LightweightTerrainGenerator.generate(context, structures, (s, c) -> {}).target();
        Map<String, Long> rejected = new HashMap<>();
        java.util.function.Consumer<String> drop = api -> rejected.merge(api, 1L, Long::sum);
        try (var inputs =
                        new LocalFeatureInputs(
                                context, target, drop, new LocalFeatureInputs.Observer() {});
                var region =
                        new SelectiveFeatureRegion(
                                context,
                                target,
                                inputs.chunks(),
                                inputs::fullTerrain,
                                structures,
                                scope,
                                drop)) {
            if (region.getSeed() != context.worldSeed())
                throw new IllegalStateException("C local seed routing failed");
            context.generator().applyBiomeDecoration(region, target, structures.manager());
            Blender.generateBorderTicks(region, target);
            target.setPersistedStatus(ChunkStatus.FEATURES);
            return new Result(
                    target,
                    region.calls(),
                    region.contributions(),
                    Map.copyOf(rejected),
                    region.levelEscapeCounts(),
                    inputs.metrics());
        }
    }

    public record Result(
            ProtoChunk target,
            List<SelectiveFeatureRegion.Call> calls,
            List<TreeContribution> contributions,
            Map<String, Long> rejected,
            Map<String, Long> levelEscapes,
            LocalFeatureInputs.Metrics inputs) {}
}
