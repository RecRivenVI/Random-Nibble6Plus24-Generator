package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Native 1.20.1 dependency formula, not a guessed full-terrain radius. */
final class FeatureOuterInputs {
    private FeatureOuterInputs() {}

    static ChunkStatus requiredStatus(ChunkPos writer, int x, int z) {
        long distance = Math.max(Math.abs((long) x - writer.x), Math.abs((long) z - writer.z));
        if (distance > ChunkStatus.FEATURES.getRange())
            throw new IllegalArgumentException("Outside native FEATURES frontier");
        return distance == 0
                ? ChunkStatus.FEATURES.getParent()
                : ChunkStatus.getStatusAroundFullChunk(
                        ChunkStatus.getDistance(ChunkStatus.FEATURES) + (int) distance);
    }

    static int carverRadius() {
        int radius = 0;
        for (int distance = 1; distance <= ChunkStatus.FEATURES.getRange(); distance++)
            if (ChunkStatus.getStatusAroundFullChunk(
                            ChunkStatus.getDistance(ChunkStatus.FEATURES) + distance)
                    .isOrAfter(ChunkStatus.CARVERS)) radius = distance;
        if (radius != 1)
            throw new IllegalStateException("Unaudited FEATURES terrain requirement " + radius);
        return radius;
    }

    static ChunkStatus status(ChunkPos writer, int x, int z) {
        var required = requiredStatus(writer, x, z);
        long distance = Math.max(Math.abs((long) x - writer.x), Math.abs((long) z - writer.z));
        if (distance <= carverRadius()
                || required.isOrAfter(ChunkStatus.NOISE)
                || !required.isOrAfter(ChunkStatus.STRUCTURE_STARTS))
            throw new UnsupportedOperationException("Unaudited outer prerequisite " + required);
        return required;
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
                            context.host().registryAccess().registryOrThrow(Registries.BIOME),
                            null);
            inputs.copyStartsTo(chunk);
            chunk.setStatus(ChunkStatus.STRUCTURE_STARTS);
            if (required.isOrAfter(ChunkStatus.STRUCTURE_REFERENCES)) {
                inputs.copyReferencesTo(chunk);
                chunk.setStatus(ChunkStatus.STRUCTURE_REFERENCES);
            }
            if (required.isOrAfter(ChunkStatus.BIOMES)) {
                context.generator()
                        .createBiomes(
                                Util.backgroundExecutor(),
                                context.randomState(),
                                Blender.empty(),
                                inputs.manager(),
                                chunk)
                        .join();
                chunk.setStatus(ChunkStatus.BIOMES);
            }
            if (chunk.getStatus() != required)
                throw new IllegalStateException("Outer native stage mismatch");
            return chunk;
        }
    }
}
