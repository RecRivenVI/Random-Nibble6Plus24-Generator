package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.StructureInputChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;

/** Only native ChunkAccess.carverBiome(Supplier) is consumed, never neighbor terrain. */
final class CarverSourceChunk extends StructureInputChunk {
    CarverSourceChunk(ChunkPos position, LevelHeightAccessor height) {
        super(position, height);
    }
}
