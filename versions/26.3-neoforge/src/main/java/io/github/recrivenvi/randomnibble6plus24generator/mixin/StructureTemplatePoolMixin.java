package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureGenerationLocks;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureTemplatePool.class)
abstract class StructureTemplatePoolMixin {
    @Unique private boolean randomnibble6plus24generator$auditedSizeCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void randomnibble6plus24generator$auditPool(CallbackInfo callback) {
        randomnibble6plus24generator$auditedSizeCache =
                StructureGenerationLocks.supportsPool((StructureTemplatePool) (Object) this);
    }

    @WrapMethod(method = "getMaxSize")
    private int randomnibble6plus24generator$sizeCache(
            StructureTemplateManager manager, Operation<Integer> original) {
        if (!randomnibble6plus24generator$auditedSizeCache) return original.call(manager);
        // Only the shared deterministic cache, not selection, RNG, height evaluation
        // or the Jigsaw graph. Pool -> template access has no inverse palette -> pool edge.
        synchronized (this) {
            return original.call(manager);
        }
    }
}
