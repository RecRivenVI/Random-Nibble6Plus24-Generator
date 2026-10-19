package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructureTemplatePool.class)
public interface StructurePoolAccess {
    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> mosaic$rawTemplates();
}
