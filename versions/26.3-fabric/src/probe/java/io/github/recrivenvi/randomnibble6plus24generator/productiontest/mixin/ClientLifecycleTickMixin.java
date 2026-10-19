package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.ClientLifecycleEvidence;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class ClientLifecycleTickMixin {
    @Inject(method = "tickChildren", at = @At("RETURN"))
    private void tick(java.util.function.BooleanSupplier haveTime, CallbackInfo callback) {
        ClientLifecycleEvidence.tick((MinecraftServer) (Object) this);
    }
}
