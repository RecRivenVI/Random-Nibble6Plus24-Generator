package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact native prerequisite stages outside the CARVERS ring, never a fake terrain fallback. */
final class FeatureOuterInputs {
    private FeatureOuterInputs() {}

    static ChunkStatus status(ChunkPos writer, int x, int z) {
        long distance = Math.max(Math.abs((long) x - writer.x), Math.abs((long) z - writer.z));
        var step = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        if (distance <= step.directDependencies().getRadiusOf(ChunkStatus.CARVERS)
                || distance >= step.directDependencies().size())
            throw new IllegalArgumentException("Outside native outer read range");
        var status = step.accumulatedDependencies().get((int) distance);
        if (status.isAfter(ChunkStatus.BIOMES) || status.isBefore(ChunkStatus.STRUCTURE_STARTS))
            throw new UnsupportedOperationException("Unaudited outer prerequisite " + status);
        return status;
    }

    static ProtoChunk resolve(StructureGenerationContext writer, int x, int z) {
        var required = status(writer.target(), x, z);
        try (var context =
                new StructureGenerationContext(writer.host(), writer.seed(), new ChunkPos(x, z))) {
            var inputs = CompactStructureInputs.resolve(context);
            var chunk =
                    new ProtoChunk(
                            context.target(),
                            UpgradeData.EMPTY,
                            context.host(),
                            context.host()
                                    .registryAccess()
                                    .registryOrThrow(
                                            net.minecraft.core.registries.Registries.BIOME),
                            null);
            inputs.copyStartsTo(chunk);
            chunk.setPersistedStatus(ChunkStatus.STRUCTURE_STARTS);
            if (required.isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) {
                inputs.copyReferencesTo(chunk);
                chunk.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
            }
            if (required.isOrAfter(ChunkStatus.BIOMES)) {
                context.generator()
                        .createBiomes(
                                context.randomState(), Blender.empty(), inputs.manager(), chunk)
                        .join();
                chunk.setPersistedStatus(ChunkStatus.BIOMES);
            }
            if (chunk.getPersistedStatus() != required)
                throw new IllegalStateException("Outer native stage mismatch");
            return chunk;
        }
    }
}
