package io.github.recrivenvi.randomnibble6plus24generator.spawntest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicSpawnBiomeCarrier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureStableSnapshot;

/** Ordinary physical getChunkFuture requests; no hand-driven production stages. */
public final class SpawnReuseVerifier {
    private static boolean started;
    private static final List<JsonObject> rows = new ArrayList<>();
    private static final String PREFIX="mosaic.spawn.test.";
    public static void recordFailureCleanup(){rows.getLast().addProperty("failureCleanup",true);}

    public static FeatureStableSnapshot before(ServerLevel level, ChunkAccess chunk) {
        var carried=((MosaicSpawnBiomeCarrier)chunk).randomnibble6plus24generator$spawnBiomes();
        var runtime=MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        if(Boolean.getBoolean(PREFIX+"expectCarried") && carried==null)throw new AssertionError("Fresh SPAWN missed carried snapshot");
        JsonObject row=new JsonObject();
        row.addProperty("dimension",level.dimension().identifier().toString());
        row.addProperty("chunk",chunk.getPos().toString());
        row.addProperty("carried",carried!=null);
        row.addProperty("cells",carried==null?0:carried.cellCount());
        if(carried!=null && Boolean.getBoolean(PREFIX+"comparePalettes")) {
            long seed=runtime.resolveLocalWorldSeed(level.dimension(),chunk.getPos());
            try(var oracle=IsolatedGenerationContext.create(IsolatedGenerationMode.ISOLATED_MOSAIC,level,seed,chunk.getPos())) {
                var expected=oracle.generateBiomesForSpawn(Set.copyOf(SpawnBiomeSnapshot.neighbors(chunk.getPos())));
                int samples=0;
                for(var pos:carried.positions()) {
                    ChunkAccess source=expected.get(pos);
                    if(!java.util.Arrays.stream(source.getSections()).allMatch(net.minecraft.world.level.chunk.LevelChunkSection::hasOnlyAir))
                        throw new AssertionError("V2 BIOMES neighbour contains terrain at "+pos);
                    for(int y=QuartPos.fromBlock(source.getMinY())-1;y<=QuartPos.fromBlock(source.getMaxY())+1;y++) {
                        for(int z=0;z<4;z++)for(int x=0;x<4;x++) {
                            int qx=pos.x()*4+x,qz=pos.z()*4+z;
                            if(!carried.biome(pos,qx,y,qz).equals(source.getNoiseBiome(qx,y,qz)))
                                throw new AssertionError("V2 SPAWN stored palette mismatch at "+pos+" / "+qx+","+y+","+qz);
                            samples++;
                        }
                    }
                }
                row.addProperty("exactBiomeSamples",samples);
                row.addProperty("oracleBiomesNeighborsAllAir",true);
            }
        }
        rows.add(row);
        if(Boolean.getBoolean(PREFIX+"evict")) {
            ((MosaicSpawnBiomeCarrier)chunk).randomnibble6plus24generator$spawnBiomes(null);
            row.addProperty("evictedBeforeSpawn",true);
        }
        return FeatureStableSnapshot.capture(level.dimension().identifier().toString(),chunk,level.registryAccess());
    }

    public static void after(ServerLevel level,ChunkAccess chunk,FeatureStableSnapshot before) {
        var after=FeatureStableSnapshot.capture(level.dimension().identifier().toString(),chunk,level.registryAccess());
        JsonObject row=rows.getLast();
        String id=level.dimension().identifier().getPath()+"-"+chunk.getPos().x()+"-"+chunk.getPos().z();
        before.write(output().resolve(id+"-before.gz"));after.write(output().resolve(id+"-after.gz"));
        String reference=System.getProperty(PREFIX+"reference","");
        if(!reference.isBlank()) {
            var beforeDiff=before.diff(FeatureStableSnapshot.read(Path.of(reference).resolve(id+"-before.gz")));
            var afterDiff=after.diff(FeatureStableSnapshot.read(Path.of(reference).resolve(id+"-after.gz")));
            if(!beforeDiff.equivalent() || !afterDiff.equivalent())
                throw new AssertionError("V2 SPAWN mismatch "+id+" PRE="+beforeDiff+" POST="+afterDiff);
            row.addProperty("oracleParity",true);
        }
        row.addProperty("beforeHash",before.hash());row.addProperty("afterHash",after.hash());
        row.addProperty("spawnedEntities",after.entityCount()-before.entityCount());
        row.addProperty("beforeEntities",before.entityCount());row.addProperty("afterEntities",after.entityCount());
        JsonArray raw=new JsonArray();after.rawEntityNbt().forEach(raw::add);row.add("rawEntities",raw);
        JsonArray canonical=new JsonArray();after.canonicalEntityNbt().forEach(canonical::add);row.add("canonicalEntities",canonical);
    }

