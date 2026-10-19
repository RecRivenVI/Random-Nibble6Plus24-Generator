package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** This Target's exact B module, detached for the existing physical lifecycle. */
public final class BFeaturePreparation {
    private BFeaturePreparation() {}

    public record Result(
            FeatureStableGenerationRun run,
            SpawnBiomeSnapshot spawnBiomes,
            List<CompoundTag> externalStarts,
            Map<String, Long> rejected,
            long preparationNanos,
            long decorationNanos) {}

    public static Result generate(IsolatedGenerationContext context, MosaicWorldProfile profile) {
        return generate(context, profile, prepared -> {});
    }

    /** Heavy input evidence is supplied only by an explicit probe, not daily generation. */
    public static Result generate(
            IsolatedGenerationContext context,
            MosaicWorldProfile profile,
            java.util.function.Consumer<StrictFeatureBaseline.Result> observer) {
        profile.requireSupported();
        if (profile.featureOrderingAlgorithmVersion()
                != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)
            throw new IllegalArgumentException("Standalone B handoff requires Ordering 2");
        long began = System.nanoTime();
        int physicalBefore = context.hostLevel().getChunkSource().getLoadedChunksCount();
        var spawn = new AtomicReference<SpawnBiomeSnapshot>();
        var prepared =
                StrictFeatureBaseline.generate(
                        context,
                        (region, terrain) -> {
                            spawn.set(
                                    SpawnBiomeSnapshot.capture(
                                            context.dimension(),
                                            context.target(),
                                            context.worldSeed(),
                                            profile,
                                            pos -> terrain.get(pos.pack())));
                            var visible =
                                    new HashSet<
                                            net.minecraft.core.Holder<
                                                    net.minecraft.world.level.biome.Biome>>();
                            for (var chunk : terrain.values())
                                for (var section : chunk.getSections())
                                    section.getBiomes().getAll(visible::add);
                            var registry =
                                    context.hostLevel()
                                            .registryAccess()
                                            .lookupOrThrow(Registries.BIOME);
                            context.recordFeatureVisibleBiomes(
                                    context.target()
                                            + "="
                                            + visible.stream()
                                                    .map(
                                                            biome ->
                                                                    registry.getKey(biome.value())
                                                                            .toString())
                                                    .sorted()
                                                    .toList());
                        });
        observer.accept(prepared);
        for (var stage :
                List.of(
                        ChunkStatus.STRUCTURE_STARTS,
                        ChunkStatus.STRUCTURE_REFERENCES,
                        ChunkStatus.BIOMES,
                        ChunkStatus.NOISE,
                        ChunkStatus.SURFACE,
                        ChunkStatus.CARVERS,
                        ChunkStatus.FEATURES)) context.recordStage(stage);
        if (physicalBefore != context.hostLevel().getChunkSource().getLoadedChunksCount())
            throw new IllegalStateException("B preparation loaded physical neighbours");
        var starts =
                prepared.structures()
                        .captureStartTags(
                                StructurePieceSerializationContext.fromLevel(context.hostLevel()));
        int inputs = prepared.terrain().size() + prepared.region().outerReadInputs().size();
        long elapsed = System.nanoTime() - began;
        return new Result(
                context.captureStandaloneFeatures(
                        prepared.target(), elapsed, inputs, physicalBefore),
                Objects.requireNonNull(spawn.get()),
                starts,
                prepared.rejected(),
                elapsed - prepared.decorationNanos(),
                prepared.decorationNanos());
    }
}
