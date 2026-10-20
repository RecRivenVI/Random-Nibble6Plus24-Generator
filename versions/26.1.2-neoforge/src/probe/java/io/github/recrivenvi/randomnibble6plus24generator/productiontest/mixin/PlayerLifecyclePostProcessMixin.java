package io.github.recrivenvi.randomnibble6plus24generator.productiontest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.verify.PlayerLifecycleEvidence;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
abstract class PlayerLifecyclePostProcessMixin {
    @Inject(method = "postProcessGeneration", at = @At("RETURN"))
    private void completed(CallbackInfo callback) {
        PlayerLifecycleEvidence.postProcess((LevelChunk) (Object) this);
    }
}
