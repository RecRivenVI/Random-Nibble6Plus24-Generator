package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Cat.class)
abstract class FeatureCatStructuresMixin {
    @WrapOperation(
            method = "finalizeSpawn",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;structureManager()Lnet/minecraft/world/level/StructureManager;"))
    private StructureManager localStructures(
            ServerLevel level, Operation<StructureManager> original) {
        var scope = FeatureExecutionScope.current(level);
        return scope == null ? original.call(level) : scope.structures();
    }
}
