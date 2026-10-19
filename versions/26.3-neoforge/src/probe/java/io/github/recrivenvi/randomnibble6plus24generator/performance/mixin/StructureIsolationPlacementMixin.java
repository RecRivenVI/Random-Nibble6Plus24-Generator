package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.performance.StructureIsolation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructurePlacement.class)
abstract class StructureIsolationPlacementMixin {
    @Inject(method = "isStructureChunk", at = @At("HEAD"))
    private void candidate(
            ChunkGeneratorStructureState state, int x, int z, CallbackInfoReturnable<Boolean> ci) {
        StructureIsolation.placement((StructurePlacement) (Object) this);
    }
}
