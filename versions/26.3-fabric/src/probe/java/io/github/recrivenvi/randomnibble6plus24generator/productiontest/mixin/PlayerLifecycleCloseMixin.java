package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.ClientLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PlayerLifecycleEvidence;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class PlayerLifecycleCloseMixin {
    @Inject(method = "stopServer", at = @At("RETURN"))
    private void closed(CallbackInfo callback) {
        PlayerLifecycleEvidence.closed((MinecraftServer) (Object) this);
        ClientLifecycleEvidence.closed((MinecraftServer) (Object) this);
    }
}
