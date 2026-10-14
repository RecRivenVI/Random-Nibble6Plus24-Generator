package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BFeaturePreparation.class, remap = false)
abstract class PerfBPreparationMixin {
    @Invoker("generatePrepared")
    static BFeaturePreparation.Result referencePreparation(
            IsolatedGenerationContext context,
            MosaicWorldProfile profile,
            boolean reuseStructures) {
        throw new AssertionError("Mixin invoker not installed");
    }

    @WrapMethod(method = "generate")
    private static BFeaturePreparation.Result measure(
            IsolatedGenerationContext context,
            MosaicWorldProfile profile,
            Operation<BFeaturePreparation.Result> original) {
        var span = PerfTrace.begin(context.hostLevel(), context.target(), "CANONICAL");
        try {
            var result =
                    Boolean.getBoolean("mosaic.profile.bReferenceStructures")
                            ? referencePreparation(context, profile, false)
                            : original.call(context, profile);
            PerfTrace.count("b.preparationNanos", result.preparationNanos());
            PerfTrace.count("b.decorationNanos", result.decorationNanos());
            PerfTrace.count("b.fullNeighborFallbacks", result.reads().fullNeighbors());
            var columns = result.reads().columns();
            PerfTrace.count("b.localNoiseBatches", columns.noiseBatches());
            PerfTrace.count("b.localMaterialBatches", columns.materialBatches());
            PerfTrace.count("b.localColumns", columns.columns());
            PerfTrace.count("b.localRawSamples", columns.rawSamples());
            PerfTrace.count("b.heightOnlyQueries", columns.heightOnlyQueries());
            PerfTrace.count("b.heightCacheHits", columns.heightCacheHits());
            if (result.structureReuse() != null) {
                PerfTrace.count("b.structureSourceQueries", result.structureReuse().queries());
                PerfTrace.count("b.structureSourceHits", result.structureReuse().hits());
                PerfTrace.count(
                        "b.structureSourceEvaluations", result.structureReuse().evaluations());
                PerfTrace.count(
                        "b.structureSourceEvaluationNanos",
                        result.structureReuse().evaluationNanos());
            }
            return result;
        } finally {
            PerfTrace.end(context.hostLevel(), context.target(), "CANONICAL", span);
        }
    }
}
