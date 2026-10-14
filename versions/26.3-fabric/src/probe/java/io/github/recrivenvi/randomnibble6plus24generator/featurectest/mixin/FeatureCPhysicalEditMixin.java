package io.github.recrivenvi.randomnibble6plus24generator.featurectest.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureCEditTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LevelChunk.class)
abstract class FeatureCPhysicalEditMixin {
    @WrapMethod(method = "setBlockState")
    private BlockState observe(
            BlockPos pos, BlockState state, int flags, Operation<BlockState> original) {
        var chunk = (LevelChunk) (Object) this;
        boolean ownWrite = FeatureCEditTracker.claim(chunk, pos, state);
        var old = original.call(pos, state, flags);
        FeatureCEditTracker.changed(chunk, pos, old, state, ownWrite);
        return old;
    }
}
