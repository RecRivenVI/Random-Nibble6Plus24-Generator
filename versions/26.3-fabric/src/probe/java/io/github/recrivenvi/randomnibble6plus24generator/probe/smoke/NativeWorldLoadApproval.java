package io.github.recrivenvi.randomnibble6plus24generator.probe.smoke;

import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Approves the owned experimental test save through the native load continuation, without UI input.
 */
@Mixin(WorldOpenFlows.class)
abstract class NativeWorldLoadApproval {
    @Inject(method = "askForBackup", at = @At("HEAD"), cancellable = true)
    private void mosaic$approveOwnedTestSave(
            LevelStorageSource.LevelStorageAccess access,
            boolean customized,
            Runnable proceed,
            Runnable cancel,
            CallbackInfo callback) {
        if (!"smoke-dependencies".equals(System.getProperty("validation.name"))
                || !Boolean.getBoolean("mosaic.smoke.approveOwnedExperimentalSave")) return;
        proceed.run();
        callback.cancel();
    }
}
