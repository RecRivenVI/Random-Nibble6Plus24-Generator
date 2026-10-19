package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
abstract class FeatureStructureMixin {
    @Inject(method = "placeInChunk", at = @At("HEAD"))
    private void placement(
            WorldGenLevel level,
            StructureManager manager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos owner,
            CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled())
            NativeFeatureVerifier.structurePlacement((StructureStart) (Object) this, level, owner);
    }

    @Inject(method = "placeInChunk", at = @At("RETURN"))
    private void completed(
            WorldGenLevel level,
            StructureManager manager,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos owner,
            CallbackInfo callback) {
        if (NativeFeatureVerifier.enabled()) NativeFeatureVerifier.structurePlacementCompleted();
    }
}
