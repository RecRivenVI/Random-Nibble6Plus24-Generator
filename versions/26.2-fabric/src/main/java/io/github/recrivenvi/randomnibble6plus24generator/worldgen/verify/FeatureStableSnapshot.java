package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.List;
import java.nio.file.Path;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Canonical FEATURES snapshot. Entity UUID values remain available as raw
 * evidence but are excluded from equality and SHA-256 because Vanilla creates
 * them from a non-worldgen random source.
 */
public final class FeatureStableSnapshot {

    private final FeatureStageSnapshot canonical;
    private final List<String> rawEntityNbt;

    private FeatureStableSnapshot(FeatureStageSnapshot canonical, List<String> rawEntityNbt) {
        this.canonical = canonical;
        this.rawEntityNbt = List.copyOf(rawEntityNbt);
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
        return new FeatureStableSnapshot(canonical, raw.entityNbt());
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
        return new FeatureStableSnapshot(FeatureStageSnapshot.read(path), List.of());
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
}
