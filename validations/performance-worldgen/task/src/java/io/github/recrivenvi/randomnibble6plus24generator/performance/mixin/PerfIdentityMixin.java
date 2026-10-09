package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;
import java.util.Optional;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
@Mixin(value=MosaicWorldIdentity.class,remap=false)
abstract class PerfIdentityMixin {
    @WrapMethod(method="runtimeContext(Lnet/minecraft/server/MinecraftServer;)Ljava/util/Optional;")
    private static Optional<MosaicRuntimeContext> runtime(MinecraftServer server,Operation<Optional<MosaicRuntimeContext>> original){
        long start=System.nanoTime();try{return original.call(server);}finally{
            String thread=Thread.currentThread().getName();PerfTrace.count("identityCalls@"+thread,1);PerfTrace.count("identityNanos@"+thread,System.nanoTime()-start);
        }
    }
}
