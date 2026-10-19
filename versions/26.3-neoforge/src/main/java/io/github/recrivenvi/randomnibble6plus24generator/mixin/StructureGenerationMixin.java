package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Structure.class)
abstract class StructureGenerationMixin {
    @WrapMethod(method = "generate")
    private StructureStart randomnibble6plus24generator$sharedPieces(
            Holder<Structure> structure,
            ResourceKey<Level> dimension,
            RegistryAccess registry,
            ChunkGenerator generator,
            BiomeSource biomes,
            Climate.Sampler climate,
            RandomState randomState,
            StructureTemplateManager templates,
            long seed,
            ChunkPos position,
            int references,
            LevelHeightAccessor height,
            Predicate<Holder<Biome>> validBiome,
            Operation<StructureStart> original) {
        Object lock = StructureGenerationLocks.sharedPieceLock((Structure) (Object) this);
        if (lock == null)
            return original.call(
                    structure,
                    dimension,
                    registry,
                    generator,
                    biomes,
                    climate,
                    randomState,
                    templates,
                    seed,
                    position,
                    references,
                    height,
                    validBiome);
        // Protect reset + expansion together. Locking only resetPieces/start constructors
        // would still let another structure overwrite the static state during expansion.
        synchronized (lock) {
            return original.call(
                    structure,
                    dimension,
                    registry,
                    generator,
                    biomes,
                    climate,
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
