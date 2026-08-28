package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-harness-only access to establish exact-status physical scheduler holders without gameplay tickets. */
@Mixin(ChunkMap.class)
public interface ChunkMapInvoker {

    @Invoker("updateChunkScheduling")
    ChunkHolder randomnibble6plus24generator$invokeUpdateChunkScheduling(
            long packedPos, int newLevel, ChunkHolder holder, int oldLevel);

    @Invoker("promoteChunkMap")
    boolean randomnibble6plus24generator$invokePromoteChunkMap();
}
