package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts actual preparation and asynchronous biome-search invocations, not accessor calls. */
@Mixin(ChunkGeneratorStructureState.class)
abstract class FeatureRingMixin {
    @Inject(method = "generateRingPositions", at = @At("HEAD"))
    private void rings(
            Holder<StructureSet> set,
            ConcentricRingsStructurePlacement placement,
            CallbackInfoReturnable<CompletableFuture<List<ChunkPos>>> callback) {
        NativeFeatureVerifier.ringPreparation();
        io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe
                .ring(false);
    }

    @Inject(method = "lambda$generateRingPositions$5", at = @At("HEAD"))
    private void biomeSearch(
            int x,
            int z,
            HolderSet<Biome> preferred,
            RandomSource random,
            CallbackInfoReturnable<ChunkPos> callback) {
        NativeFeatureVerifier.ringBiomeSearch();
        io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe
                .ring(true);
    }
}
