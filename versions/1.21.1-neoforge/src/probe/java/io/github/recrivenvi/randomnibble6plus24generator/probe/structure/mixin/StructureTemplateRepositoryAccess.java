package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureTemplateManager.class)
public interface StructureTemplateRepositoryAccess {
    @Accessor("structureRepository")
    Map<ResourceLocation, Optional<StructureTemplate>> mosaic$repository();
}
