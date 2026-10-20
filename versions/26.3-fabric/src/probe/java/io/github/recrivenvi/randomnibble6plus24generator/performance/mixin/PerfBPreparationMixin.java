package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BFeaturePreparation.class, remap = false)
abstract class PerfBPreparationMixin {
    @WrapMethod(method = "generateInput")
    private static BFeaturePreparation.Result measure(
            IsolatedGenerationContext context,
            MosaicWorldProfile profile,
            boolean reuseStructures,
            ProtoChunk terrain,
            Operation<BFeaturePreparation.Result> original) {
        var span = PerfTrace.begin(context.hostLevel(), context.target(), "CANONICAL");
        try {
            boolean referenceStructures = Boolean.getBoolean("mosaic.profile.bReferenceStructures");
            var result =
                    original.call(
                            context,
                            profile,
                            referenceStructures ? false : reuseStructures,
                            referenceStructures ? null : terrain);
            PerfTrace.count("b.retainedTerrain", result.retainedTerrain() ? 1 : 0);
            if (System.getProperty("mosaic.profile.output") != null) {
                var json = new com.google.gson.Gson();
                var work = new com.google.gson.JsonObject();
                work.addProperty("schema", 1);
                work.addProperty("retainedTerrain", result.retainedTerrain());
                work.addProperty("preparationNanos", result.preparationNanos());
                work.addProperty("decorationNanos", result.decorationNanos());
                work.add("reads", json.toJsonTree(result.reads()));
                work.add("structureReuse", json.toJsonTree(result.structureReuse()));
                work.add("biomeReuse", json.toJsonTree(result.biomeReuse()));
                PerfTrace.event(
                        context.hostLevel(), context.target(), "B_WORK_METRICS", work.toString());
            }
            PerfTrace.count("b.preparationNanos", result.preparationNanos());
            PerfTrace.count("b.decorationNanos", result.decorationNanos());
            PerfTrace.count("b.fullNeighborFallbacks", result.reads().fullNeighbors());
            var columns = result.reads().columns();
            PerfTrace.count("b.localNoiseBatches", columns.noiseBatches());
            PerfTrace.count("b.localMaterialBatches", columns.materialBatches());
            PerfTrace.count("b.localColumns", columns.columns());
            PerfTrace.count("b.localRawSamples", columns.rawSamples());
            PerfTrace.count("b.localDensitySamples", columns.densitySamples());
            PerfTrace.count("b.localDensityTiles", columns.densityTiles());
            PerfTrace.count("b.localDensitySourceSamples", columns.densitySourceSamples());
            PerfTrace.count("b.heightOnlyQueries", columns.heightOnlyQueries());
            PerfTrace.count("b.heightCacheHits", columns.heightCacheHits());
            if (result.biomeReuse() != null) {
                var biomes = result.biomeReuse();
                PerfTrace.count("b.biomeRequests", biomes.requests());
                PerfTrace.count("b.biomeReuseHits", biomes.hits());
                PerfTrace.count("b.biomeNativeBuilds", biomes.nativeBuilds());
                PerfTrace.count("b.biomeTargetCopies", biomes.targetCopies());
                PerfTrace.count("b.biomePreparationHits", biomes.preparationHits());
            }
            if (result.structureReuse() != null) {
                PerfTrace.count(
                        "b.structureStateMismatches", result.structureReuse().stateMismatches());
                PerfTrace.count("b.structureStateReplays", result.structureReuse().stateReplays());
                PerfTrace.count(
                        "b.structureUniqueSources", result.structureReuse().uniqueSources());
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
