package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2AVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2BVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1Verification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1RootCauseVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1StageBisectionHarness;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaControlHarness;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaFeatureOrderProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaFeatureControlHarness;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void randomnibble6plus24generator$armNativeControlBeforePhysicalGeneration(CallbackInfo callbackInfo) {
        NativeVanillaControlHarness.armIfRequested((MinecraftServer) (Object) this);
        NativeVanillaFeatureOrderProbe.armIfRequested((MinecraftServer) (Object) this);
        Phase2C1StageBisectionHarness.armNativeIfRequested((MinecraftServer) (Object) this);
    }

    @Inject(
            method = "createLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;setInitialSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/storage/ServerLevelData;ZZLnet/minecraft/server/level/progress/LevelLoadListener;)V",
                    shift = At.Shift.BEFORE))
    private void randomnibble6plus24generator$runNativeOriginBeforeSpawnSelection(CallbackInfo callbackInfo) {
        NativeVanillaFeatureControlHarness.runBeforeInitialSpawnIfRequested((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void randomnibble6plus24generator$validateMosaicIdentityBeforeLevelPreparation(CallbackInfo callbackInfo) {
        MosaicWorldIdentity.validateServerAfterLevelCreation((MinecraftServer) (Object) this);
        Phase2AVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase2BVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase2C1Verification.runIfRequested((MinecraftServer) (Object) this);
        Phase2C1RootCauseVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase2C1StageBisectionHarness.runIsolatedIfRequested((MinecraftServer) (Object) this);
        NativeVanillaControlHarness.completeIfRequested((MinecraftServer) (Object) this);
        NativeVanillaFeatureOrderProbe.runIfRequested((MinecraftServer) (Object) this);
        NativeVanillaFeatureControlHarness.runIfRequested((MinecraftServer) (Object) this);
        Phase2C1StageBisectionHarness.completeNativeIfRequested((MinecraftServer) (Object) this);
    }
}
