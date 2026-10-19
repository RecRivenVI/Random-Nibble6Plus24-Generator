package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Native picker/mask call bridges; no interception of normal-world generation. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
    @Accessor("globalFluidPicker")
    Supplier<Aquifer.FluidPicker> randomnibble6plus24generator$globalFluidPicker();

    @Invoker("applyCarvingMask")
    void randomnibble6plus24generator$applyColumnMask(
            ChunkAccess chunk,
            CarvingMask mask,
            RandomState random,
            MaterialRule rule,
            WorldGenerationContext context,
            NoiseChunk noise,
            Function<BlockPos, Holder<Biome>> biomes,
            CarvingMask.Filter filter);
}
