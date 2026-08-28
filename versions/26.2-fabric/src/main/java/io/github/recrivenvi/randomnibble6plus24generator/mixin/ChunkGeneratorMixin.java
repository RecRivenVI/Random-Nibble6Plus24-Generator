package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.WorldgenRandom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeFeatureExecutionTrace;

/** Makes the Vanilla decoration-seed read an explicit, audited isolated boundary. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @Redirect(
            method = "applyBiomeDecoration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/WorldGenLevel;getSeed()J"))
    private long randomnibble6plus24generator$useIsolatedDecorationSeed(WorldGenLevel level) {
        if (level instanceof WorldGenRegion region) {
            var context = GenerationContextRegistry.find(region);
            if (context.isPresent()) {
                context.get().recordDecorationSeedRead(context.get().worldSeed());
                return context.get().worldSeed();
            }
        }
        NativeFeatureExecutionTrace.recordDecorationSeedRead();
        return level.getSeed();
    }

    @Redirect(
            method = "applyBiomeDecoration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setFeatureSeed(JII)V"))
    private void randomnibble6plus24generator$recordFeatureSeedSequence(
            WorldgenRandom random,
            long decorationSeed,
            int featureIndex,
            int step,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        if (level instanceof WorldGenRegion region) {
            GenerationContextRegistry.find(region).ifPresent(context ->
                    context.recordFeatureSeed(decorationSeed, featureIndex, step));
        }
        if (!(level instanceof WorldGenRegion region)
                || GenerationContextRegistry.find(region).isEmpty()) {
            NativeFeatureExecutionTrace.recordFeatureSeed(decorationSeed, featureIndex, step);
        }
        random.setFeatureSeed(decorationSeed, featureIndex, step);
    }
}
