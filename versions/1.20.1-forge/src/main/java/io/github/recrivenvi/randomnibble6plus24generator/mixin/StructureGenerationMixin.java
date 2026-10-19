package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ChunkGenerator.class)
abstract class StructureGenerationMixin
        implements io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                .NativeStructureSynchronization {
    @Redirect(
            method = "tryGenerateStructure",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private StructureStart generate(
            Structure structure,
            RegistryAccess registry,
            ChunkGenerator generator,
            BiomeSource biomes,
            RandomState randomState,
            StructureTemplateManager templates,
            long seed,
            ChunkPos position,
            int references,
            LevelHeightAccessor height,
            Predicate<Holder<Biome>> validBiome) {
        var lock = StructureGenerationLocks.sharedPieceLock(structure);
        if (lock == null || !StructureGenerationLocks.enabled())
            return structure.generate(
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
            return structure.generate(
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
