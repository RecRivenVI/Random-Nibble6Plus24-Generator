package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Structure.class)
abstract class StructureGenerationMixin {
    @WrapMethod(method = "generate")
    private StructureStart mosaic$sharedPieces(
            RegistryAccess registry,
            ChunkGenerator generator,
            BiomeSource biomes,
            RandomState randomState,
            StructureTemplateManager templates,
            long seed,
            ChunkPos position,
            int references,
            LevelHeightAccessor height,
            Predicate<Holder<Biome>> validBiome,
            Operation<StructureStart> original) {
        Object lock = StructureGenerationLocks.sharedPieceLock((Structure) (Object) this);
        if (lock == null || !StructureGenerationLocks.enabled())
            return original.call(
                    registry,
                    generator,
                    biomes,
                    randomState,
                    templates,
                    seed,
                    position,
                    references,
                    height,
                    validBiome);
        synchronized (lock) {
            return original.call(
                    registry,
                    generator,
                    biomes,
                    randomState,
                    templates,
                    seed,
                    position,
                    references,
                    height,
                    validBiome);
        }
    }
}
