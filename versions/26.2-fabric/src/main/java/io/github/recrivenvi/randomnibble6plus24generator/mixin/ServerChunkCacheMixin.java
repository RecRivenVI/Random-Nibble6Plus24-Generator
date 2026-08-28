package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LightChunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;

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
}
