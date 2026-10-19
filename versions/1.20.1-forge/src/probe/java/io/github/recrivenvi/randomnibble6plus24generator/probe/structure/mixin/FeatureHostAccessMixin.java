package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
abstract class FeatureHostAccessMixin {
    @Inject(
            method =
                    "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"))
    private void chunk(
            int x,
            int z,
            ChunkStatus status,
            boolean required,
            CallbackInfoReturnable<ChunkAccess> callback) {
        NativeFeatureVerifier.checkPhysicalRequest("getChunk:" + status, x, z);
    }

    @Inject(
            method = "getChunkNow(II)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("HEAD"))
    private void now(int x, int z, CallbackInfoReturnable<LevelChunk> callback) {
        NativeFeatureVerifier.checkPhysicalRequest("getChunkNow", x, z);
    }

    @Inject(method = "hasChunk(II)Z", at = @At("HEAD"))
    private void present(int x, int z, CallbackInfoReturnable<Boolean> callback) {
        NativeFeatureVerifier.checkPhysicalRequest("hasChunk", x, z);
    }
}
