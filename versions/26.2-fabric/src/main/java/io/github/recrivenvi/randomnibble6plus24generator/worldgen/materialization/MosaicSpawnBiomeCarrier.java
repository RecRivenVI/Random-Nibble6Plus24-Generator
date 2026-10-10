package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;

/**
 * Transient {@link net.minecraft.world.level.chunk.ProtoChunk}-owned input; not serialized or
 * copied into LevelChunk.
 */
public interface MosaicSpawnBiomeCarrier {
    SpawnBiomeSnapshot randomnibble6plus24generator$spawnBiomes();

    void randomnibble6plus24generator$spawnBiomes(SpawnBiomeSnapshot snapshot);
}
