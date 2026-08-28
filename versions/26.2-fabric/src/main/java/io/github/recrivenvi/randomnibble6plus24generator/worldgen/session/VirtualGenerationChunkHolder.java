package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;

final class VirtualGenerationChunkHolder extends GenerationChunkHolder {

    VirtualGenerationChunkHolder(ChunkPos pos) {
        super(pos);
        // Physical holders receive this cached limit from ChunkMap ticket updates.
        // A session-local holder has no physical ChunkMap, and the first transition
        // from null only depends on getTicketLevel(), so a null map is safe here.
        updateHighestAllowedStatus(null);
    }

    @Override
    protected void addSaveDependency(CompletableFuture<?> future) {
        // Virtual chunks are never persisted.
    }

    @Override
    public int getTicketLevel() {
        return 0;
    }

    @Override
    public int getQueueLevel() {
        return 0;
    }
}
