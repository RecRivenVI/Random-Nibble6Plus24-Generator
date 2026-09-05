package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import io.github.recrivenvi.randomnibble6plus24generator.test.MosaicTestWorlds;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;

class SpawnBiomeSnapshotTest {
    static Holder<Biome> plains,desert;
    static PalettedContainerFactory factory;
    static final ChunkPos TARGET=new ChunkPos(-7,13);
    static final MosaicWorldProfile PROFILE=MosaicWorldProfile.current();
    @BeforeAll static void bootstrap() {
        var biomes=MosaicTestWorlds.registries().lookupOrThrow(Registries.BIOME);
        plains=biomes.getOrThrow(Biomes.PLAINS);desert=biomes.getOrThrow(Biomes.DESERT);
        IdMapper<Holder<Biome>> ids=new IdMapper<>();ids.add(plains);ids.add(desert);
        factory=new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY),
                Blocks.AIR.defaultBlockState(),null,Strategy.createForBiomes(ids),plains,null);
    }
    static Map<ChunkPos,ProtoChunk> inputs() {
        Map<ChunkPos,ProtoChunk> result=new LinkedHashMap<>();
        for(var pos:SpawnBiomeSnapshot.neighbors(TARGET)) {
            var chunk=new ProtoChunk(pos,UpgradeData.EMPTY,LevelHeightAccessor.create(-64,384),factory,null);
            chunk.fillBiomesFromNoise((x,y,z,sampler)->((x+y+z)&1)==0?plains:desert,null);
            chunk.setPersistedStatus(ChunkStatus.BIOMES);result.put(pos,chunk);
        }
        return result;
    }
    static SpawnBiomeSnapshot capture(Map<ChunkPos,ProtoChunk> chunks,long seed) {
        return SpawnBiomeSnapshot.capture(Level.OVERWORLD,TARGET,seed,PROFILE,chunks::get);
    }
    @Test void exactlyCopiesStoredBiomesIncludingNegativeAndClampedCoordinates() {
        var chunks=inputs();var snapshot=capture(chunks,123);
        assertEquals(8,snapshot.positions().size());assertEquals(12288,snapshot.cellCount());
        for(var pos:snapshot.positions())for(int y=-18;y<=82;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)
            assertEquals(chunks.get(pos).getNoiseBiome(pos.x()*4+x,y,pos.z()*4+z),snapshot.biome(pos,pos.x()*4+x,y,pos.z()*4+z));
    }
    @Test void sourcePaletteMutationCannotChangeSnapshot() {
        var chunks=inputs();var snapshot=capture(chunks,123);var pos=snapshot.positions().iterator().next();
        var saved=snapshot.biome(pos,0,0,0);
        chunks.values().forEach(chunk->chunk.fillBiomesFromNoise((x,y,z,sampler)->desert,null));
        assertSame(saved,snapshot.biome(pos,0,0,0));
        assertThrows(UnsupportedOperationException.class,()->snapshot.positions().clear());
    }
    @Test void sameCoordinatesCannotReuseAnotherSeedOrDimensionOrProfile() {
        var snapshot=capture(inputs(),123);
        assertDoesNotThrow(()->snapshot.validate(Level.OVERWORLD,TARGET,123,PROFILE));
        assertThrows(IllegalStateException.class,()->snapshot.validate(Level.OVERWORLD,TARGET,124,PROFILE));
        assertThrows(IllegalStateException.class,()->snapshot.validate(Level.NETHER,TARGET,123,PROFILE));
        assertThrows(IllegalStateException.class,()->snapshot.validate(Level.OVERWORLD,ChunkPos.ZERO,123,PROFILE));
        assertThrows(IllegalStateException.class,()->snapshot.validate(Level.OVERWORLD,TARGET,123,
                new MosaicWorldProfile(2,1,1,1,Level.NETHER)));
    }
    @Test void rejectsIncompleteOrWrongSourceChunks() {
        var chunks=inputs();var pos=SpawnBiomeSnapshot.neighbors(TARGET).getFirst();
        chunks.get(pos).setPersistedStatus(ChunkStatus.EMPTY);
        assertThrows(IllegalStateException.class,()->capture(chunks,0));
        chunks.remove(pos);assertThrows(NullPointerException.class,()->capture(chunks,0));
    }
    @Test void viewRetainsNativeBiomesStageAirWithoutAllocatingTerrainOrAllowingWrites() {
        var snapshot=capture(inputs(),123);var pos=snapshot.positions().iterator().next();
        var view=new MosaicSpawnBiomeView(pos,snapshot,factory);
        assertEquals(0,view.getSections().length);assertEquals(-64,view.getMinY());assertEquals(384,view.getHeight());
        assertSame(snapshot.biome(pos,0,0,0),view.getNoiseBiome(0,0,0));
        assertTrue(view.getBlockState(pos.getWorldPosition()).isAir());
        assertTrue(view.getFluidState(pos.getWorldPosition()).isEmpty());
        assertThrows(IllegalStateException.class,()->view.setBlockState(pos.getWorldPosition(),Blocks.STONE.defaultBlockState(),0));
        assertThrows(IllegalStateException.class,view::getAllStarts);
        assertThrows(IllegalStateException.class,view::getBlockTicks);
    }
    @Test void concurrentReadersObserveOnlyDetachedValues() throws Exception {
        var snapshot=capture(inputs(),123);var pos=snapshot.positions().iterator().next();
        try(var workers=Executors.newFixedThreadPool(8)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<16;i++)futures.add(workers.submit(()->{
                for(int n=0;n<2000;n++)assertSame(plains,snapshot.biome(pos,0,0,0));
            }));
            for(var future:futures)future.get();
        }
    }
}
