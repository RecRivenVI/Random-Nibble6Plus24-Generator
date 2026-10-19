package io.github.recrivenvi.randomnibble6plus24generator.mixin;

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
    @Unique private MosaicWorldRuntimeState mosaic$identity;

    @Unique
    private java.util.List<
                    io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                            .MosaicPhysicalLevelOwner>
            mosaic$closing;

    // Explicit injection avoids legacy initializer extraction dropping the identity field.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void mosaic$initializeOwner(CallbackInfo callback) {
        if (mosaic$identity == null) mosaic$identity = new MosaicWorldRuntimeState();
        if (mosaic$closing == null) mosaic$closing = java.util.List.of();
    }

    @Override
    public MosaicWorldRuntimeState randomnibble6plus24generator$worldIdentity() {
        return mosaic$identity;
    }

    // Native 1.20.1 inserts the overworld before this call and before initial spawn generation.
    @Inject(
            method = "createLevels",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;getDataStorage()Lnet/minecraft/world/level/storage/DimensionDataStorage;",
                            ordinal = 0,
                            shift = At.Shift.AFTER))
    private void mosaic$validateLoad(CallbackInfo callback) {
        MosaicWorldIdentity.initializeServerIdentity((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels", at = @At("RETURN"))
    private void mosaic$validateDimensions(CallbackInfo callback) {
        var server = (MinecraftServer) (Object) this;
        for (var level : server.getAllLevels()) MosaicWorldIdentity.runtimeContext(level);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void mosaic$beginClose(CallbackInfo callback) {
        var levels =
                new java.util.ArrayList<
                        io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                .MosaicPhysicalLevelOwner>();
        for (var level : ((MinecraftServer) (Object) this).getAllLevels()) {
            var owner =
                    (io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization
                                    .MosaicPhysicalLevelOwner)
                            level;
            if (owner.randomnibble6plus24generator$generationLifecycle().active()) {
                levels.add(owner);
                owner.mosaic$physicalState().beginClosing();
            }
        }
        mosaic$closing = java.util.List.copyOf(levels);
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void mosaic$finishClose(CallbackInfo callback) {
        for (var owner : mosaic$closing) owner.mosaic$physicalState().awaitClosed();
        mosaic$closing = java.util.List.of();
    }
}
