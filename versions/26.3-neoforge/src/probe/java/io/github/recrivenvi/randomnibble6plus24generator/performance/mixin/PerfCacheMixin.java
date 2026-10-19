package io.github.recrivenvi.randomnibble6plus24generator.performance.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.recrivenvi.randomnibble6plus24generator.performance.PerfTrace;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.*;

@Mixin(ServerChunkCache.class)
abstract class PerfCacheMixin {
    @Shadow @Final private ServerLevel level;

    @WrapMethod(method = "getChunkFutureMainThread")
    private CompletableFuture<ChunkResult<ChunkAccess>> get(
            int x,
            int z,
            ChunkStatus status,
            boolean load,
            Operation<CompletableFuture<ChunkResult<ChunkAccess>>> original) {
        ChunkPos pos = new ChunkPos(x, z);
        PerfTrace.event(level, pos, "PUBLIC_REQUEST", status.getName() + " load=" + load);
        var result = original.call(x, z, status, load);
        if (load)
            PerfTrace.observe(
                    result,
                    level,
                    pos,
                    "PUBLIC_"
                            + status.getName()
                                    .replace("minecraft:", "")
                                    .toUpperCase(java.util.Locale.ROOT)
                            + "_READY");
        return result;
    }
}
