package io.github.recrivenvi.randomnibble6plus24generator.identitytest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.identitytest.IdentityLifecycleVerifier;

/** Test-only: identity lifecycle needs real levels/storage, but no terrain generation. */
@Mixin(value = MinecraftServer.class, priority = 500)
abstract class IdentityTestServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void skipTerrain(ServerLevel level, ServerLevelData data, boolean bonus,
            boolean debug, LevelLoadListener listener, CallbackInfo callback) {
        data.setInitialized(true);
        callback.cancel();
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void verify(CallbackInfo callback) {
        IdentityLifecycleVerifier.verify((MinecraftServer) (Object) this);
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void noPreparation(CallbackInfo callback) { callback.cancel(); }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void finish(java.util.function.BooleanSupplier time, CallbackInfo callback) {
        ((MinecraftServer) (Object) this).halt(false);
    }

    @Inject(method = "runServer", at = @At("RETURN"))
    private void closed(CallbackInfo callback) {
        IdentityLifecycleVerifier.verifyAfterServerStopped((MinecraftServer) (Object) this);
    }
}
