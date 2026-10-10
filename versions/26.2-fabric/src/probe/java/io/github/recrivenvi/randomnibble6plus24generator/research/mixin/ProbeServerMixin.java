package io.github.recrivenvi.randomnibble6plus24generator.research.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.research.LightweightArchitectureProbe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loaded only by -PlightweightProbe. Never present in the production jar. */
@Mixin(MinecraftServer.class)
abstract class ProbeServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void skipHostSpawn(
            ServerLevel level,
            ServerLevelData data,
            boolean bonus,
            boolean debug,
            LevelLoadListener listener,
            CallbackInfo ci) {
        data.setInitialized(true);
        ci.cancel();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void research(CallbackInfo ci) {
        LightweightArchitectureProbe.run((MinecraftServer) (Object) this);
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void skipHostPreparation(CallbackInfo ci) {
        ci.cancel();
    }
}
