package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.RuntimeLifecycleEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class RuntimeFurnaceEvidenceMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void furnace(
            Level level,
            BlockPos pos,
            BlockState state,
            AbstractFurnaceBlockEntity blockEntity,
            CallbackInfo callback) {
        RuntimeLifecycleEvidence.furnaceTick(level, pos);
    }
}
