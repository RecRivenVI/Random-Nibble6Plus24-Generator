package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Test mod only: retain the pre-repair physical workload through the existing eager Oracle mode.
 */
@Mixin(value = IsolatedGenerationContext.class, remap = false)
abstract class RepairCOracleMixin {
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static IsolatedGenerationMode eager(IsolatedGenerationMode mode) {
        boolean legacyResearch =
                Boolean.getBoolean("mosaic.lazy.experiment")
                        && !Boolean.getBoolean("mosaic.repairC");
        return Boolean.getBoolean("mosaic.repairC.oracle") || legacyResearch
                ? IsolatedGenerationMode.VANILLA_CONTROL
                : mode;
    }
}
