package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.NaturalRuntimeEvidence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SerializableChunkData.class)
abstract class NaturalChunkSaveMixin {
    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void saved(
            ServerLevel level,
            ChunkAccess chunk,
            CallbackInfoReturnable<SerializableChunkData> callback) {
        NaturalRuntimeEvidence.saved(level, chunk, callback.getReturnValue());
    }
}
