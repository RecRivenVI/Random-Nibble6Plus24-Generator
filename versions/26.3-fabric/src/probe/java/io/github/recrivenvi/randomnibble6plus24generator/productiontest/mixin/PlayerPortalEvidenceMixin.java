package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.ClientLifecycleEvidence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes native portal destination resolution, never creates a transition. */
@Mixin(PortalProcessor.class)
abstract class PlayerPortalEvidenceMixin {
    @Shadow @Final private Portal portal;

    @Inject(method = "getPortalDestination", at = @At("RETURN"))
    private void resolved(
            ServerLevel source,
            Entity entity,
            CallbackInfoReturnable<TeleportTransition> callback) {
        if (entity instanceof ServerPlayer player && callback.getReturnValue() != null)
            ClientLifecycleEvidence.portal(source, player, portal, callback.getReturnValue());
    }
}