    public static void run(MinecraftServer server) {
        if(started)return;started=true;
        try {
            Files.createDirectories(output());
            long beforeArtifacts=MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
            String specs=System.getProperty(PREFIX+"fixtures","overworld:0:0,overworld:125:-37,overworld:-10:11,the_nether:-11:-6,the_end:0:0");
            if(specs.equals("spawn-candidates")) {
                List<String> candidates=new ArrayList<>();
                var runtime=MosaicWorldIdentity.runtimeContext(server).orElseThrow();
                for(int i=0;i<1000 && candidates.size()<8;i++) {
                    ChunkPos pos=new ChunkPos(144+i*4,-37);
                    var random=new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(0));
                    random.setDecorationSeed(runtime.resolveLocalWorldSeed(Level.OVERWORLD,pos),pos.getMinBlockX(),pos.getMinBlockZ());
                    if(random.nextFloat()<0.06f)candidates.add("overworld:"+pos.x()+":"+pos.z());
                }
                specs=String.join(",",candidates);
            }
            int requested=0;
            for(String spec:specs.split(",")) {
                String[] parts=spec.split(":");
                ResourceKey<Level> dimension=ResourceKey.create(Registries.DIMENSION,Identifier.withDefaultNamespace(parts[0]));
                ServerLevel level=server.getLevel(dimension);ChunkPos pos=new ChunkPos(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
                ChunkAccess chunk=level.getChunkSource().getChunkFuture(pos.x(),pos.z(),ChunkStatus.FULL,true)
                        .join().orElseThrow(()->new AssertionError("physical request failed"));
                if(!(chunk instanceof LevelChunk))throw new AssertionError("not FULL LevelChunk");
                requested++;
            }
            if(GenerationContextRegistry.bindingCount()!=0 || MosaicSpawnContextRegistry.bindingCount()!=0)
                throw new AssertionError("context leak");
            long artifacts=MosaicPhysicalMaterializer.metrics().artifactCaptureCount()-beforeArtifacts;
            if(Boolean.getBoolean(PREFIX+"expectReload") && (artifacts!=0 || !rows.isEmpty()))throw new AssertionError("FULL reload regenerated");
            JsonObject result=new JsonObject();result.addProperty("status","PASS");result.addProperty("requests",requested);
            if(Boolean.getBoolean(PREFIX+"fortress"))result.add("fortress",verifyFortress(server));
            result.addProperty("artifacts",artifacts);result.addProperty("oracle",Boolean.getBoolean(PREFIX+"oracle"));
            JsonArray all=new JsonArray();rows.forEach(all::add);result.add("spawns",all);
            finish(server,result);
        } catch(Exception e) { throw new IllegalStateException("SPAWN reuse regression failed",e); }
    }
    private static Path output(){return Path.of(System.getProperty(PREFIX+"output")).toAbsolutePath();}
    private static void finish(MinecraftServer server,JsonObject result) {
        try{Files.writeString(output().resolve("result.json"),result.toString());}
        catch(java.io.IOException e){throw new IllegalStateException(e);}
        System.out.println("SPAWN REUSE PASS "+output());server.halt(false);
    }

    private static JsonObject verifyFortress(MinecraftServer server) {
        var level=server.getLevel(Level.NETHER);var owner=new ChunkPos(-11,-6);
        var chunk=level.getChunkSource().getChunk(owner.x(),owner.z(),ChunkStatus.FULL,true);
        var structure=level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(net.minecraft.world.level.levelgen.structure.BuiltinStructures.FORTRESS).value();
        var manager=level.structureManager();net.minecraft.core.BlockPos inside=null,outside=null;
        for(int y=level.getMinY()+1;y<level.getMaxY() && (inside==null||outside==null);y++) {
            for(int z=owner.getMinBlockZ();z<=owner.getMaxBlockZ();z++)for(int x=owner.getMinBlockX();x<=owner.getMaxBlockX();x++) {
                var pos=new net.minecraft.core.BlockPos(x,y,z);
                boolean in=manager.getStructureWithPieceAt(pos,structure).isValid();
                if(inside==null && in && chunk.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.NETHER_BRICKS))inside=pos;
                if(outside==null && !in && !manager.getStructureAt(pos,structure).isValid())outside=pos;
            }
        }
        if(inside==null||outside==null)throw new AssertionError("Missing real fortress inside/outside fixture");
        var category=net.minecraft.world.entity.MobCategory.MONSTER;
        if(!net.minecraft.world.level.NaturalSpawner.isInNetherFortressBounds(inside,level,category,manager))throw new AssertionError("Fortress inside lost override");
        if(net.minecraft.world.level.NaturalSpawner.isInNetherFortressBounds(outside,level,category,manager))throw new AssertionError("Fortress outside contaminated");
        var generator=level.getChunkSource().getGenerator();
        var expected=net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure.FORTRESS_ENEMIES;
        if(!generator.getMobsAt(level.getBiome(inside),manager,category,inside).equals(expected))throw new AssertionError("Wrong fortress mob table");
        if(generator.getMobsAt(level.getBiome(outside),manager,category,outside).equals(expected))throw new AssertionError("Outside used fortress mob table");
        JsonObject result=new JsonObject();result.addProperty("inside",inside.toShortString());result.addProperty("outside",outside.toShortString());result.addProperty("matched",true);return result;
    }
}
