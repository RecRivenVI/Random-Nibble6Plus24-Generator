package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PublicationInventory;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class PublicationInventoryMixin {
    @Inject(method = "runServer", at = @At("HEAD"))
    private void install(CallbackInfo callback) {
        PublicationInventory.install((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void close(CallbackInfo callback) {
        PublicationInventory.close();
    }
}
