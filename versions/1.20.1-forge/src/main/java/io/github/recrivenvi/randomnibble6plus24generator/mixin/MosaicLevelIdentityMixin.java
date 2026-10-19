package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicLevelLifecycle;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class MosaicLevelIdentityMixin
        implements MosaicLevelLifecycle,
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                        .MosaicPhysicalLevelOwner {
    @Unique private volatile boolean mosaic$closed;

    @Unique
    private io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                    .MosaicGenerationLifecycle
            mosaic$lifecycle;

    @Unique
    private io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                    .MosaicPhysicalLevelState
            mosaic$physical;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mosaic$initializePhysicalOwner(CallbackInfo callback) {
        if (mosaic$lifecycle == null)
            mosaic$lifecycle =
                    new io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                            .MosaicGenerationLifecycle();
        if (mosaic$physical == null)
            mosaic$physical =
                    new io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                            .MosaicPhysicalLevelState(
                            (ServerLevel) (Object) this, mosaic$lifecycle);
    }

    @Override
    public io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                    .MosaicGenerationLifecycle
            randomnibble6plus24generator$generationLifecycle() {
        return mosaic$lifecycle;
    }

    @Override
    public io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                    .MosaicPhysicalLevelState
            mosaic$physicalState() {
        return mosaic$physical;
    }

    @Override
    public boolean randomnibble6plus24generator$identityLevelClosed() {
        return mosaic$closed;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mosaic$closeIdentity(CallbackInfo callback) {
        if (mosaic$lifecycle.active()) mosaic$physical.beginClosing();
        mosaic$closed = true;
    }
}
