package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

/** Frozen FEATURES writer ordering contract for Mosaic format version 2. */
public record FeatureOrderingPlan(
        int algorithmVersion,
        int blockStateWriteRadius,
        List<ChunkPos> writers) {

    public static final int TARGET_LOCAL_ZX_ROW_MAJOR_V1 = 1;
    private static final Comparator<ChunkPos> Z_THEN_X = Comparator
            .comparingInt(ChunkPos::z)
            .thenComparingInt(ChunkPos::x);

    public FeatureOrderingPlan {
        writers = List.copyOf(writers);
    }

    public static FeatureOrderingPlan targetLocalZxRowMajorV1(ChunkPos target) {
        ChunkStep featureStep = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES);
        int radius = featureStep.blockStateWriteRadius();
        if (radius != 1) {
            throw new IllegalStateException(
                    "FeatureOrderingAlgorithmVersion 1 requires Minecraft FEATURES blockStateWriteRadius=1, found "
                            + radius);
        }
        List<ChunkPos> writers = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int z = target.z() - radius; z <= target.z() + radius; z++) {
            for (int x = target.x() - radius; x <= target.x() + radius; x++) {
                writers.add(new ChunkPos(x, z));
            }
        }
        writers.sort(Z_THEN_X);
        return new FeatureOrderingPlan(TARGET_LOCAL_ZX_ROW_MAJOR_V1, radius, writers);
    }

    public Set<Long> packedWriterSet() {
        return writers.stream()
                .map(pos -> ChunkPos.pack(pos.x(), pos.z()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
