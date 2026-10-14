package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.SelectiveFeatureRegion;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeFeatureExecutionTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes the Vanilla decoration-seed read an explicit, audited isolated boundary. */
@Mixin(ChunkGenerator.class)
abstract class ChunkGeneratorMixin {

    @Redirect(
            method = "applyBiomeDecoration",
            at =
                    @At(
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
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setFeatureSeed(JII)V"))
    private void randomnibble6plus24generator$recordFeatureSeedSequence(
            WorldgenRandom random,
            long decorationSeed,
            int featureIndex,
            int step,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager structureManager) {
        if (level instanceof WorldGenRegion region) {
            GenerationContextRegistry.find(region)
                    .ifPresent(
                            context ->
                                    context.recordFeatureSeed(decorationSeed, featureIndex, step));
        }
        if (!(level instanceof WorldGenRegion region)
                || GenerationContextRegistry.find(region).isEmpty()) {
            NativeFeatureExecutionTrace.recordFeatureSeed(decorationSeed, featureIndex, step);
        }
        random.setFeatureSeed(decorationSeed, featureIndex, step);
        if (level instanceof SelectiveFeatureRegion selective)
            selective.recordSlot(decorationSeed, featureIndex, step);
    }

    @WrapOperation(
            method = "applyBiomeDecoration",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/placement/FeaturePlacer;placeWithBiomeCheck(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean randomnibble6plus24generator$selectiveTreeSlot(
            FeaturePlacer placer,
            PlacedFeature feature,
            RandomSource random,
            BlockPos origin,
            Operation<Boolean> original,
            WorldGenLevel level,
            ChunkAccess chunk,
            StructureManager manager) {
        if (level instanceof SelectiveFeatureRegion selective)
            return selective.place(
                    feature, random, origin, () -> original.call(placer, feature, random, origin));
        return original.call(placer, feature, random, origin);
    }
}
