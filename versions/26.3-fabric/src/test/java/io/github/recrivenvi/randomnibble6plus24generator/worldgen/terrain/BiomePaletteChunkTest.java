package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomePaletteChunkTest {
    static Holder<Biome> plains, desert;
    static PalettedContainerFactory factory;

    @BeforeAll
    static void bootstrap() {
        var registry = MosaicTestWorlds.registries().lookupOrThrow(Registries.BIOME);
        plains = registry.getOrThrow(Biomes.PLAINS);
        desert = registry.getOrThrow(Biomes.DESERT);
        IdMapper<Holder<Biome>> ids = new IdMapper<>();
        ids.add(plains);
        ids.add(desert);
        factory =
                new PalettedContainerFactory(
                        Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                        Blocks.AIR.defaultBlockState(),
                        null,
                        Strategy.createForBiomes(ids),
                        plains,
                        null);
    }

    @Test
    void preservesNativeResolverInvocationOrderAndStoredData() {
        var height = LevelHeightAccessor.create(-64, 384);
        var pos = new ChunkPos(-125, 37);
        var nativeChunk = new ProtoChunk(pos, UpgradeData.EMPTY, height, factory, null);
        var compact = new BiomePaletteChunk(pos, height, factory);
        var nativeCalls = new ArrayList<String>();
        var compactCalls = new ArrayList<String>();
        BiomeResolver nativeResolver =
                (x, y, z) -> {
                    nativeCalls.add(x + "," + y + "," + z);
                    return ((x + y + z) & 1) == 0 ? plains : desert;
                };
        BiomeResolver compactResolver =
                (x, y, z) -> {
                    compactCalls.add(x + "," + y + "," + z);
                    return ((x + y + z) & 1) == 0 ? plains : desert;
                };
        nativeChunk.fillBiomesFromNoise(nativeResolver);
        nativeChunk.setPersistedStatus(ChunkStatus.BIOMES);
        compact.fillBiomesFromNoise(compactResolver);
        assertEquals(nativeCalls, compactCalls);
        assertEquals(1536, nativeCalls.size());
        for (int y = -30; y <= 100; y++)
            for (int x = -500; x < -496; x++)
                for (int z = 148; z < 152; z++)
                    assertSame(nativeChunk.getNoiseBiome(x, y, z), compact.getNoiseBiome(x, y, z));
        var left = new HashSet<Holder<Biome>>();
        var right = new HashSet<Holder<Biome>>();
        nativeChunk.collectBiomesInPalette(left);
        compact.collectBiomesInPalette(right);
        assertEquals(left, right);
        assertEquals(2, right.size());
        assertThrows(
                IllegalStateException.class, () -> compact.fillBiomesFromNoise(compactResolver));
    }

    @Test
    void cannotProvideEmptyTerrainOrUninitializedBiomes() {
        var compact =
                new BiomePaletteChunk(ChunkPos.ZERO, LevelHeightAccessor.create(0, 256), factory);
        assertFalse(compact.canBeSerialized());
        assertThrows(IllegalStateException.class, () -> compact.getNoiseBiome(0, 0, 0));
        assertThrows(UnsupportedOperationException.class, compact::getSections);
        assertThrows(
                UnsupportedOperationException.class, () -> compact.getBlockState(BlockPos.ZERO));
        assertThrows(UnsupportedOperationException.class, compact::getBlockTicks);
        assertThrows(
                UnsupportedOperationException.class,
                () -> compact.markPosForPostProcessing(BlockPos.ZERO));
    }

    @Test
    void retainedPaletteCopiesAreIndependentAndKeepUnusedEntries() {
        var height = LevelHeightAccessor.create(-64, 384);
        var original =
                new ProtoChunk(new ChunkPos(-1, 2), UpgradeData.EMPTY, height, factory, null);
        original.fillBiomesFromNoise((x, y, z) -> plains);
        original.setPersistedStatus(ChunkStatus.BIOMES);
        // A palette entry may survive after its final cell is replaced. Native enumeration sees it.
        ((PalettedContainer<Holder<Biome>>) original.getSection(0).getBiomes())
                .getAndSetUnchecked(0, 0, 0, desert);
        ((PalettedContainer<Holder<Biome>>) original.getSection(0).getBiomes())
                .getAndSetUnchecked(0, 0, 0, plains);
        var retained = BiomePaletteChunk.snapshot(original, factory);
        var target = new ProtoChunk(original.getPos(), UpgradeData.EMPTY, height, factory, null);
        target.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
        var block = new BlockPos(-15, -60, 33);
        target.setBlockState(block, Blocks.STONE.defaultBlockState(), 0);
        retained.copyTo(target);
        target.setPersistedStatus(ChunkStatus.BIOMES);
        assertEquals(Blocks.STONE.defaultBlockState(), target.getBlockState(block));
        var nativePalette = new HashSet<Holder<Biome>>();
        var copiedPalette = new HashSet<Holder<Biome>>();
        original.collectBiomesInPalette(nativePalette);
        retained.collectBiomesInPalette(copiedPalette);
        assertEquals(nativePalette, copiedPalette);
        assertTrue(copiedPalette.contains(desert));
        ((PalettedContainer<Holder<Biome>>) target.getSection(1).getBiomes())
                .getAndSetUnchecked(0, 0, 0, desert);
        ((PalettedContainer<Holder<Biome>>) original.getSection(2).getBiomes())
                .getAndSetUnchecked(0, 0, 0, desert);
        assertSame(plains, retained.getNoiseBiome(-4, -12, 8));
        assertSame(plains, retained.getNoiseBiome(-4, -8, 8));
        assertSame(plains, target.getNoiseBiome(-4, -8, 8));
        target.setPersistedStatus(ChunkStatus.TERRAIN);
        assertThrows(IllegalArgumentException.class, () -> retained.copyTo(target));
        var foreign = new ProtoChunk(ChunkPos.ZERO, UpgradeData.EMPTY, height, factory, null);
        foreign.setPersistedStatus(ChunkStatus.STRUCTURE_REFERENCES);
        assertThrows(IllegalArgumentException.class, () -> retained.copyTo(foreign));
        assertThrows(
                IllegalArgumentException.class, () -> BiomePaletteChunk.snapshot(foreign, factory));
        var second = BiomePaletteChunk.snapshot(retained, factory);
        var secondPalette = new HashSet<Holder<Biome>>();
        second.collectBiomesInPalette(secondPalette);
        assertEquals(nativePalette, secondPalette);
    }
}
