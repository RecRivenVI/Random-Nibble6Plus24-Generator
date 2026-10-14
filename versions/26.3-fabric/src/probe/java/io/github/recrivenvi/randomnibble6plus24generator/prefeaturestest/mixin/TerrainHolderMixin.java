package io.github.recrivenvi.randomnibble6plus24generator.prefeaturestest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.TerrainProbeEvidence;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets =
                "io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.VirtualGenerationChunkHolder")
abstract class TerrainHolderMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void created(ChunkPos pos, CallbackInfo callback) {
        TerrainProbeEvidence.holderCreated();
    }
}
