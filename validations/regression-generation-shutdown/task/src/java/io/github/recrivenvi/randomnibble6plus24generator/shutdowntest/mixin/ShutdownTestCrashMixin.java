package io.github.recrivenvi.randomnibble6plus24generator.shutdowntest.mixin;

import net.minecraft.CrashReport;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.recrivenvi.randomnibble6plus24generator.shutdowntest.ShutdownVerification;

@Mixin(BlockableEventLoop.class)
abstract class ShutdownTestCrashMixin {
    @Inject(method="relayDelayCrash",at=@At("HEAD"))
    private static void observe(CrashReport report,CallbackInfo ci){ShutdownVerification.reported(report);}
}
