package io.github.recrivenvi.randomnibble6plus24generator.strongholdtest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.recrivenvi.randomnibble6plus24generator.strongholdtest.StrongholdParityVerifier;

/** Only the explicitly selected test mod loads this fixture. No physical terrain is required. */
@Mixin(MinecraftServer.class)
abstract class StrongholdTestServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void noSpawnTerrain(ServerLevel level, ServerLevelData data, boolean bonus,
            boolean debug, LevelLoadListener listener, CallbackInfo callback) {
        data.setInitialized(true);
        callback.cancel();
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void noPreparation(CallbackInfo callback) { callback.cancel(); }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void verify(CallbackInfo callback) {
        if (System.getProperty("mosaic.stronghold.test.lifecycle", "").isBlank())
            StrongholdParityVerifier.run((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void lifecycle(java.util.function.BooleanSupplier time, CallbackInfo callback) {
        if (!System.getProperty("mosaic.stronghold.test.lifecycle", "").isBlank())
            io.github.recrivenvi.randomnibble6plus24generator.strongholdtest.StrongholdLifecycleVerifier.run((MinecraftServer) (Object) this);
    }
}
