package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import net.minecraft.server.level.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class PerformanceTicketSourceMixin {
    @Inject(method = "fudgeSpawnLocation", at = @At("HEAD"), cancellable = true)
    private void source(ServerLevel level, CallbackInfo ci) {
        if (!LegacyPerformanceProbe.constructingTicketSource()) return;
        LegacyPerformanceProbe.tokenRespawnSkipped();
        ci.cancel();
    }
}
