package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;

/** Empty region storage belonging only to one fresh virtual universe. */
public final class EmptyVirtualChunkScanAccess implements ChunkScanAccess {

    private final AtomicInteger scanCount = new AtomicInteger();

    @Override
    public CompletableFuture<Void> scanChunk(ChunkPos pos, StreamTagVisitor visitor) {
        scanCount.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }

    public int scanCount() {
        return scanCount.get();
    }
}
