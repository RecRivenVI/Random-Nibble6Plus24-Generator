package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MosaicPhysicalGenerationPlan.class, remap = false)
abstract class PerfPlanMixin {
    @WrapMethod(
            method =
                    "derive(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/materialization/MosaicPhysicalGenerationPlan;")
    private static MosaicPhysicalGenerationPlan plan(
            ChunkPos pos,
            ChunkStatus status,
            boolean partialBiomes,
            Operation<MosaicPhysicalGenerationPlan> original) {
        var result = original.call(pos, status, partialBiomes);
        var level = PerfTrace.planLevel();
        if (level != null) {
            PerfTrace.event(
                    level,
                    pos,
                    "PLAN_SHAPE",
                    "status="
                            + status
                            + ";requirements="
                            + result.physicalStatusRequirements().size()
                            + ";artifacts="
                            + result.materializationObligations().size()
                            + ";partialBiomes="
                            + partialBiomes
                            + ";requiredStages="
                            + counts(result.physicalStatusRequirements())
                            + ";obligationStages="
                            + counts(result.materializationObligations()));
        }
        return result;
    }

    private static java.util.Map<String, Integer> counts(
            java.util.Map<ChunkPos, ChunkStatus> stages) {
        var result = new java.util.TreeMap<String, Integer>();
        stages.values().forEach(stage -> result.merge(stage.getName(), 1, Integer::sum));
        return result;
    }
}
