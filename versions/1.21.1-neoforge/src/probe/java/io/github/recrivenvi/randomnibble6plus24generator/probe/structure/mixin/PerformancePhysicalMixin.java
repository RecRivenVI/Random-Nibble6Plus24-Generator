package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import java.util.concurrent.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MosaicPhysicalLevelState.class, remap = false)
abstract class PerformancePhysicalMixin {
    @Shadow @Final private ServerLevel level;

    @WrapOperation(
            method = "prepare",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;runAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> accepted(
            Runnable action,
            Executor executor,
            Operation<CompletableFuture<Void>> original,
            @Local(argsOnly = true) ChunkPos position) {
        return original.call(
                LegacyPerformanceProbe.worker(
                        level, position, (MosaicPhysicalLevelState) (Object) this, action),
                executor);
    }
}
