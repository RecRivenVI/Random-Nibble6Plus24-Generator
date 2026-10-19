package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicLevelLifecycle;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicGenerationLifecycle;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalLevelState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerLevel.class)
abstract class MosaicLevelLifecycleMixin implements MosaicPhysicalLevelOwner, MosaicLevelLifecycle {
    @Unique
    private final MosaicGenerationLifecycle mosaic$generation = new MosaicGenerationLifecycle();

    @Unique private volatile boolean mosaic$closed;
    @Unique private MosaicPhysicalLevelState mosaic$physical;

    @Override
    public synchronized MosaicPhysicalLevelState mosaic$physicalState() {
        if (mosaic$physical == null)
            mosaic$physical =
                    new MosaicPhysicalLevelState((ServerLevel) (Object) this, mosaic$generation);
        return mosaic$physical;
    }

    @Override
    public MosaicGenerationLifecycle randomnibble6plus24generator$generationLifecycle() {
        return mosaic$generation;
    }

    @Override
    public boolean randomnibble6plus24generator$identityLevelClosed() {
        return mosaic$closed;
    }

    @WrapMethod(method = "close")
    private void mosaic$closeDimension(Operation<Void> original) {
        if (mosaic$generation.active()) {
            mosaic$physicalState().beginClosing();
            mosaic$generation.awaitClosedWorkers();
        }
        try {
            original.call();
        } finally {
            if (mosaic$physical != null) mosaic$physical.clearClosed();
            mosaic$closed = true;
        }
    }
}
