package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The native chunk-source queue is pumped by synchronous chunk waits in this version. */
@Mixin(ChunkMap.class)
public interface ChunkMapQueues {
    @Accessor("mainThreadExecutor")
    BlockableEventLoop<Runnable> mosaic$mainThreadExecutor();

    @Accessor("level")
    ServerLevel mosaic$level();

    @Invoker("getUpdatingChunkIfPresent")
    ChunkHolder mosaic$holder(long pos);
}
