package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.performance.StructureReuseHistory;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ScopedStructureSources.class, remap = false)
abstract class PerfStructureReuseAuditMixin {
    @Shadow private NativeBiomeSearchState biomeState;

    @Shadow
    public abstract ScopedStructureSources.Metrics metrics();

    @Unique private final StructureReuseHistory mosaic$history = new StructureReuseHistory();
    @Unique private boolean mosaic$observed;

    @WrapOperation(
            method =
                    "source(Lnet/minecraft/world/level/LevelHeightAccessor;JLnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/structure/StructureInputChunk;",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"))
    private Object observe(
            Supplier<?> evaluate,
            Operation<Object> original,
            @Local(argsOnly = true, ordinal = 1) ChunkPos source) {
        if (!mosaic$observed) {
            mosaic$observed = true;
            PerfTrace.count("reuseAudit.openScopes", 1);
        }
        Object input = ((PerfNativeBiomeStateAccessor) (Object) biomeState).mosaic$captureInput();
        long began = System.nanoTime();
        var result = original.call(evaluate);
        long nanos = System.nanoTime() - began;
        mosaic$history.observe(source.pack(), input, nanos);
        return result;
    }

    @WrapMethod(method = "handoffForDecoration")
    private void handoff(ChunkPos owner, Set<Long> retained, Operation<Void> original) {
        original.call(owner, retained);
        mosaic$history.invalidate(retained);
    }

    @WrapMethod(method = "close")
    private void close(Operation<Void> original) {
        try {
            original.call();
        } finally {
            if (mosaic$observed) {
                PerfTrace.count(
                        "reuseAudit.evaluationCountMismatch",
                        Math.abs(
                                metrics().evaluations()
                                        - mosaic$history.counters().get("evaluations")));
                mosaic$history
                        .counters()
                        .forEach((key, value) -> PerfTrace.count("reuseAudit." + key, value));
                mosaic$history.clear();
                PerfTrace.count(
                        "reuseAudit.retainedAfterClose", mosaic$history.retainedPositions());
                PerfTrace.count("reuseAudit.closedScopes", 1);
                mosaic$observed = false;
            }
        }
    }
}
