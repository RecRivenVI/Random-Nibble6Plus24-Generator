package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;

/** Restores saved Mosaic structure starts with the same local world seed that created them. */
@Mixin(SerializableChunkData.class)
abstract class SerializableChunkDataMixin {

    @Shadow
    @Final
    private ChunkPos chunkPos;

    @Redirect(
            method = "read",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long randomnibble6plus24generator$decodeStructuresWithLocalWorldSeed(ServerLevel level) {
        if (MosaicWorldIdentity.isMosaicWorld(level) && MosaicWorldIdentity.isMosaicDimension(level)) {
            return MosaicWorldIdentity.runtimeContext(level)
                    .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context while loading Chunk"))
                    .resolveLocalWorldSeed(level.dimension(), chunkPos);
        }
        return level.getSeed();
    }

}
