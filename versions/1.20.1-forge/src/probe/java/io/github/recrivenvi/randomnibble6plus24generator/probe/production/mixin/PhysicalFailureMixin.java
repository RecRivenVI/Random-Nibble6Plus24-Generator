package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MosaicPhysicalLevelState.class, remap = false)
abstract class PhysicalFailureMixin {
    @Inject(method = "terminate", at = @At("HEAD"), remap = false)
    private void observeActualFailureStage(
            java.util.concurrent.CompletableFuture<?> future,
            Throwable failure,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                .failureStage(future, failure);
    }

    @Inject(method = "generate", at = @At("HEAD"), remap = false)
    private void genuineFailure(
            ChunkPos pos,
            MosaicRuntimeContext runtime,
            CallbackInfoReturnable<BFeaturePreparation.Result> callback) {
        io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe
                .preparation();
        if (Boolean.getBoolean(ModConstants.MOD_ID + ".nativePhysical.injectFailure"))
            throw new IllegalStateException(
                    "Injected genuine Mosaic physical generation failure at " + pos);
    }

    @Inject(
            method = "generate",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/features/BFeaturePreparation;generate(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/StructureGenerationContext;Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/profile/MosaicWorldProfile;)Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/features/BFeaturePreparation$Result;"),
            remap = false)
    private void inContextFailure(
            ChunkPos pos,
            MosaicRuntimeContext runtime,
            CallbackInfoReturnable<BFeaturePreparation.Result> callback) {
        if (pos.x != 125 || pos.z != -37) return;
        String kind =
                System.getProperty(
                        io.github.recrivenvi.randomnibble6plus24generator.probe.production
                                        .NativeLifecycleProbe.PREFIX
                                + "kind",
                        "");
        if (kind.equals("genuine"))
            throw new IllegalStateException("Injected genuine Mosaic generation failure at " + pos);
        if (kind.equals("cancellation"))
            throw new java.util.concurrent.CancellationException(
                    "Injected unexpected Mosaic cancellation at " + pos);
    }

    @Inject(method = "generate", at = @At("RETURN"), remap = false)
    private void recordArtifact(
            ChunkPos pos,
            MosaicRuntimeContext runtime,
            CallbackInfoReturnable<BFeaturePreparation.Result> callback) {
        io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe
                .generated(pos, callback.getReturnValue().artifact());
    }
}
