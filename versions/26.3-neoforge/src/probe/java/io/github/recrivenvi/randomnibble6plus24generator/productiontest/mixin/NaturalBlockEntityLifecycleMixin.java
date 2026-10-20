package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.NaturalRuntimeEvidence;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class NaturalBlockEntityLifecycleMixin {
    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void installed(BlockEntity blockEntity, CallbackInfo callback) {
        NaturalRuntimeEvidence.blockEntityInstalled((LevelChunk) (Object) this, blockEntity);
    }
}
