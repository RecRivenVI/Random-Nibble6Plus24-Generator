package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface NativePlanDependencyAccess {
    @Invoker("getDependencyStatus")
    ChunkStatus mosaic$nativeDependency(ChunkStatus status, int distance);
}
