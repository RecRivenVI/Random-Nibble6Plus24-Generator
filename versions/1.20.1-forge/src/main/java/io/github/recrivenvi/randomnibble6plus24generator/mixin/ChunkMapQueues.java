package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapQueues {
    @Accessor("mainThreadExecutor")
    BlockableEventLoop<Runnable> mosaic$mainThreadExecutor();
}
