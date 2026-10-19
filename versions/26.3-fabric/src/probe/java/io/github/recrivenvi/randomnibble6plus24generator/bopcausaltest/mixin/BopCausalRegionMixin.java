package io.github.recrivenvi.randomnibble6plus24generator.bopcausaltest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.BopCausalTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(WorldGenRegion.class)
abstract class BopCausalRegionMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"))
    private void block(BlockPos pos, CallbackInfoReturnable<BlockState> ci) {
        BopCausalTrace.read(this, "read:block", pos, ci.getReturnValue());
    }

    @Inject(method = "getFluidState", at = @At("RETURN"))
    private void fluid(BlockPos pos, CallbackInfoReturnable<FluidState> ci) {
        BopCausalTrace.read(this, "read:fluid", pos, ci.getReturnValue());
    }

    @Inject(
            method = "getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I",
            at = @At("RETURN"))
    private void height(Heightmap.Types type, int x, int z, CallbackInfoReturnable<Integer> ci) {
        BopCausalTrace.read(
                this, "read:height:" + type, new BlockPos(x, 0, z), ci.getReturnValue());
    }

    @Inject(method = "setBlock", at = @At("RETURN"))
    private void write(
            BlockPos pos,
            BlockState state,
            int flags,
            int depth,
            CallbackInfoReturnable<Boolean> ci) {
        BopCausalTrace.read(
                this,
                "attempt:block",
                pos,
                state + ":" + flags + ":" + depth + ":" + ci.getReturnValue());
    }
}
