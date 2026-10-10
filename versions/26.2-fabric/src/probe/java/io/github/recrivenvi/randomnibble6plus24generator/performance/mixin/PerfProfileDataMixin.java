package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = MosaicWorldProfileData.class, remap = false)
abstract class PerfProfileDataMixin {
    @WrapMethod(method = "fileExists")
    private static boolean exists(MinecraftServer server, Operation<Boolean> original) {
        long start = System.nanoTime();
        try {
            return original.call(server);
        } finally {
            long nanos = System.nanoTime() - start;
            PerfTrace.profileIo(server, nanos);
            String thread = Thread.currentThread().getName();
            PerfTrace.count("profileStatCalls@" + thread, 1);
            PerfTrace.count("profileStatNanos@" + thread, nanos);
        }
    }
}
