package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicRuntimeContextOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldRuntimeState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin implements MosaicRuntimeContextOwner {
    @Unique
    private final MosaicWorldRuntimeState randomnibble6plus24generator$identity =
            new MosaicWorldRuntimeState();

    @Override
    public MosaicWorldRuntimeState randomnibble6plus24generator$worldIdentity() {
        return randomnibble6plus24generator$identity;
    }

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void randomnibble6plus24generator$initializeWorldIdentity(CallbackInfo callbackInfo) {
        MosaicWorldIdentity.bootstrapNewWorldProfileIfNeeded((MinecraftServer) (Object) this);
        MosaicWorldIdentity.initializeServerIdentity((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void randomnibble6plus24generator$validateWorldIdentity(CallbackInfo callbackInfo) {
        MosaicWorldIdentity.validateServerAfterLevelCreation((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void randomnibble6plus24generator$cancelMosaicMaterializationsOnStop(
            CallbackInfo callbackInfo) {
        MosaicPhysicalMaterializer.shutdown((MinecraftServer) (Object) this);
    }

    @Inject(
            method = "stopServer",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/MinecraftServer;saveAllChunks(ZZZ)Z",
                            shift = At.Shift.BEFORE))
    private void randomnibble6plus24generator$drainMosaicWorkersBeforeSave(
            CallbackInfo callbackInfo) {
        MosaicPhysicalMaterializer.finishShutdown((MinecraftServer) (Object) this);
        MosaicStructureOverlayStore.clear((MinecraftServer) (Object) this);
    }

    @WrapMethod(method = "stopServer")
    private void randomnibble6plus24generator$releaseIdentityAfterSaveAndClose(
            Operation<Void> original) {
        try {
            original.call();
        } catch (RuntimeException | Error failure) {
            try {
                MosaicPhysicalMaterializer.finishFailedShutdown((MinecraftServer) (Object) this);
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        } finally {
            MosaicWorldIdentity.closeServerIdentity((MinecraftServer) (Object) this);
        }
    }
}
