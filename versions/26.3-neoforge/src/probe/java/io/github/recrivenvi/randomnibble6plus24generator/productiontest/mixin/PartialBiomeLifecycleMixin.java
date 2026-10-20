package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PartialBiomeLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2ProductionVerification;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Phase3C2ProductionVerification.class, remap = false)
abstract class PartialBiomeLifecycleMixin {
    @Inject(method = "runIfRequested", at = @At("HEAD"), cancellable = true)
    private static void partial(MinecraftServer server, CallbackInfo callback) {
        if (PartialBiomeLifecycleEvidence.runIfRequested(server)) callback.cancel();
    }
}
