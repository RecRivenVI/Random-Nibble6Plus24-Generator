package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.WorldIdentityEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MosaicWorldIdentity.class, remap = false)
abstract class WorldIdentityEvidenceMixin {
    @Inject(method = "closeServerIdentity", at = @At("TAIL"))
    private static void closed(MinecraftServer server, CallbackInfo callback) {
        WorldIdentityEvidence.closed(server);
    }
}
