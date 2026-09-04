package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicLevelLifecycle;

@Mixin(ServerLevel.class)
abstract class ServerLevelIdentityMixin implements MosaicLevelLifecycle {
    @Unique
    private volatile boolean randomnibble6plus24generator$closed;

    @Override
    public boolean randomnibble6plus24generator$identityLevelClosed() {
        return randomnibble6plus24generator$closed;
    }

    @WrapMethod(method = "close")
    private void randomnibble6plus24generator$retireDimensionInstance(Operation<Void> original) {
        try {
            original.call();
        } finally {
            randomnibble6plus24generator$closed = true;
        }
    }
}
