package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ListPoolElement.class)
public interface StructurePoolListAccess {
    @Accessor("elements")
    List<StructurePoolElement> mosaic$elements();
}
