package io.github.recrivenvi.randomnibble6plus24generator.probe.structure.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Same native request used by getChunkFuture, without its calling-thread managed wait. */
@Mixin(ServerChunkCache.class)
public interface AsyncChunkRequestAccess {
    @Invoker("getChunkFutureMainThread")
    CompletableFuture<ChunkResult<ChunkAccess>> mosaic$request(
            int x, int z, ChunkStatus status, boolean create);
}
