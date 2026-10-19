package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
abstract class PerformanceMapMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "schedule", at = @At("HEAD"))
    private void begin(
            ChunkHolder holder,
            ChunkStatus status,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    ci) {
        LegacyPerformanceProbe.scheduled(level, holder.getPos(), status);
        LegacyPerformanceProbe.step(level, holder.getPos(), status, false);
    }

    @Inject(method = "schedule", at = @At("RETURN"))
    private void end(
            ChunkHolder holder,
            ChunkStatus status,
            CallbackInfoReturnable<
                            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
                    ci) {
        if (!LegacyPerformanceProbe.enabled()) return;
        ci.getReturnValue()
                .whenComplete(
                        (chunk, error) -> {
                            if (error != null) LegacyPerformanceProbe.failed(error);
                            else if (chunk.left().isPresent())
                                LegacyPerformanceProbe.step(level, holder.getPos(), status, true);
                        });
    }
}
