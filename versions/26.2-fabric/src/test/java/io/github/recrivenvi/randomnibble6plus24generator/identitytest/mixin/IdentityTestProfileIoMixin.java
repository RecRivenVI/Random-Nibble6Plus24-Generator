package io.github.recrivenvi.randomnibble6plus24generator.identitytest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;

import io.github.recrivenvi.randomnibble6plus24generator.identitytest.IdentityLifecycleVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfileData;

@Mixin(value = MosaicWorldProfileData.class, remap = false)
abstract class IdentityTestProfileIoMixin {
    @WrapMethod(method = "fileExists")
    private static boolean countProfileIo(MinecraftServer server, Operation<Boolean> original) {
        IdentityLifecycleVerifier.PROFILE_PROBES.incrementAndGet();
        return original.call(server);
    }
}
