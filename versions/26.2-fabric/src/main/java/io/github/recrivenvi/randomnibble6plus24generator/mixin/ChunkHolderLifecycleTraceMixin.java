package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.Executor;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2RSpawnTrace;

/** Test-only visibility into Vanilla FullChunkStatus promotion and ticket changes. */
@Mixin(ChunkHolder.class)
abstract class ChunkHolderLifecycleTraceMixin {

    @Inject(method = "updateFutures", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceUpdateFutures(
            ChunkMap chunkMap, Executor executor, CallbackInfo callbackInfo) {
        Phase3C2RSpawnTrace.recordHolderUpdate(
                chunkMap, (ChunkHolder) (Object) this, "update-before");
    }

    @Inject(method = "updateFutures", at = @At("RETURN"))
    private void randomnibble6plus24generator$traceUpdatedFutures(
            ChunkMap chunkMap, Executor executor, CallbackInfo callbackInfo) {
        Phase3C2RSpawnTrace.recordHolderUpdate(
                chunkMap, (ChunkHolder) (Object) this, "update-after");
    }

    @Inject(method = "setTicketLevel", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceTicketLevel(
            int level, CallbackInfo callbackInfo) {
        Phase3C2RSpawnTrace.recordTicketLevel((ChunkHolder) (Object) this, level);
    }

    @Inject(method = "scheduleFullChunkPromotion", at = @At("HEAD"))
    private void randomnibble6plus24generator$tracePromotion(
            ChunkMap chunkMap,
            java.util.concurrent.CompletableFuture<?> future,
            Executor executor,
            FullChunkStatus status,
            CallbackInfo callbackInfo) {
        Phase3C2RSpawnTrace.recordPromotion((ChunkHolder) (Object) this, status);
    }
}
