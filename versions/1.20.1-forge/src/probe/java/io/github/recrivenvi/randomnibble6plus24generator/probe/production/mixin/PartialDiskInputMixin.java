package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.PartialPreLightProbe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkSerializer.class)
abstract class PartialDiskInputMixin {
    @Inject(method = "read", at = @At("RETURN"))
    private static void observe(
            ServerLevel host,
            PoiManager poi,
            ChunkPos pos,
            CompoundTag data,
            CallbackInfoReturnable<ProtoChunk> callback) {
        PartialPreLightProbe.loaded(host, callback.getReturnValue());
    }
}
