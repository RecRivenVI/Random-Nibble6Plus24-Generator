package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureTemplatePool.class)
public interface StructurePoolColdAccess {
    @Accessor("maxSize")
    int mosaic$maxSize();
}
