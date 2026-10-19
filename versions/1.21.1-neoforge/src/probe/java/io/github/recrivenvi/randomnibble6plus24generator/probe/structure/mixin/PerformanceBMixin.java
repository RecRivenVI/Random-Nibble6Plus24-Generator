package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.RuntimeLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import java.util.Map;
import java.util.function.*;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BFeaturePreparation.class, remap = false)
abstract class PerformanceBMixin {
    @WrapMethod(
            method =
                    "generate(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/StructureGenerationContext;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/profile/MosaicWorldProfile;Ljava/util/function/BiConsumer;Ljava/util/function/Consumer;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/features/BFeaturePreparation$Result;")
    private static BFeaturePreparation.Result measure(
            StructureGenerationContext context,
            MosaicWorldProfile profile,
            BiConsumer<StrictFeatureRegion, Map<Long, ProtoChunk>> before,
            Consumer<StrictFeatureBaseline.Result> observer,
            Operation<BFeaturePreparation.Result> original) {
        Consumer<StrictFeatureBaseline.Result> observed = observer;
        if (RuntimeLifecycleEvidence.enabled())
            observed =
                    result -> {
                        RuntimeLifecycleEvidence.sourceReusePrepared(result.structureReuse());
                        observer.accept(result);
                    };
        if (!LegacyPerformanceProbe.enabled())
            return original.call(context, profile, before, observed);
        LegacyPerformanceProbe.canonical(context.host(), context.target(), false, null);
        var result = original.call(context, profile, before, observed);
        LegacyPerformanceProbe.canonical(context.host(), context.target(), true, result);
        return result;
    }
}
