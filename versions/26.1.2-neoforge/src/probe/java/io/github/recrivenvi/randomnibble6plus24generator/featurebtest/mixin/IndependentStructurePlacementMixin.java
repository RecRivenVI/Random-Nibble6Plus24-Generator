package io.github.recrivenvi.randomnibble6plus24generator.featurebtest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.IndependentFeatureBVerifier;
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
abstract class IndependentStructurePlacementMixin {
    @Inject(method = "placeInChunk", at = @At("HEAD"))
    private void observe(
            WorldGenLevel world,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos target,
            CallbackInfo callback) {
        IndependentFeatureBVerifier.observeStructure((StructureStart) (Object) this, world, target);
    }
}
