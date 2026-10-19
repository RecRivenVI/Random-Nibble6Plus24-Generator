package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StructureGenerationContext.class, remap = false)
abstract class FeatureContextMixin {
    @Shadow private volatile boolean closed;

    @Inject(
            method =
                    "<init>(Lnet/minecraft/server/level/ServerLevel;JLnet/minecraft/world/level/ChunkPos;)V",
            at = @At("RETURN"))
    private void root(ServerLevel host, long seed, ChunkPos target, CallbackInfo callback) {
        NativeFeatureVerifier.contextOpened(
                false, ((StructureGenerationContext) (Object) this).narrowTemplateLocks());
        io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe
                .context(((StructureGenerationContext) (Object) this).narrowTemplateLocks());
    }

    @Inject(
            method =
                    "<init>(Lio/github/recrivenvi/randomnibble6plus24generator/worldgen/session/StructureGenerationContext;Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("RETURN"))
    private void child(StructureGenerationContext owner, ChunkPos target, CallbackInfo callback) {
        NativeFeatureVerifier.contextOpened(
                true, ((StructureGenerationContext) (Object) this).narrowTemplateLocks());
        io.github.recrivenvi.randomnibble6plus24generator.probe.performance.LegacyPerformanceProbe
                .context(((StructureGenerationContext) (Object) this).narrowTemplateLocks());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void close(CallbackInfo callback) {
        if (!closed) NativeFeatureVerifier.contextClosed();
    }
}
