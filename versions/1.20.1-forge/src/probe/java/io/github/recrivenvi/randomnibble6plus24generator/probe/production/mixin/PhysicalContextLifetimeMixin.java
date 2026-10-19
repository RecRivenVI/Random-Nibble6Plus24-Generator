package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeLifecycleProbe;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StructureGenerationContext.class, remap = false)
abstract class PhysicalContextLifetimeMixin {
    @org.spongepowered.asm.mixin.Shadow(remap = false)
    private boolean closed;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void opened(CallbackInfo callback) {
        NativeLifecycleProbe.opened((StructureGenerationContext) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"), remap = false)
    private void closed(CallbackInfo callback) {
        if (!closed)
            io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativePhysicalProbe
                    .ringContextClosed((StructureGenerationContext) (Object) this);
        NativeLifecycleProbe.closed((StructureGenerationContext) (Object) this);
    }
}
