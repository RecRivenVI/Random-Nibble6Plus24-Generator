package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Native 1.20.1 B preparation; only detached values may outlive its owner. */
public final class BFeaturePreparation {
    private BFeaturePreparation() {}

    public record Result(
            CanonicalChunkArtifact artifact,
            SpawnBiomeSnapshot spawnBiomes,
            SpawnCollisionSnapshot spawnCollisions,
            List<CompoundTag> externalStarts,
            Map<String, Long> rejected,
            long decorationNanos) {
        public Result {
            externalStarts = externalStarts.stream().map(CompoundTag::copy).toList();
            rejected = Map.copyOf(rejected);
        }

        @Override
        public List<CompoundTag> externalStarts() {
            return externalStarts.stream().map(CompoundTag::copy).toList();
        }
    }

    public static Result generate(StructureGenerationContext context, MosaicWorldProfile profile) {
        return generate(context, profile, (region, terrain) -> {}, result -> {});
    }

    public static Result generate(
            StructureGenerationContext context,
            MosaicWorldProfile profile,
            java.util.function.BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>>
                    beforeDecoration,
            java.util.function.Consumer<StrictFeatureBaseline.Result> observer) {
        profile.requireSupported();
        if (profile.featureOrderingAlgorithmVersion()
                != MosaicWorldProfile.FEATURE_ORDERING_TARGET_ONLY)
            throw new IllegalArgumentException("B transport requires explicit Ordering 2");
        var palettes = new AtomicReference<SpawnBiomeSnapshot>();
        var collisions = new AtomicReference<SpawnCollisionSnapshot>();
        var result =
                StrictFeatureBaseline.generate(
                        context,
                        (region, terrain) -> {
                            palettes.set(
                                    SpawnBiomeSnapshot.capture(
                                            context.host().dimension(),
                                            context.target(),
                                            context.seed(),
                                            profile,
                                            pos -> terrain.get(pos.toLong())));
                            collisions.set(
                                    SpawnCollisionSnapshot.capture(
                                            palettes.get(), pos -> terrain.get(pos.toLong())));
                            beforeDecoration.accept(region, terrain);
                        });
        observer.accept(result);
        var serialization = StructurePieceSerializationContext.fromLevel(context.host());
        return new Result(
                CanonicalChunkArtifact.capture(
                        result,
                        context.host().dimension().location().toString(),
                        context.seed(),
                        profile,
                        context.host().registryAccess(),
                        serialization),
                Objects.requireNonNull(palettes.get()),
                Objects.requireNonNull(collisions.get()),
                result.structures().captureStartTags(serialization),
                result.rejected(),
                result.decorationNanos());
    }
}
