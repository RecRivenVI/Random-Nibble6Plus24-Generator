package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2AVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase2BVerification;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.NativeVanillaControlHarness;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void randomnibble6plus24generator$armNativeControlBeforePhysicalGeneration(CallbackInfo callbackInfo) {
        NativeVanillaControlHarness.armIfRequested((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void randomnibble6plus24generator$validateMosaicIdentityBeforeLevelPreparation(CallbackInfo callbackInfo) {
        MosaicWorldIdentity.validateServerAfterLevelCreation((MinecraftServer) (Object) this);
        Phase2AVerification.runIfRequested((MinecraftServer) (Object) this);
        Phase2BVerification.runIfRequested((MinecraftServer) (Object) this);
        NativeVanillaControlHarness.completeIfRequested((MinecraftServer) (Object) this);
    }
}
