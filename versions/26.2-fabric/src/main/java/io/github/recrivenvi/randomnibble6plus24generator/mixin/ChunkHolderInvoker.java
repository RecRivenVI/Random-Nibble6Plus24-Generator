package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.Executor;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Test-harness access to Vanilla's status-promotion state machine. */
@Mixin(ChunkHolder.class)
public interface ChunkHolderInvoker {

    @Invoker("updateFutures")
    void randomnibble6plus24generator$invokeUpdateFutures(ChunkMap chunkMap, Executor executor);
}
