package io.github.recrivenvi.randomnibble6plus24generator.lazy.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConcentricRingsStructurePlacement.class)
abstract class LazyPlacementMixin {
    @Inject(method = "isPlacementChunk", at = @At("HEAD"))
    private void domain(
            ChunkGeneratorStructureState state, int x, int z, CallbackInfoReturnable<Boolean> ci) {
        var scope = ((LazyStateAccess) state).mosaicLazyScope();
        if (scope != null && scope.lazy) scope.checkQuery(x, z);
    }
}
