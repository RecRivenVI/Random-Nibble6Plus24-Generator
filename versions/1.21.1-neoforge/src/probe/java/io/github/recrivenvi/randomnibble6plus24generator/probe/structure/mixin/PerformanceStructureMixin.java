package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CompactStructureInputs.class, remap = false)
abstract class PerformanceStructureMixin {
    @WrapMethod(
            method =
                    "resolve(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/StructureGenerationContext;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/ScopedStructureSources;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/CompactStructureInputs;")
    private static CompactStructureInputs measure(
            StructureGenerationContext context,
            ScopedStructureSources reuse,
            Operation<CompactStructureInputs> original) {
        if (!LegacyPerformanceProbe.enabled()) return original.call(context, reuse);
        var before = reuse == null ? null : reuse.metrics();
        long began = System.nanoTime();
        var result = original.call(context, reuse);
        LegacyPerformanceProbe.structures(
                System.nanoTime() - began, result.metrics().evaluatedSources());
        if (reuse != null) {
            var after = reuse.metrics();
            LegacyPerformanceProbe.sourceReuse(
                    after.hits() - before.hits(),
                    after.evaluations() - before.evaluations(),
                    after.stateMismatches() - before.stateMismatches(),
                    after.stateReplays() - before.stateReplays());
        }
        return result;
    }
}
