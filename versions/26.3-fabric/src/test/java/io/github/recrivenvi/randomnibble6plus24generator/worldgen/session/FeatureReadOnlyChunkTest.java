package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.ScheduledTick;
import org.junit.jupiter.api.Test;

class FeatureReadOnlyChunkTest {
    @Test
    void delegatesStoredTerrainAndBiomesButDiscardsAllNeighborSideEffects() {
        Holder<Biome> plains =
                MosaicTestWorlds.registries()
                        .lookupOrThrow(Registries.BIOME)
                        .getOrThrow(Biomes.PLAINS);
        IdMapper<Holder<Biome>> ids = new IdMapper<>();
        ids.add(plains);
        var factory =
                new PalettedContainerFactory(
                        Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                        Blocks.AIR.defaultBlockState(),
                        null,
                        Strategy.createForBiomes(ids),
                        plains,
                        null);
        var source =
                new ProtoChunk(
                        new ChunkPos(-1, 2),
                        UpgradeData.EMPTY,
                        LevelHeightAccessor.create(-64, 384),
                        factory,
                        null);
        BlockPos pos = new BlockPos(-1, 20, 33);
        source.setBlockState(pos, Blocks.STONE.defaultBlockState(), 0);
        source.fillBiomesFromNoise((x, y, z) -> plains);
        source.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.BIOMES);
        AtomicInteger discarded = new AtomicInteger();
        var view = new FeatureReadOnlyChunk(source, factory, discarded::incrementAndGet);
        assertEquals(source.getBlockState(pos), view.getBlockState(pos));
        assertSame(source.getNoiseBiome(-1, 5, 8), view.getNoiseBiome(-1, 5, 8));
        assertEquals(source.getMinY(), view.getMinY());
        assertEquals(source.getHeight(), view.getHeight());
        view.setBlockState(pos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
        view.setBlockEntity(new ChestBlockEntity(pos, Blocks.CHEST.defaultBlockState()));
        CompoundTag pending = new CompoundTag();
        pending.putInt("x", pos.getX());
        pending.putInt("y", pos.getY());
        pending.putInt("z", pos.getZ());
        pending.putString("id", "DUMMY");
        view.setBlockEntityNbt(pending);
        view.markPosForPostProcessing(pos);
        view.getBlockTicks().schedule(new ScheduledTick<>(Blocks.STONE, pos, 0, 0));
        view.getFluidTicks().schedule(new ScheduledTick<>(Fluids.WATER, pos, 0, 0));
        assertEquals(6, discarded.get());
        assertEquals(Blocks.STONE.defaultBlockState(), source.getBlockState(pos));
        assertNull(source.getBlockEntity(pos));
        assertNull(source.getBlockEntityNbt(pos));
        assertEquals(0, source.getBlockTicks().count());
        assertEquals(0, source.getFluidTicks().count());
        for (var offsets : source.getPostProcessing())
            assertTrue(offsets == null || offsets.isEmpty());
        view.getSection(source.getSectionIndex(pos.getY()))
                .setBlockState(
                        pos.getX() & 15,
                        pos.getY() & 15,
                        pos.getZ() & 15,
                        Blocks.GOLD_BLOCK.defaultBlockState());
        assertEquals(Blocks.STONE.defaultBlockState(), source.getBlockState(pos));
        assertEquals(
                Blocks.STONE.defaultBlockState(),
                view.getSection(source.getSectionIndex(pos.getY()))
                        .getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15));
        assertEquals(7, discarded.get());
        assertFalse(view.canBeSerialized());
    }
}
