package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalGenerationPlan;
@Mixin(value=MosaicPhysicalGenerationPlan.class,remap=false)
abstract class PerfPlanMixin {
    @WrapMethod(method="derive")
    private static MosaicPhysicalGenerationPlan plan(ChunkPos pos,ChunkStatus status,Operation<MosaicPhysicalGenerationPlan> original){
        var result=original.call(pos,status);var level=PerfTrace.planLevel();
        if(level!=null){
            PerfTrace.event(level,pos,"PLAN_SHAPE","status="+status+";requirements="+result.physicalStatusRequirements().size()+";artifacts="+result.materializationObligations().size());
            result.materializationObligations().forEach((p,s)->PerfTrace.event(level,p,"PLAN_CANONICAL_DEPENDENCY",PerfTrace.key(level,pos)+"/"+status+"->"+s));
        }
        return result;
    }
}
