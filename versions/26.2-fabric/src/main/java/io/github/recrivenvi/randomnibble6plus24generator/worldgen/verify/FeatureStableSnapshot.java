package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.List;
import java.nio.file.Path;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import java.util.Map;

/**
 * Canonical FEATURES snapshot. Entity UUID values remain available as raw
 * evidence but are excluded from equality and SHA-256 because Vanilla creates
 * them from a non-worldgen random source.
 */
public final class FeatureStableSnapshot {

    private final FeatureStageSnapshot canonical;
    private final List<String> rawEntityNbt;
    private final int instantiatedBlockEntityCount;
    private final int pendingBlockEntityNbtCount;

    private FeatureStableSnapshot(
            FeatureStageSnapshot canonical,
            List<String> rawEntityNbt,
            int instantiatedBlockEntityCount,
            int pendingBlockEntityNbtCount) {
        this.canonical = canonical;
        this.rawEntityNbt = List.copyOf(rawEntityNbt);
        this.instantiatedBlockEntityCount = instantiatedBlockEntityCount;
        this.pendingBlockEntityNbtCount = pendingBlockEntityNbtCount;
    }

    public static FeatureStableSnapshot capture(
            String dimension,
            ChunkAccess chunk,
            RegistryAccess registryAccess) {
        FeatureStageSnapshot raw = FeatureStageSnapshot.capture(dimension, chunk, registryAccess);
        FeatureStageSnapshot canonical = FeatureStageSnapshot.captureWithCanonicalEntityUuids(
                dimension,
                chunk,
                registryAccess);
        ProtoChunk protoChunk = (ProtoChunk) chunk;
        return new FeatureStableSnapshot(
                canonical,
                raw.entityNbt(),
                protoChunk.getBlockEntities().size(),
                protoChunk.getBlockEntityNbts().size());
    }

    public FeatureStageSnapshot.Diff diff(FeatureStableSnapshot other) {
        return canonical.diff(other.canonical);
    }

    public String deterministicMetadataDifference(FeatureStableSnapshot other) {
        return canonical.deterministicMetadataDifference(other.canonical);
    }

    public void write(Path path) {
        canonical.write(path);
    }

    public static FeatureStableSnapshot read(Path path) {
        return new FeatureStableSnapshot(FeatureStageSnapshot.read(path), List.of(), -1, -1);
    }

    public String hash() {
        return canonical.hash();
    }

    public ChunkPos chunkPos() {
        return canonical.chunkPos();
    }

    public int blockEntityCount() {
        return canonical.blockEntityCount();
    }

    public int blockTickCount() {
        return canonical.blockTickCount();
    }

    public int fluidTickCount() {
        return canonical.fluidTickCount();
    }

    public int postProcessingCount() {
        return canonical.postProcessingCount();
    }

    public int entityCount() {
        return canonical.entityCount();
    }

    public List<String> canonicalEntityNbt() {
        return canonical.entityNbt();
    }

    public List<String> rawEntityNbt() {
        return rawEntityNbt;
    }

    public int instantiatedBlockEntityCount() {
        return instantiatedBlockEntityCount;
    }

    public int pendingBlockEntityNbtCount() {
        return pendingBlockEntityNbtCount;
    }

    public Map<String, String> blockEntityNbt() {
        return canonical.blockEntityNbt();
    }

    public List<String> blockTickData() {
        return canonical.blockTickData();
    }

    public List<String> fluidTickData() {
        return canonical.fluidTickData();
    }

    public Map<String, String> structureStartData() {
        return canonical.structureStartData();
    }
}
