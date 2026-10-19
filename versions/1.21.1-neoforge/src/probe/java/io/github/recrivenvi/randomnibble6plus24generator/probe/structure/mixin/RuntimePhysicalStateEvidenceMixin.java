package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.RuntimeLifecycleEvidence;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicGenerationLifecycle;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MosaicPhysicalLevelState.class, remap = false)
abstract class RuntimePhysicalStateEvidenceMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void created(
            ServerLevel level, MosaicGenerationLifecycle lifecycle, CallbackInfo callback) {
        RuntimeLifecycleEvidence.physicalStateCreated();
    }
}
