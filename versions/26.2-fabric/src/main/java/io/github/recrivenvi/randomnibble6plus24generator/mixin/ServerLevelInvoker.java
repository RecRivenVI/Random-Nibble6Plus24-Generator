package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Harness-only access to the exact Vanilla scheduled-tick callbacks. */
@Mixin(ServerLevel.class)
public interface ServerLevelInvoker {

    @Invoker("tickBlock")
    void randomnibble6plus24generator$invokeTickBlock(BlockPos pos, Block block);

    @Invoker("tickFluid")
    void randomnibble6plus24generator$invokeTickFluid(BlockPos pos, Fluid fluid);

    @Invoker("tickNonPassenger")
    void randomnibble6plus24generator$invokeTickNonPassenger(Entity entity);
}
