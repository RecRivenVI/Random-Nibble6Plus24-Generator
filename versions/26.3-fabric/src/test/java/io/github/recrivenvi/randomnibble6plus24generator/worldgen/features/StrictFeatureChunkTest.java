package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.ScheduledTick;
import org.junit.jupiter.api.Test;

class StrictFeatureChunkTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        MosaicTestWorlds.registries();
    }

    private static PalettedContainerFactory factory() {
        Holder<Biome> plains =
                MosaicTestWorlds.registries()
                        .lookupOrThrow(Registries.BIOME)
                        .getOrThrow(Biomes.PLAINS);
        IdMapper<Holder<Biome>> ids = new IdMapper<>();
        ids.add(plains);
        return new PalettedContainerFactory(
                Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                Blocks.AIR.defaultBlockState(),
                null,
                Strategy.createForBiomes(ids),
                plains,
                null);
    }

    private static ProtoChunk chunk(ChunkPos pos) {
        var c =
                new ProtoChunk(
                        pos,
                        UpgradeData.EMPTY,
                        LevelHeightAccessor.create(-64, 384),
                        factory(),
                        null);
        c.setPersistedStatus(ChunkStatus.TERRAIN);
        return c;
    }

    private static CompoundTag pending(BlockPos pos) {
        var tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putString("id", "DUMMY");
        return tag;
    }

    private static CompoundTag entity(BlockPos pos) {
        var tag = new CompoundTag();
        tag.putString("id", "minecraft:end_crystal");
        ListTag coords = new ListTag();
        coords.add(DoubleTag.valueOf(pos.getX() + .5));
        coords.add(DoubleTag.valueOf(pos.getY()));
        coords.add(DoubleTag.valueOf(pos.getZ() + .5));
        tag.put("Pos", coords);
        tag.putString("marker", "preserved");
        return tag;
    }

    private static StrictFeatureChunk view(
            ProtoChunk c, ChunkPos writer, Map<String, Integer> drops) {
        return new StrictFeatureChunk(
                c,
                writer,
                factory(),
                api -> drops.merge(api, 1, Integer::sum),
                MosaicTestWorlds.registries());
    }

    @Test
    void retainsCompleteTargetSideEffectsAndGuardsWrongAbsoluteCoordinates() {
        var c = chunk(new ChunkPos(-1, 2));
        var drops = new HashMap<String, Integer>();
        var v = view(c, c.getPos(), drops);
        BlockPos inside = new BlockPos(-1, 20, 33), outside = inside.offset(1, 0, 0);
        v.setBlockState(inside, Blocks.CHEST.defaultBlockState(), 0);
        var chest = new ChestBlockEntity(inside, Blocks.CHEST.defaultBlockState());
        v.setBlockEntity(chest);
        BlockPos pendingPos = inside.offset(0, 1, 0);
        v.setBlockEntityNbt(pending(pendingPos));
        v.markPosForPostProcessing(inside);
        v.getBlockTicks().schedule(new ScheduledTick<>(Blocks.CHEST, inside, 7, 3));
        v.getFluidTicks().schedule(new ScheduledTick<>(Fluids.WATER, inside, 9, 4));
        v.addEntityNbt(entity(inside));
        assertSame(chest, c.getBlockEntity(inside));
        assertNotNull(c.getBlockEntityNbt(pendingPos));
        assertEquals(1, c.getBlockTicks().count());
        assertEquals(1, c.getFluidTicks().count());
        assertEquals(1, c.getEntities().size());
        assertEquals("preserved", c.getEntities().getFirst().getStringOr("marker", ""));
        assertEquals(
                1,
                Arrays.stream(c.getPostProcessing())
                        .filter(Objects::nonNull)
                        .mapToInt(List::size)
                        .sum());
        v.setBlockState(outside, Blocks.GOLD_BLOCK.defaultBlockState(), 0);
        v.setBlockEntity(new ChestBlockEntity(outside, Blocks.CHEST.defaultBlockState()));
        v.setBlockEntityNbt(pending(outside));
        v.addEntityNbt(entity(outside));
        v.getBlockTicks().schedule(new ScheduledTick<>(Blocks.STONE, outside, 0, 0));
        v.getFluidTicks().schedule(new ScheduledTick<>(Fluids.LAVA, outside, 0, 0));
        v.markPosForPostProcessing(outside);
        assertEquals(7, drops.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, c.getBlockTicks().count());
        assertEquals(1, c.getFluidTicks().count());
        assertEquals(1, c.getEntities().size());
    }

    @Test
    void everyNeighborDirectionHasNoMutableWriteAuthority() {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                var c = chunk(new ChunkPos(-5 + dx, 7 + dz));
                var drops = new HashMap<String, Integer>();
                var v = view(c, new ChunkPos(-5, 7), drops);
                BlockPos p = c.getPos().getWorldPosition().offset(0, 30, 15);
                c.setBlockState(p, Blocks.STONE.defaultBlockState(), 0);
                v.setBlockState(p, Blocks.GOLD_BLOCK.defaultBlockState(), 0);
                v.setBlockEntityNbt(pending(p));
                v.markPosForPostProcessing(p);
                v.addEntityNbt(entity(p));
                v.getBlockTicks().schedule(new ScheduledTick<>(Blocks.STONE, p, 0, 0));
                v.getFluidTicks().schedule(new ScheduledTick<>(Fluids.WATER, p, 0, 0));
                var section = v.getSection(c.getSectionIndex(p.getY()));
                section.setBlockState(
                        p.getX() & 15,
                        p.getY() & 15,
                        p.getZ() & 15,
                        Blocks.GOLD_BLOCK.defaultBlockState());
                section.getStates()
                        .set(
                                p.getX() & 15,
                                p.getY() & 15,
                                p.getZ() & 15,
                                Blocks.DIAMOND_BLOCK.defaultBlockState());
                assertEquals(Blocks.STONE.defaultBlockState(), c.getBlockState(p));
                assertEquals(c.getBlockState(p), v.getBlockState(p));
                assertEquals(0, c.getEntities().size());
                assertEquals(0, c.getBlockTicks().count());
                assertEquals(0, c.getFluidTicks().count());
                assertNull(c.getBlockEntityNbt(p));
                assertThrows(UnsupportedOperationException.class, v::getAllStarts);
                assertThrows(
                        UnsupportedOperationException.class, () -> v.setAllReferences(Map.of()));
                assertThrows(UnsupportedOperationException.class, v::getHeightmaps);
                assertThrows(UnsupportedOperationException.class, v::getUpgradeData);
                assertThrows(UnsupportedOperationException.class, v::initializeLightSources);
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> section.fillBiomesFromNoise(null, 0, 0, 0));
                assertNotSame(section.getBiomes(), section.getBiomes());
                assertThrows(
                        IllegalArgumentException.class,
                        () -> v.getNoiseBiome((c.getPos().x() + 1) * 4, 0, c.getPos().z() * 4));
            }
    }

    @Test
    void borrowedObjectsCannotMutateNeighborOrChangeLaterReads() {
        var c = chunk(new ChunkPos(0, 0));
        var v = view(c, new ChunkPos(1, 0), new HashMap<>());
        BlockPos p = new BlockPos(1, 20, 2);
        c.setBlockState(p, Blocks.CHEST.defaultBlockState(), 0);
        var chest = new ChestBlockEntity(p, Blocks.CHEST.defaultBlockState());
        c.setBlockEntity(chest);
        BlockPos pendingPos = p.offset(0, 1, 0);
        c.setBlockEntityNbt(pending(pendingPos));
        c.markPosForPostProcessing(p);
        assertNotSame(chest, v.getBlockEntity(p));
        v.getBlockEntityNbt(pendingPos).putString("id", "corrupt");
        assertEquals("DUMMY", c.getBlockEntityNbt(pendingPos).getStringOr("id", ""));
        v.getPostProcessing()[c.getSectionIndex(p.getY())].clear();
        assertEquals(1, c.getPostProcessing()[c.getSectionIndex(p.getY())].size());
        v.getSections()[0] = null;
        assertNotNull(v.getSections()[0]);
    }

    @Test
    void clipsNestedEntityPassengersWithoutDroppingInsideData() {
        var c = chunk(new ChunkPos(0, 0));
        var drops = new HashMap<String, Integer>();
        var v = view(c, c.getPos(), drops);
        CompoundTag root = entity(new BlockPos(1, 20, 1));
        ListTag passengers = new ListTag();
        passengers.add(entity(new BlockPos(2, 20, 1)));
        passengers.add(entity(new BlockPos(16, 20, 1)));
        root.put("Passengers", passengers);
        v.addEntityNbt(root);
        assertEquals(1, c.getEntities().getFirst().getListOrEmpty("Passengers").size());
        assertEquals(2, root.getListOrEmpty("Passengers").size());
        assertEquals(1, drops.get("entity_nbt"));
        assertThrows(IllegalArgumentException.class, () -> v.addEntityNbt(new CompoundTag()));
        assertThrows(NoSuchElementException.class, () -> v.setBlockEntityNbt(new CompoundTag()));
    }
}
