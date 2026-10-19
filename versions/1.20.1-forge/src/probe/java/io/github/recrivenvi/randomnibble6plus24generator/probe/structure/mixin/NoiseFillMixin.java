package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.terrain.NoiseDensityEvidence;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseFillMixin {
    // The native async lambda has one doFill call. Match that operation, not its synthetic name.
    @Redirect(
            method = "*",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;doFill(Lnet/minecraft/world/level/levelgen/blending/Blender;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;II)Lnet/minecraft/world/level/chunk/ChunkAccess;"),
            require = 1,
            allow = 1)
    private ChunkAccess capture(
            NoiseBasedChunkGenerator generator,
            Blender blender,
            StructureManager structures,
            RandomState randomState,
            ChunkAccess target,
            int minCellY,
            int countY) {
        try (var capture = NoiseDensityEvidence.open(target.getPos())) {
            return ((NoiseGeneratorAccess) generator)
                    .mosaicDoFill(blender, structures, randomState, target, minCellY, countY);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
