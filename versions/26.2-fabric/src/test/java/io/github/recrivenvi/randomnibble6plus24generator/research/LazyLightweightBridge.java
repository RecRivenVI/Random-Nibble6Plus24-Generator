package io.github.recrivenvi.randomnibble6plus24generator.research;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import io.github.recrivenvi.randomnibble6plus24generator.lazy.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.CarverStageSnapshot;

/** Proves the resolver also serves the existing scheduler-free Pre-FEATURES carrier. */
public final class LazyLightweightBridge {
    public static JsonObject run(ServerLevel host,String id,ChunkPos pos,long seed,Path reference)throws Exception{
        JsonObject row=new JsonObject(),hashes=new JsonObject();row.addProperty("id",id);row.add("hashes",hashes);
        long started=System.nanoTime();
        try(var universe=new LightweightUniverse(host,seed,pos)){
            var chunk=universe.begin(pos);
            universe.biomes(chunk);hashes.addProperty(ChunkStatus.BIOMES.getName(),CarverStageSnapshot.capture(chunk,host.registryAccess()).hash());
            universe.noise(chunk);hashes.addProperty(ChunkStatus.NOISE.getName(),CarverStageSnapshot.capture(chunk,host.registryAccess()).hash());
            universe.surface(chunk);hashes.addProperty(ChunkStatus.SURFACE.getName(),CarverStageSnapshot.capture(chunk,host.registryAccess()).hash());
            universe.carvers(chunk);hashes.addProperty(ChunkStatus.CARVERS.getName(),CarverStageSnapshot.capture(chunk,host.registryAccess()).hash());
            JsonObject expected=JsonParser.parseString(Files.readString(reference.resolve(id+".json"))).getAsJsonObject();
            for(var e:hashes.entrySet())if(!expected.getAsJsonObject("hashes").get(e.getKey()).equals(e.getValue()))throw new IllegalStateException("Lightweight stage mismatch "+id+" "+e.getKey());
            int starts=0,pieces=0;Set<String> matched=new HashSet<>();
            for(var source:universe.starts.entrySet())for(var entry:source.getValue().entrySet()){
                String key=source.getKey()+"/"+host.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                String nbt=entry.getValue().createTag(StructurePieceSerializationContext.fromLevel(host),source.getKey()).toString();
                if(!new JsonPrimitive(nbt).equals(expected.getAsJsonObject("preStructures").get(key)))throw new IllegalStateException("Lightweight NBT mismatch "+key+" actual="+nbt+" expected="+expected.getAsJsonObject("preStructures").get(key));
                matched.add(key);starts++;pieces+=entry.getValue().getPieces().size();
            }
            for(String key:expected.getAsJsonObject("preStructures").keySet()){
                String[] coords=key.substring(1,key.indexOf(']')).split(", ");
                ChunkPos source=new ChunkPos(Integer.parseInt(coords[0]),Integer.parseInt(coords[1]));
                if(Math.abs(source.x()-pos.x())<=9 && Math.abs(source.z()-pos.z())<=9 && !matched.contains(key))throw new IllegalStateException("Missing structure "+key);
            }
            var scope=((LazyStateAccess)universe.context.structureState()).mosaicLazyScope();
            if(scope.beardInputs.size()!=9)throw new IllegalStateException("Missing lightweight terrain/biome halo evidence");
            JsonObject actualBeard=new Gson().toJsonTree(new TreeMap<>(scope.beardInputs)).getAsJsonObject(),wantedBeard=new JsonObject();
            for(String key:actualBeard.keySet())wantedBeard.add(key,Objects.requireNonNull(expected.getAsJsonObject("preBeardInputs").get(key)));
            row.add("beardComparison",BeardInputComparison.compare(wantedBeard,actualBeard,host));
            row.add("metrics",new Gson().toJsonTree(scope.metrics()));row.addProperty("beardInputChunks",scope.beardInputs.size());
            row.addProperty("nativeStructureEvaluations",universe.nativeStructureEvaluations);row.addProperty("validStarts",starts);row.addProperty("pieces",pieces);
            var mapField=universe.context.getClass().getDeclaredField("chunkMap");mapField.setAccessible(true);
            Object map=mapField.get(universe.context);var holdersField=map.getClass().getDeclaredField("holders");holdersField.setAccessible(true);
            int holders=((Map<?,?>)holdersField.get(map)).size();
            if(holders!=0 || universe.terrain.size()!=1)throw new IllegalStateException("Unexpected full neighbor storage");
            row.addProperty("protoChunks",universe.terrain.size());row.addProperty("holders",holders);row.addProperty("oracleParity",true);
        }
        row.addProperty("wallMsIncludingCapture",(System.nanoTime()-started)/1e6);return row;
    }
}
