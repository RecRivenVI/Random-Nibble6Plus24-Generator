package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.PhysicalMosaicTrace;

/** Development-only counters for Vanilla physical runtime ticks. */
@Mixin(ServerLevel.class)
abstract class ServerLevelLifecycleTraceMixin {

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceBlockTick(
            BlockPos pos, Block block, CallbackInfo callbackInfo) {
        PhysicalMosaicTrace.recordRuntimeTick((ServerLevel) (Object) this, "block");
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceFluidTick(
            BlockPos pos, Fluid fluid, CallbackInfo callbackInfo) {
        PhysicalMosaicTrace.recordRuntimeTick((ServerLevel) (Object) this, "fluid");
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void randomnibble6plus24generator$traceEntityTick(
            Entity entity, CallbackInfo callbackInfo) {
        PhysicalMosaicTrace.recordRuntimeTick((ServerLevel) (Object) this, "entity");
    }

}
