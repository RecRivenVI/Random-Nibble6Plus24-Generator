package io.github.recrivenvi.randomnibble6plus24generator.shutdowntest.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.recrivenvi.randomnibble6plus24generator.shutdowntest.ShutdownVerification;

@Mixin(MinecraftServer.class)
abstract class ShutdownTestServerMixin {
    @Inject(method="setInitialSpawn",at=@At("HEAD"),cancellable=true)
    private static void noSpawn(ServerLevel level,ServerLevelData data,boolean bonus,boolean debug,LevelLoadListener listener,CallbackInfo ci){
        data.setInitialized(true);ci.cancel();
    }
    @Inject(method="prepareLevels",at=@At("HEAD"),cancellable=true)
    private void noPrep(CallbackInfo ci){ci.cancel();}
    @Inject(method="tickServer",at=@At("RETURN"))
    private void test(java.util.function.BooleanSupplier time,CallbackInfo ci){ShutdownVerification.run((MinecraftServer)(Object)this);}
    @Inject(method="runServer",at=@At("RETURN"))
    private void stopped(CallbackInfo ci){ShutdownVerification.stopped((MinecraftServer)(Object)this);}
}
