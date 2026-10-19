package io.github.recrivenvi.randomnibble6plus24generator.bopcausaltest.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.BopCausalTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ProtoChunk.class)
abstract class BopCausalChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void write(
            BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> ci) {
        BopCausalTrace.write(this, pos, ci.getReturnValue(), state, flags);
    }
}
