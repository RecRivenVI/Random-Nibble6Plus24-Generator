package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class PhysicalServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void coldSpawn(
            ServerLevel level,
            ServerLevelData data,
            boolean bonus,
            boolean debug,
            CallbackInfo callback) {
        if (!NativePhysicalProbe.enabled()) return;
        data.setInitialized(true);
        callback.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void coldPreparation(CallbackInfo callback) {
        if (NativePhysicalProbe.enabled()) callback.cancel();
    }
}
