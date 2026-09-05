package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingStateAccess;

@Mixin(ConcentricRingsStructurePlacement.class)
abstract class ConcentricRingsStructurePlacementMixin {
    @Inject(method = "isPlacementChunk", at = @At("HEAD"))
    private void randomnibble6plus24generator$validateQueryRange(
            ChunkGeneratorStructureState state, int x, int z, CallbackInfoReturnable<Boolean> callback) {
        var scope = ((ConcentricRingStateAccess) state).randomnibble6plus24generator$ringScope();
        if (scope != null) scope.requireQueryInRange(x, z);
    }
}
