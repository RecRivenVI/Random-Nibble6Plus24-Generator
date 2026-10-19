package io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin;

import io.github.recrivenvi.randomnibble6plus24generator.probe.production.NativeRuntimeProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class RuntimeTickMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void block(BlockPos pos, Block block, CallbackInfo callback) {
        NativeRuntimeProbe.blockTick((ServerLevel) (Object) this, pos);
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void fluid(BlockPos pos, Fluid fluid, CallbackInfo callback) {
        NativeRuntimeProbe.fluidTick((ServerLevel) (Object) this, pos);
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void entity(Entity entity, CallbackInfo callback) {
        NativeRuntimeProbe.entityTick((ServerLevel) (Object) this, entity);
    }
}
