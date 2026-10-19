package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseGeneratorAccess {
    @Invoker("doFill")
    ChunkAccess mosaicDoFill(
            Blender blender,
            StructureManager structures,
            RandomState randomState,
            ChunkAccess target,
            int minCellY,
            int countY);
}
