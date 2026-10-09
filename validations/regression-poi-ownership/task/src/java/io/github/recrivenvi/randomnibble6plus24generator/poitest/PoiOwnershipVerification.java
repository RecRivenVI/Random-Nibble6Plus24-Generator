package io.github.recrivenvi.randomnibble6plus24generator.poitest;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.*;

/** Real concurrent FULL requests, normal server ticks and independent JVM save/reload. */
public final class PoiOwnershipVerification {
    private static final String MODE=System.getProperty("mosaic.poi.test.mode","create");
    private static final JsonObject RESULT=new JsonObject();
    private static final Map<Object,ServerLevel> OWNERS=new IdentityHashMap<>();
    private static final Map<Object,Map<Thread,Integer>> ENTERED=new IdentityHashMap<>();
    private static final Set<String> THREADS=new TreeSet<>();
    private static final List<Request> REQUESTS=new ArrayList<>();
    private static long calls,offThread,overlaps,artifactBefore,startedAt;
    private static int peakThreads,peakInFlight;
    private static boolean started,finished;
    private static volatile boolean submitted;

    private PoiOwnershipVerification(){}

    public static synchronized void enter(Object storage,String method,long section){
        ServerLevel level=OWNERS.get(storage);if(level==null)return;
        calls++;Thread current=Thread.currentThread();THREADS.add(current.getName());
        var threads=ENTERED.computeIfAbsent(storage,ignored->new IdentityHashMap<>());
        threads.merge(current,1,Integer::sum);peakThreads=Math.max(peakThreads,threads.size());
        if(!level.getServer().isSameThread()){
            offThread++;
            if(!RESULT.has("firstOffThread"))RESULT.addProperty("firstOffThread",method+" section="+section+" thread="+current.getName()+" "+Arrays.toString(current.getStackTrace()));
        }
        if(threads.size()>1){
            overlaps++;
            if(!RESULT.has("firstOverlap")){
                JsonArray traces=new JsonArray();
                threads.keySet().forEach(t->traces.add(t.getName()+" "+Arrays.toString(t.getStackTrace())));
                RESULT.add("firstOverlap",traces);write("thread-evidence.json");
            }
        }
    }
    public static synchronized void leave(Object storage){
        var threads=ENTERED.get(storage);if(threads==null)return;
        Thread current=Thread.currentThread();Integer depth=threads.get(current);if(depth==null)return;
        if(depth==1)threads.remove(current);else threads.put(current,depth-1);
    }

