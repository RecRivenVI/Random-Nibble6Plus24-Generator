package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PlayerLifecycleEvidence;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerChunkCache.class)
abstract class PlayerLifecycleTicketMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "addTicketWithRadius", at = @At("HEAD"))
    private void ticket(TicketType type, ChunkPos pos, int radius, CallbackInfo callback) {
        PlayerLifecycleEvidence.ticket(level, type);
    }

    @Inject(method = "addTicketAndLoadWithRadius", at = @At("HEAD"))
    private void load(
            TicketType type,
            ChunkPos pos,
            int radius,
            CallbackInfoReturnable<CompletableFuture<?>> callback) {
        PlayerLifecycleEvidence.ticket(level, type);
    }
}
