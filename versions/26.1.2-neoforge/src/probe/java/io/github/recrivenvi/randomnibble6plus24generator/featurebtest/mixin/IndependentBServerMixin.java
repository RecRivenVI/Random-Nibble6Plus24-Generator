package io.github.recrivenvi.randomnibble6plus24generator.featurebtest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.IndependentFeatureBVerifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class IndependentBServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void skipSpawn(
            ServerLevel level,
            ServerLevelData data,
            boolean bonus,
            boolean debug,
            LevelLoadListener listener,
            CallbackInfo callback) {
        data.setInitialized(true);
        callback.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void skipPreparation(CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void verify(CallbackInfo callback) {
        IndependentFeatureBVerifier.run((MinecraftServer) (Object) this);
    }
}
