package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicRuntimeContextOwner;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldRuntimeState;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class MosaicServerIdentityMixin implements MosaicRuntimeContextOwner {
    @Unique private final MosaicWorldRuntimeState mosaic$identity = new MosaicWorldRuntimeState();

    @Override
    public MosaicWorldRuntimeState randomnibble6plus24generator$worldIdentity() {
        return mosaic$identity;
    }

    @Inject(
            method = "createLevels",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;getDataStorage()Lnet/minecraft/world/level/storage/DimensionDataStorage;",
                            ordinal = 0,
                            shift = At.Shift.AFTER))
    private void mosaic$loadIdentityBeforeSpawn(CallbackInfo callback) {
        MosaicWorldIdentity.initializeServerIdentity((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void mosaic$validateCreatedDimensions(CallbackInfo callback) {
        var server = (MinecraftServer) (Object) this;
        for (var level : server.getAllLevels()) MosaicWorldIdentity.runtimeContext(level);
    }

    @WrapMethod(method = "stopServer")
    private void mosaic$closeIdentity(Operation<Void> original) {
        try {
            for (var level : ((MinecraftServer) (Object) this).getAllLevels()) {
                var lifecycle =
                        ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                                .MosaicGenerationLifecycleOwner)
                                        level)
                                .randomnibble6plus24generator$generationLifecycle();
                if (lifecycle.active())
                    ((io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                            .MosaicPhysicalLevelOwner)
                                    level)
                            .mosaic$physicalState()
                            .beginClosing();
            }
            original.call();
        } finally {
            MosaicWorldIdentity.closeServerIdentity((MinecraftServer) (Object) this);
        }
    }
}
