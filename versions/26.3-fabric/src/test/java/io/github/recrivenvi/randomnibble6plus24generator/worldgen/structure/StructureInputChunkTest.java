package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureInputChunkTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void carriesRealHeightAndCoordinatesWithoutClaimingTerrain() {
        var height = LevelHeightAccessor.create(-64, 384);
        var chunk = new StructureInputChunk(new ChunkPos(-125, 37), height);
        assertEquals(-64, chunk.getMinY());
        assertEquals(384, chunk.getHeight());
        assertSame(height, chunk.getHeightAccessorForGeneration());
        assertEquals(ChunkStatus.STRUCTURE_STARTS, chunk.getPersistedStatus());
        assertFalse(chunk.canBeSerialized());
        assertTrue(chunk.getAllStarts().isEmpty());
        assertTrue(chunk.getAllReferences().isEmpty());
        assertEquals(8, CompactStructureInputs.referenceRadius());
    }

    @Test
    void neverAnswersMissingTerrainWithDefaultValues() {
        var chunk = new StructureInputChunk(ChunkPos.ZERO, LevelHeightAccessor.create(0, 256));
        assertThrows(UnsupportedOperationException.class, chunk::getSections);
        assertThrows(UnsupportedOperationException.class, () -> chunk.getBlockState(BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> chunk.getFluidState(BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> chunk.getNoiseBiome(0, 0, 0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0));
        assertThrows(UnsupportedOperationException.class, chunk::getBlockTicks);
        assertThrows(UnsupportedOperationException.class, chunk::getFluidTicks);
        assertThrows(
                UnsupportedOperationException.class,
                () -> chunk.setBlockState(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), 0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> chunk.markPosForPostProcessing(BlockPos.ZERO));
    }
}
