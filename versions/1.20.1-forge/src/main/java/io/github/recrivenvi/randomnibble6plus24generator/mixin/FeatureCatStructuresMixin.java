package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.StructureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Cat.class)
abstract class FeatureCatStructuresMixin {
    @Redirect(
            method = "finalizeSpawn",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;structureManager()Lnet/minecraft/world/level/StructureManager;"))
    private StructureManager localStructures(ServerLevel level) {
        var scope = FeatureExecutionScope.current(level);
        return scope == null ? level.structureManager() : scope.structures();
    }
}
