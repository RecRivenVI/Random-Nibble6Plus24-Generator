package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.PreFeaturesVerifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Activated only by the dedicated pre-decoration validation, never in a daily instance. */
@Mixin(MinecraftServer.class)
abstract class PreFeaturesServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void noInitialDecoration(
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
    private void noSpawnPreparation(CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void verify(CallbackInfo callback) {
        PreFeaturesVerifier.run((MinecraftServer) (Object) this);
    }
}
