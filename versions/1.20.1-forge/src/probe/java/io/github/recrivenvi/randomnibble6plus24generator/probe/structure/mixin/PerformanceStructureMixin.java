package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompactStructureInputs.class, remap = false)
abstract class PerformanceStructureMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void completed(StructureGenerationContext context, CallbackInfo ci) {
        if (!LegacyPerformanceProbe.enabled()) return;
        var metrics = ((CompactStructureInputs) (Object) this).metrics();
        LegacyPerformanceProbe.structures(metrics.elapsedNanos(), metrics.evaluatedSources());
    }
}
