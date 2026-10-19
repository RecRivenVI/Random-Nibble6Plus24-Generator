package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingStateAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConcentricRingsStructurePlacement.class)
abstract class ConcentricRingsStructurePlacementMixin {
    @Inject(method = "isPlacementChunk", at = @At("HEAD"))
    private void mosaic$guardRange(
            ChunkGeneratorStructureState state,
            int x,
            int z,
            CallbackInfoReturnable<Boolean> callback) {
        var scope = ((ConcentricRingStateAccess) state).mosaic$ringScope();
        if (scope != null) scope.requireQuery(x, z);
    }
}
