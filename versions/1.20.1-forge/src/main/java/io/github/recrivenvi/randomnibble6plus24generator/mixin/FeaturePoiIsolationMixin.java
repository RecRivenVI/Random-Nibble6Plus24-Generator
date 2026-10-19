package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.StrictFeatureRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** POI is reconciled from physical blocks after materialization, never from virtual writes. */
@Mixin(WorldGenRegion.class)
abstract class FeaturePoiIsolationMixin {
    @Redirect(
            method = "setBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/server/level/ServerLevel;onBlockStateChange(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void blockChange(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if ((Object) this instanceof StrictFeatureRegion region)
            region.deferPoiNotification(before, after);
        else level.onBlockStateChange(pos, before, after);
    }
}
