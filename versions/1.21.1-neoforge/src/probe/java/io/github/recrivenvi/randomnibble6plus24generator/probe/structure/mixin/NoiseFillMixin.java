package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.terrain.NoiseDensityEvidence;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseFillMixin {
    @WrapMethod(method = "doFill")
    private ChunkAccess capture(
            Blender blender,
            StructureManager structures,
            RandomState randomState,
            ChunkAccess target,
            int minCellY,
            int countY,
            Operation<ChunkAccess> original) {
        try (var capture = NoiseDensityEvidence.open(target.getPos())) {
            return original.call(blender, structures, randomState, target, minCellY, countY);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
