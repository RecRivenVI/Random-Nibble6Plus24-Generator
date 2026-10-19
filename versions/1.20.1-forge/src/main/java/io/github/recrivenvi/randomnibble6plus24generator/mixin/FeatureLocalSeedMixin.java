package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CappedProcessor.class)
abstract class FeatureLocalSeedMixin {
    @Redirect(
            method = "finalizeProcessing",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long localSeed(ServerLevel level) {
        var scope = FeatureExecutionScope.current(level);
        return scope == null ? level.getSeed() : scope.localSeed();
    }
}
