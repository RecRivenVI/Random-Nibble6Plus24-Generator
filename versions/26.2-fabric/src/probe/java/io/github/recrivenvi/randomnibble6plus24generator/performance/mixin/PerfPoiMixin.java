package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalPoiReconciler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MosaicPhysicalPoiReconciler.class, remap = false)
abstract class PerfPoiMixin {
    @WrapMethod(method = "reconcile")
    private static MosaicPhysicalPoiReconciler.Reconciliation poi(
            ServerLevel level,
            ChunkAccess chunk,
            Operation<MosaicPhysicalPoiReconciler.Reconciliation> original) {
        var span = PerfTrace.begin(level, chunk.getPos(), "POI");
        try {
            return original.call(level, chunk);
        } finally {
            PerfTrace.end(level, chunk.getPos(), "POI", span);
        }
    }
}
