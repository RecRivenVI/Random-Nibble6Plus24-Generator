package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class PerformanceServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void initial(
            ServerLevel level,
            ServerLevelData data,
            boolean bonus,
            boolean debug,
            CallbackInfo ci) {
        if (!LegacyPerformanceProbe.enabled()) return;
        data.setInitialized(true);
        ci.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void prepare(CallbackInfo ci) {
        if (LegacyPerformanceProbe.enabled()) ci.cancel();
    }
}
