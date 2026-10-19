package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StructureTemplatePool.class)
abstract class StructurePoolSizeMixin {
    @WrapMethod(method = "getMaxSize")
    private int mosaic$sizeCache(StructureTemplateManager manager, Operation<Integer> original) {
        if (!StructureGenerationLocks.enabled()
                || !StructureGenerationLocks.supportsPool((StructureTemplatePool) (Object) this))
            return original.call(manager);
        // The native lazy int is shared; no RNG, Jigsaw expansion or height query is locked.
        synchronized (this) {
            return original.call(manager);
        }
    }
}
