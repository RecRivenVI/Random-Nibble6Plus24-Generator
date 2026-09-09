package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.performance.StructureIsolation;

@Mixin(ChunkGenerator.class)
abstract class StructureIsolationGeneratorMixin {
    @WrapMethod(method="tryGenerateStructure")
    private boolean attempt(StructureSet.StructureSelectionEntry selected,StructureManager manager,RegistryAccess registries,
            RandomState random,StructureTemplateManager templates,long seed,ChunkAccess chunk,ChunkPos pos,SectionPos section,
            ResourceKey<Level> dimension,Operation<Boolean> original) {
        StructureIsolation.generating(selected.structure());
        boolean valid=original.call(selected,manager,registries,random,templates,seed,chunk,pos,section,dimension);
        StructureIsolation.generated(selected.structure(),valid);return valid;
    }
}
