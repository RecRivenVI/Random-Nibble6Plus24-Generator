package io.github.recrivenvi.randomnibble6plus24generator.mixin;

import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicSpawnBiomeCarrier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;

@Mixin(ProtoChunk.class)
abstract class ProtoChunkSpawnBiomeMixin implements MosaicSpawnBiomeCarrier {
    @Unique private volatile SpawnBiomeSnapshot randomnibble6plus24generator$spawnBiomes;

    @Override public SpawnBiomeSnapshot randomnibble6plus24generator$spawnBiomes() {
        return randomnibble6plus24generator$spawnBiomes;
    }

    @Override public void randomnibble6plus24generator$spawnBiomes(SpawnBiomeSnapshot snapshot) {
        randomnibble6plus24generator$spawnBiomes = snapshot;
    }
}
