package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Short-lived seed bridge for the physical SPAWN status.  It deliberately does
 * not own a generation scheduler or a second world; it only supplies the local
 * seed-dependent view while Vanilla's spawn task is executing.
 */
public final class MosaicSpawnGenerationContext implements AutoCloseable {

    private final ServerLevel hostLevel;
    private final ChunkPos target;
    private final long worldSeed;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState randomState;
    private final Map<ChunkPos, net.minecraft.world.level.chunk.ChunkAccess> biomeChunks;
    private final Map<String, AtomicLong> chunkReads = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean closed;

    public MosaicSpawnGenerationContext(
            ServerLevel hostLevel,
            ChunkPos target,
            long worldSeed,
            NoiseBasedChunkGenerator generator,
            RandomState randomState,
            Map<ChunkPos, net.minecraft.world.level.chunk.ChunkAccess> biomeChunks) {
        this.hostLevel = java.util.Objects.requireNonNull(hostLevel, "hostLevel");
        this.target = java.util.Objects.requireNonNull(target, "target");
        this.worldSeed = worldSeed;
        this.generator = java.util.Objects.requireNonNull(generator, "generator");
        this.randomState = java.util.Objects.requireNonNull(randomState, "randomState");
        this.biomeChunks = Map.copyOf(java.util.Objects.requireNonNull(biomeChunks, "biomeChunks"));
    }

    public ServerLevel hostLevel() {
        return hostLevel;
    }

    public ChunkPos target() {
        return target;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public NoiseBasedChunkGenerator generator() {
        return generator;
    }

    public RandomState randomState() {
        return randomState;
    }

    public Map<ChunkPos, net.minecraft.world.level.chunk.ChunkAccess> biomeChunks() {
        return biomeChunks;
    }

    /**
     * SPAWN's Vanilla WorldGenRegion is allowed to read the target LIGHT
     * chunk.  Any other chunk read is recorded and rejected by the caller so
     * that a physical Mosaic neighbour can never silently become the local
     * seed universe.
     */
    public void recordChunkRead(ChunkPos requested, ChunkStatus requestedStatus) {
        String key = requested.x() + "," + requested.z() + "@"
                + (requestedStatus == null ? "null" : requestedStatus.getName());
        chunkReads.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    public boolean hasNonTargetChunkReads() {
        return chunkReads.keySet().stream().anyMatch(key -> {
            int at = key.indexOf('@');
            String pos = at < 0 ? key : key.substring(0, at);
            return !pos.equals(target.x() + "," + target.z())
                    && biomeChunks.keySet().stream().noneMatch(value ->
                            pos.equals(value.x() + "," + value.z()));
        });
    }

    public Map<String, Long> chunkReads() {
        Map<String, Long> result = new TreeMap<>();
        chunkReads.forEach((key, value) -> result.put(key, value.get()));
        return Collections.unmodifiableMap(result);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
