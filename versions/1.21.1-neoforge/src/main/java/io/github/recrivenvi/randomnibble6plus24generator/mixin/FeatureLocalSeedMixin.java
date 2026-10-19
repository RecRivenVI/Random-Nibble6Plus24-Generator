package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CappedProcessor.class)
abstract class FeatureLocalSeedMixin {
    @WrapOperation(
            method = "finalizeProcessing",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long localSeed(ServerLevel level, Operation<Long> original) {
        var scope = FeatureExecutionScope.current(level);
        return scope == null ? original.call(level) : scope.localSeed();
    }
}