    public static void tick(MinecraftServer server){
        if(finished)return;
        try{
            if(!started){
                started=true;startedAt=System.nanoTime();artifactBefore=MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
                synchronized(PoiOwnershipVerification.class){for(ServerLevel level:server.getAllLevels())OWNERS.put(level.getPoiManager(),level);}
                // This native wait pumps the chunk-source main executor, not the server's
                // general task queue. A handoff to the wrong queue must not deadlock it.
                server.overworld().getChunkSource().getChunkFuture(125,-37,ChunkStatus.FULL,true)
                        .join().orElseThrow(()->new AssertionError("Synchronous FULL request failed"));
                RESULT.addProperty("synchronousFullCompleted",true);
                // Target includes actual canonical village POIs, not injected test POIs.
                for(int z=9;z<=13;z++)for(int x=-12;x<=-8;x++)REQUESTS.add(new Request(server.overworld(),new ChunkPos(x,z)));
                if(!MODE.equals("vanilla")){
                    REQUESTS.add(new Request(server.getLevel(Level.NETHER),new ChunkPos(-11,-6)));
                    REQUESTS.add(new Request(server.getLevel(Level.END),new ChunkPos(1004,1006)));
                }
                // Keep this finite test frontier requested while normal ticks continue.
                // UNKNOWN tickets made by getChunkFuture expire after one tick in 26.2.
                for(Request request:REQUESTS)request.level.getChunkSource().addTicket(
                        new Ticket(TicketType.PLAYER_LOADING,ChunkLevel.byStatus(ChunkStatus.FULL)),request.pos);
                Thread.ofPlatform().name("poi-test-requester").start(()->{
                    for(Request request:REQUESTS)request.future=request.level.getChunkSource().getChunkFuture(request.pos.x(),request.pos.z(),ChunkStatus.FULL,true);
                    submitted=true;
                });
                return;
            }
            peakInFlight=Math.max(peakInFlight,MosaicPhysicalMaterializer.metrics().inFlightCount());
            if(System.nanoTime()-startedAt>180_000_000_000L)throw new AssertionError("FULL frontier timed out");
            if(!submitted||REQUESTS.stream().anyMatch(r->r.future==null||!r.future.isDone()))return;
            JsonObject chunks=new JsonObject();int poiCount=0;
            for(Request request:REQUESTS){
                ChunkAccess chunk=request.future.join().orElseThrow(()->new AssertionError("Missing FULL "+request.pos));
                var expected=MosaicPhysicalPoiReconciler.expectedEntries(chunk);
                var actual=MosaicPhysicalPoiReconciler.actualEntries(request.level,chunk);
                if(!expected.equals(actual))throw new AssertionError("POI mismatch "+request.pos+" expected="+expected+" actual="+actual);
                if(new HashSet<>(actual).size()!=actual.size())throw new AssertionError("Duplicate POI");
                poiCount+=actual.size();
                JsonObject entry=new JsonObject();JsonArray values=new JsonArray();actual.forEach(p->values.add(p.toString()));entry.add("poi",values);
                JsonArray structures=new JsonArray();
                if(!MODE.equals("vanilla"))MosaicStructureOverlayStore.startsForOwner(request.level,request.pos).stream().map(start->start.createTag(
                        net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(request.level),start.getChunkPos()).toString()).sorted().forEach(structures::add);
                entry.add("structures",structures);chunks.add(request.level.dimension().identifier()+"/"+request.pos,entry);
            }
            Request target=REQUESTS.stream().filter(r->r.pos.equals(new ChunkPos(-10,11))).findFirst().orElseThrow();
            LevelChunk chunk=(LevelChunk)target.future.join().orElseThrow(()->new AssertionError("Village FULL absent"));BlockPos marker=new BlockPos(-160,chunk.getMinY()+2,176);
            long artifacts=MosaicPhysicalMaterializer.metrics().artifactCaptureCount()-artifactBefore;
            if(MODE.equals("reload")){
                JsonObject reference=JsonParser.parseString(Files.readString(Path.of(System.getProperty("mosaic.poi.test.reference")))).getAsJsonObject();
                if(!reference.get("chunks").equals(chunks))throw new AssertionError("POI/structure restart mismatch");
                if(!chunk.getBlockState(marker).is(Blocks.DIAMOND_BLOCK))throw new AssertionError("Player edit lost");
                if(artifacts!=0||!PhysicalMosaicTrace.snapshot().spawnSeeds().isEmpty())throw new AssertionError("FULL reload regenerated Artifact/SPAWN");
            }else if(!MODE.equals("vanilla")){
                chunk.setBlockState(marker,Blocks.DIAMOND_BLOCK.defaultBlockState(),0);chunk.markUnsaved();
                if(poiCount==0)throw new AssertionError("No positive canonical POI fixture");
            }
            if(!MODE.equals("audit")&&(offThread!=0||overlaps!=0))throw new AssertionError("Off-thread POI storage access "+offThread+" overlaps "+overlaps);
            if(MODE.equals("vanilla")&&MosaicPhysicalPoiReconciler.metrics().invocationCount()!=0)throw new AssertionError("Mosaic hook activated in Vanilla");
            RESULT.add("chunks",chunks);RESULT.addProperty("positivePoi",poiCount);RESULT.addProperty("artifactCount",artifacts);
            RESULT.addProperty("reconciliations",MosaicPhysicalPoiReconciler.metrics().invocationCount());
            RESULT.addProperty("reconcileMillis",MosaicPhysicalPoiReconciler.metrics().totalNanos()/1e6);
            RESULT.addProperty("generationPeakInFlight",peakInFlight);RESULT.addProperty("fullRequests",REQUESTS.size());
            RESULT.addProperty("status",MODE.equals("audit")?"AUDIT":"PASS");
            finished=true;server.halt(false);
        }catch(Throwable failure){RESULT.addProperty("status","FAIL");RESULT.addProperty("failure",failure.toString());failure.printStackTrace();finished=true;server.halt(false);}
    }

    public static synchronized void stopped(MinecraftServer server){
        if(!finished){RESULT.addProperty("status","FAIL");RESULT.addProperty("failure","Server stopped before frontier completed");}
        RESULT.addProperty("mode",MODE);RESULT.addProperty("calls",calls);RESULT.addProperty("offThreadCalls",offThread);
        RESULT.addProperty("overlappingCalls",overlaps);RESULT.addProperty("maxStorageThreads",peakThreads);
        RESULT.add("threads",new Gson().toJsonTree(THREADS));
        RESULT.addProperty("bindingsAfterClose",GenerationContextRegistry.bindingCount());
        RESULT.addProperty("inFlightAfterClose",MosaicPhysicalMaterializer.metrics().inFlightCount());
        if(GenerationContextRegistry.bindingCount()!=0||MosaicPhysicalMaterializer.metrics().inFlightCount()!=0){RESULT.addProperty("status","FAIL");RESULT.addProperty("failure","Leaked generation context");}
        write("result.json");System.out.println("POI OWNERSHIP TEST "+RESULT.get("status"));
    }
    private static void write(String file){try{Path folder=Path.of(System.getProperty("mosaic.poi.test.output"));Files.createDirectories(folder);Files.writeString(folder.resolve(file),RESULT.toString());}catch(Exception e){throw new IllegalStateException(e);}}
    private static final class Request {
        final ServerLevel level;final ChunkPos pos;volatile CompletableFuture<ChunkResult<ChunkAccess>> future;
        Request(ServerLevel level,ChunkPos pos){this.level=Objects.requireNonNull(level);this.pos=pos;}
    }
}
