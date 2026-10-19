package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeRuntimeProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class RuntimeFurnaceMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void furnace(
            Level level,
            BlockPos pos,
            BlockState state,
            AbstractFurnaceBlockEntity be,
            CallbackInfo callback) {
        NativeRuntimeProbe.furnaceTick(level, pos);
    }
}
