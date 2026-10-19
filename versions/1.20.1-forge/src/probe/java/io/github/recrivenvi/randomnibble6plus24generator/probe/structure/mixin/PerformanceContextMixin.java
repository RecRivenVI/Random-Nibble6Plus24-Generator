package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StructureGenerationContext.class, remap = false)
abstract class PerformanceContextMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void created(ServerLevel host, long seed, ChunkPos target, CallbackInfo ci) {
        LegacyPerformanceProbe.context(
                ((StructureGenerationContext) (Object) this).narrowTemplateLocks());
        if (LegacyPerformanceProbe.enabled()) {
            var context = (StructureGenerationContext) (Object) this;
            var scope =
                    ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                            .ConcentricRingStateAccess)
                                    context.state())
                            .mosaic$ringScope();
            if (scope != null) LegacyPerformanceProbe.scopedRing(true, scope.metrics());
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void closed(CallbackInfo ci) {
        if (!LegacyPerformanceProbe.enabled()) return;
        var context = (StructureGenerationContext) (Object) this;
        var scope =
                ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure
                                        .ConcentricRingStateAccess)
                                context.state())
                        .mosaic$ringScope();
        if (scope != null) LegacyPerformanceProbe.scopedRing(false, scope.metrics());
    }
}
