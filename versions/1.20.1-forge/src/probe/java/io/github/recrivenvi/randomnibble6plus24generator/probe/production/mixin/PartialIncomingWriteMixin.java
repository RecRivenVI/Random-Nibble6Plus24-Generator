package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.PartialPreLightProbe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
abstract class PartialIncomingWriteMixin {
    @Inject(
            method =
                    "setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"))
    private void observe(
            int x,
            int y,
            int z,
            BlockState state,
            boolean moving,
            CallbackInfoReturnable<BlockState> callback) {
        PartialPreLightProbe.write(
                (LevelChunkSection) (Object) this, x, y, z, callback.getReturnValue(), state);
    }
}
