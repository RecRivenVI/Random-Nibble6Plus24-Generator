package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain.TerrainBiomeInputs;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Detached B input for the existing physical lifecycle, scoped to explicitly marked new worlds. */
public final class BFeaturePreparation {
    private BFeaturePreparation() {}

    public record Result(
            FeatureStableGenerationRun run,
            SpawnBiomeSnapshot spawnBiomes,
            List<CompoundTag> externalStarts,
            LocalFeatureInputs.Counters reads,
            ScopedStructureSources.Metrics structureReuse,
            TerrainBiomeInputs.Counters biomeReuse,
            Map<String, Long> levelEscapes,
            long preparationNanos,
            long decorationNanos) {}

    public static Result generate(IsolatedGenerationContext context, MosaicWorldProfile profile) {
        return generatePrepared(context, profile, true);
    }

    private static Result generatePrepared(
            IsolatedGenerationContext context,
            MosaicWorldProfile profile,
            boolean reuseStructures) {
        if (profile.featureOrderingAlgorithmVersion()
                != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)
            throw new IllegalArgumentException("B development handoff requires Ordering 2");
        long begin = System.nanoTime();
        int loadedBefore = context.hostLevel().getChunkSource().getLoadedChunksCount();
        var spawn = new AtomicReference<SpawnBiomeSnapshot>();
        context.beginStandaloneFeatures();
        LightweightFeatureBaseline.Result result;
        try {
            result =
                    LightweightFeatureBaseline.generateForHandoff(
                            context,
                            new LocalFeatureInputs.Observer() {},
                            (region, inputs) -> {
                                context.recordFeatureWorldSeed(region.getSeed());
                                var biomes =
                                        new HashSet<
                                                net.minecraft.core.Holder<
                                                        net.minecraft.world.level.biome.Biome>>();
                                inputs.chunks()
                                        .values()
                                        .forEach(chunk -> chunk.collectBiomesInPalette(biomes));
                                var registry =
                                        context.hostLevel()
                                                .registryAccess()
                                                .lookupOrThrow(
                                                        net.minecraft.core.registries.Registries
                                                                .BIOME);
                                context.recordFeatureVisibleBiomes(
                                        context.target()
                                                + "="
                                                + biomes.stream()
                                                        .map(
                                                                b ->
                                                                        registry.getKey(b.value())
                                                                                .toString())
                                                        .sorted()
                                                        .toList());
                                spawn.set(
                                        SpawnBiomeSnapshot.capture(
                                                context.dimension(),
                                                context.target(),
                                                context.worldSeed(),
                                                profile,
                                                inputs::storedBiomeInput));
                            },
                            reuseStructures);
        } finally {
            context.completeFeatureWriter(context.target());
        }
        for (var stage :
                List.of(
                        ChunkStatus.STRUCTURE_STARTS,
                        ChunkStatus.STRUCTURE_REFERENCES,
                        ChunkStatus.BIOMES,
                        ChunkStatus.TERRAIN,
                        ChunkStatus.FEATURES)) context.recordStage(stage);
        var distribution = new TreeMap<String, Integer>();
        context.recordDiscardedFeatureWrites(
                result.rejected().values().stream().mapToLong(Long::longValue).sum());
        result.inputs()
                .fullTerrain()
                .values()
                .forEach(
                        chunk ->
                                distribution.merge(
                                        chunk.getPersistedStatus().getName(), 1, Integer::sum));
        var reads = result.inputs().counters();
        return new Result(
                context.captureStandaloneFeatures(
                        result.target(),
                        System.nanoTime() - begin,
                        1 + reads.fullNeighbors(),
                        loadedBefore,
                        distribution),
                Objects.requireNonNull(spawn.get()),
                result.structures()
                        .captureStartTags(
                                StructurePieceSerializationContext.fromLevel(context.hostLevel())),
                reads,
                result.inputs().structureReuseMetrics().orElse(null),
                result.inputs().biomeReuseCounters().orElse(null),
                result.region().levelEscapeCounts(),
                result.preparationNanos(),
                result.decorationNanos());
    }
}
