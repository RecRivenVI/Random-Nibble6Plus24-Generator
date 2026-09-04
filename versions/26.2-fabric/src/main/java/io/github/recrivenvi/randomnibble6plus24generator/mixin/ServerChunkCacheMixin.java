package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.Phase3C2RSpawnTrace;

@Mixin(ServerChunkCache.class)
abstract class ServerChunkCacheMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "getChunkForLighting", at = @At("RETURN"))
    private void randomnibble6plus24generator$auditPhysicalMosaicLightQuery(
            int chunkX,
            int chunkZ,
            CallbackInfoReturnable<LightChunk> callback) {
        if (MosaicPhysicalMaterializer.isPhysicalMosaic(level)) {
            PhysicalMosaicTrace.recordLightingQuery(level, chunkX, chunkZ, callback.getReturnValue());
        }
    }

    @Inject(method = "addTicketAndLoadWithRadius", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceSpawnTicket(
            TicketType ticketType,
            ChunkPos center,
            int radius,
            CallbackInfoReturnable<java.util.concurrent.CompletableFuture<?>> callback) {
        Phase3C2RSpawnTrace.recordTicket(level, ticketType, center, radius);
    }

    @Inject(method = "getChunk", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceSynchronousChunkLookup(
            int chunkX,
            int chunkZ,
            ChunkStatus status,
            boolean requireFull,
            CallbackInfoReturnable<ChunkAccess> callback) {
        Phase3C2RSpawnTrace.recordLookup(level, new ChunkPos(chunkX, chunkZ), status, requireFull);
    }
}
