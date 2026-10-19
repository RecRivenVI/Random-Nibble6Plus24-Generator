package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureBaseline;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CanonicalChunkArtifact.class, remap = false)
abstract class PerformanceArtifactMixin {
    @Inject(method = "capture", at = @At("HEAD"))
    private static void captureBegin(
            StrictFeatureBaseline.Result result,
            String dimension,
            long seed,
            MosaicWorldProfile profile,
            RegistryAccess registry,
            StructurePieceSerializationContext structures,
            CallbackInfoReturnable<CanonicalChunkArtifact> callback) {
        LegacyPerformanceProbe.phase(dimension, result.target().getPos(), "ARTIFACT_BEGIN");
    }

    @Inject(method = "capture", at = @At("RETURN"))
    private static void captureEnd(
            StrictFeatureBaseline.Result result,
            String dimension,
            long seed,
            MosaicWorldProfile profile,
            RegistryAccess registry,
            StructurePieceSerializationContext structures,
            CallbackInfoReturnable<CanonicalChunkArtifact> callback) {
        LegacyPerformanceProbe.phase(dimension, result.target().getPos(), "ARTIFACT_END");
    }

    @Inject(method = "rehydrate", at = @At("HEAD"))
    private void materializeBegin(
            String dimension,
            ChunkPos position,
            long seed,
            MosaicWorldProfile profile,
            LevelHeightAccessor heights,
            RegistryAccess registry,
            StructurePieceSerializationContext structures,
            CallbackInfoReturnable<ProtoChunk> callback) {
        LegacyPerformanceProbe.phase(dimension, position, "MATERIALIZE_BEGIN");
    }

    @Inject(method = "rehydrate", at = @At("RETURN"))
    private void materializeEnd(
            String dimension,
            ChunkPos position,
            long seed,
            MosaicWorldProfile profile,
            LevelHeightAccessor heights,
            RegistryAccess registry,
            StructurePieceSerializationContext structures,
            CallbackInfoReturnable<ProtoChunk> callback) {
        LegacyPerformanceProbe.phase(dimension, position, "MATERIALIZE_END");
    }
}
