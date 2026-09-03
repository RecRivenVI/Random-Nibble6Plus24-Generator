package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2AVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2BVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1Verification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1RootCauseVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1StageBisectionHarness;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1FVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2C1FCoverageScan;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2DVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2DTransportVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaControlHarness;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaFeatureOrderProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaFeatureControlHarness;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3APhysicalMaterializationVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3APhysicalPatchVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3AFaultVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3AReloadVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3BVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C1Verification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2ProductionVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void randomnibble6plus24generator$armNativeControlBeforePhysicalGeneration(CallbackInfo callbackInfo) {
        MosaicWorldIdentity.bootstrapNewWorldProfileIfNeeded((MinecraftServer) (Object) this);
        Phase3APhysicalMaterializationVerification.bootstrapProfileIfRequested(
                (MinecraftServer) (Object) this);
        Phase3BVerification.bootstrapProfileIfRequested((MinecraftServer) (Object) this);
        Phase3C1Verification.bootstrapProfileIfRequested((MinecraftServer) (Object) this);
        NativeVanillaControlHarness.armIfRequested((MinecraftServer) (Object) this);
        NativeVanillaFeatureOrderProbe.armIfRequested((MinecraftServer) (Object) this);
        Phase2C1StageBisectionHarness.armNativeIfRequested((MinecraftServer) (Object) this);
    }

    @Redirect(
            method = "createLevels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;setInitialSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/storage/ServerLevelData;ZZLnet/minecraft/server/level/progress/LevelLoadListener;)V"))
    private void randomnibble6plus24generator$skipInitialSpawnOnlyForPhase3AFixture(
            ServerLevel level,
            ServerLevelData levelData,
            boolean generateBonusChest,
            boolean debug,
            LevelLoadListener listener) {
        if (Phase3APhysicalMaterializationVerification.skipInitialSpawnIfRequested()) {
            levelData.setInitialized(true);
            return;
        }
        if (Phase3BVerification.skipInitialSpawnIfRequested()) {
            levelData.setInitialized(true);
            return;
        }
        if (Phase3C1Verification.skipInitialSpawnIfRequested()) {
            levelData.setInitialized(true);
            return;
        }
        MinecraftServerInvoker.randomnibble6plus24generator$invokeSetInitialSpawn(
                level, levelData, generateBonusChest, debug, listener);
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
        Phase2C1FVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase2C1FCoverageScan.runIfRequested((MinecraftServer) (Object) this);
        Phase2DVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase2DTransportVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase3APhysicalMaterializationVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase3APhysicalPatchVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase3AFaultVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase3AReloadVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase3BVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase3C1Verification.runIfRequested((MinecraftServer) (Object) this);
        NativeVanillaControlHarness.completeIfRequested((MinecraftServer) (Object) this);
        NativeVanillaFeatureOrderProbe.runIfRequested((MinecraftServer) (Object) this);
        NativeVanillaFeatureControlHarness.runIfRequested((MinecraftServer) (Object) this);
        Phase2C1StageBisectionHarness.completeNativeIfRequested((MinecraftServer) (Object) this);
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), cancellable = true)
    private void randomnibble6plus24generator$skipSpawnPreparationAfterPhase3AVerification(CallbackInfo callbackInfo) {
        if (Phase3APhysicalMaterializationVerification.skipPrepareLevelsIfCompleted()
                || Phase3BVerification.skipPrepareLevelsIfCompleted()
                || Phase3C1Verification.skipPrepareLevelsIfCompleted()) callbackInfo.cancel();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void randomnibble6plus24generator$observeOrdinaryMosaicProductionRequest(
            java.util.function.BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        Phase3C2ProductionVerification.runIfRequested((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void randomnibble6plus24generator$cancelMosaicMaterializationsOnStop(CallbackInfo callbackInfo) {
        Phase3BVerification.cleanupExactStatusHolders((MinecraftServer) (Object) this);
        MosaicPhysicalMaterializer.shutdown((MinecraftServer) (Object) this);
    }
}
