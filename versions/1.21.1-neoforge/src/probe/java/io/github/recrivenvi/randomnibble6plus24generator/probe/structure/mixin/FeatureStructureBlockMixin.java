package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.features.NativeFeatureVerifier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldGenRegion.class)
abstract class FeatureStructureBlockMixin {
    @Redirect(
            method = "setBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState changed(
            ChunkAccess chunk, BlockPos position, BlockState value, boolean moved) {
        BlockState before = chunk.setBlockState(position, value, moved);
        NativeFeatureVerifier.structureBlockChanged(chunk, position, before, value);
        return before;
    }
}
