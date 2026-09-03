package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Read-only holder exposing one detached local-seed BIOMES dependency to SPAWN. */
final class MosaicSpawnBiomeHolder extends GenerationChunkHolder {

    private final ChunkAccess chunk;

    MosaicSpawnBiomeHolder(ChunkAccess chunk) {
        super(chunk.getPos());
        this.chunk = chunk;
    }

    @Override
    protected void addSaveDependency(CompletableFuture<?> future) {
        // Detached SPAWN inputs are never persisted.
    }

    @Override
    public int getTicketLevel() {
        return 0;
    }

    @Override
    public int getQueueLevel() {
        return 0;
    }

    @Override
    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
        return status.isOrBefore(ChunkStatus.BIOMES) ? chunk : null;
    }

    @Override
    public ChunkAccess getChunkIfPresent(ChunkStatus status) {
        return getChunkIfPresentUnchecked(status);
    }

    @Override
    public ChunkAccess getLatestChunk() {
        return chunk;
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ChunkStatus.BIOMES;
    }

    @Override
    public ChunkStatus getLatestStatus() {
        return ChunkStatus.BIOMES;
    }
}
