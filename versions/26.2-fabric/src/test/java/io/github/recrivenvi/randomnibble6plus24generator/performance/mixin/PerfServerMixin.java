package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.recrivenvi.randomnibble6plus24generator.performance.*;
@Mixin(value = MinecraftServer.class, priority = 1500)
abstract class PerfServerMixin {
    @Inject(method="createLevels",at=@At("HEAD"))
    private void start(CallbackInfo ci){PerfTrace.start((MinecraftServer)(Object)this);}
    @Inject(method="setInitialSpawn",at=@At("HEAD"),cancellable=true)
    private static void skipUnmeasuredHostSpawn(ServerLevel level,ServerLevelData data,boolean bonus,
            boolean debug,LevelLoadListener listener,CallbackInfo ci){
        if(PerfWorkload.cli()||Boolean.getBoolean("mosaic.lazy.verify")){data.setInitialized(true);ci.cancel();}
    }
    @Inject(method="prepareLevels",at=@At("HEAD"),cancellable=true)
    private void skipUnmeasuredPreparation(CallbackInfo ci){if(PerfWorkload.cli()||Boolean.getBoolean("mosaic.lazy.verify"))ci.cancel();}
    @Inject(method="createLevels",at=@At("RETURN"))
    private void lazyVerification(CallbackInfo ci){if(Boolean.getBoolean("mosaic.lazy.verify"))io.github.recrivenvi.randomnibble6plus24generator.lazy.LazyVerification.run((MinecraftServer)(Object)this);}
    @Inject(method="tickServer",at=@At("RETURN"))
    private void workload(java.util.function.BooleanSupplier time,CallbackInfo ci){PerfWorkload.tick((MinecraftServer)(Object)this);}
    @Inject(method="stopServer",at=@At("RETURN"))
    private void finish(CallbackInfo ci){PerfTrace.stop();}
}
