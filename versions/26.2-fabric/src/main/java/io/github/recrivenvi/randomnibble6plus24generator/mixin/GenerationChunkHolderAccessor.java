package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Test-time visibility for the Vanilla holder state machine. */
@Mixin(GenerationChunkHolder.class)
public interface GenerationChunkHolderAccessor {

    @Accessor("highestAllowedStatus")
    ChunkStatus randomnibble6plus24generator$getHighestAllowedStatus();

    @Accessor("startedWork")
    AtomicReference<ChunkStatus> randomnibble6plus24generator$getStartedWork();
}
