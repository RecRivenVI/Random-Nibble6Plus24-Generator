package io.github.recrivenvi.randomnibble6plus24generator.lazy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.*;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.*;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;

/** Finite exact query domain, not a partially computed global structure state. Research only. */
public final class TargetStructureScope {
    public final ChunkPos target;
    public final int radius;
    public final long seed;
    public final boolean lazy;
    private final Map<StructurePlacement,Set<Long>> candidates=new IdentityHashMap<>();
    private final Map<String,LongAdder> counters=new ConcurrentHashMap<>();
    public final Map<String,String> beardInputs=new ConcurrentHashMap<>();
    public final List<ChunkPos> initialRingSlots=Collections.synchronizedList(new ArrayList<>());
    public final List<ChunkPos> resolvedRingSlots=Collections.synchronizedList(new ArrayList<>());
    public TargetStructureScope(ChunkPos target,int radius,long seed,boolean lazy){this.target=target;this.radius=radius;this.seed=seed;this.lazy=lazy;}
    public static int v2Radius(){
        return ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).blockStateWriteRadius()
                +ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).accumulatedDependencies().getRadiusOf(ChunkStatus.STRUCTURE_STARTS);
    }
    public boolean contains(int x,int z){return Math.abs((long)x-target.x())<=radius && Math.abs((long)z-target.z())<=radius;}
    public boolean possibleRing(int x,int z){return Math.abs((long)x-target.x())<=radius+7L && Math.abs((long)z-target.z())<=radius+7L;}
    public void checkQuery(int x,int z){if(!contains(x,z))throw new IllegalStateException("Lazy ring query outside proven domain: "+new ChunkPos(x,z)+" target="+target+" radius="+radius);}
    public void count(String key,long value){counters.computeIfAbsent(key,k->new LongAdder()).add(value);}
    public Map<String,Object> metrics(){Map<String,Object> out=new TreeMap<>();counters.forEach((k,v)->out.put(k,v.sum()));out.put("radius",radius);out.put("lazy",lazy);return out;}
    public CompletableFuture<ChunkPos> ringJob(int x,int z,Supplier<ChunkPos> work,Executor executor){
        count("ringSlots",1);initialRingSlots.add(new ChunkPos(x,z));
        if(lazy&&!possibleRing(x,z)){
            count("ringSearchesSkipped",1);
            // This unresolved slot is provably outside the domain. Native sequence
            // ordering is kept; the out-of-domain value is removed before exposure.
            return CompletableFuture.completedFuture(new ChunkPos(x,z));
        }
        count("ringBiomeSearches",1);
        return CompletableFuture.supplyAsync(()->{
            long start=System.nanoTime();try{ChunkPos pos=work.get();resolvedRingSlots.add(pos);return pos;}
            finally{count("ringSearchNanos",System.nanoTime()-start);}
        },executor);
    }
    public List<ChunkPos> ringResult(List<ChunkPos> nativeSlots){
        return lazy?nativeSlots.stream().filter(pos->contains(pos.x(),pos.z())).toList():nativeSlots;
    }
    public List<Holder<StructureSet>> forSource(ChunkGeneratorStructureState state,ChunkPos pos,List<Holder<StructureSet>> sets){
        if(!lazy){count("sourceQueries",1);count("setConsiderations",sets.size());return sets;}
        checkQuery(pos.x(),pos.z());count("sourceQueries",1);
        List<Holder<StructureSet>> result=new ArrayList<>();
        for(var set:sets){
            StructurePlacement placement=set.value().placement();count("setConsiderations",1);
            if(placement instanceof RandomSpreadStructurePlacement spread){
                Set<Long> positions;
                synchronized(candidates){positions=candidates.computeIfAbsent(placement,p->cells(spread));}
                if(!positions.contains(pos.pack())){count("cellRejected",1);continue;}
            }
            count("placementPredicates",1);
            // Native frequency/salt/exclusion-zone semantics remain authoritative.
            if(placement.isStructureChunk(state,pos.x(),pos.z()))result.add(set);
            else count("placementRejected",1);
        }
        count("eligibleSets",result.size());return result;
    }
    private Set<Long> cells(RandomSpreadStructurePlacement placement){
        Set<Long> result=new HashSet<>();int spacing=placement.spacing();
        int minX=Math.floorDiv(target.x()-radius,spacing),maxX=Math.floorDiv(target.x()+radius,spacing);
        int minZ=Math.floorDiv(target.z()-radius,spacing),maxZ=Math.floorDiv(target.z()+radius,spacing);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
            count("placementCells",1);
            ChunkPos pos=placement.getPotentialStructureChunk(seed,x*spacing,z*spacing);
            if(contains(pos.x(),pos.z()))result.add(pos.pack());
        }
        return Set.copyOf(result);
    }
    public void beard(ChunkPos pos,Beardifier beard){
        if(!Boolean.getBoolean("mosaic.lazy.capture"))return;
        try{
            var pf=Beardifier.class.getDeclaredField("pieces");pf.setAccessible(true);
            var jf=Beardifier.class.getDeclaredField("junctions");jf.setAccessible(true);
            var bf=Beardifier.class.getDeclaredField("affectedBox");bf.setAccessible(true);
            StringBuilder s=new StringBuilder();
            for(Object entry:(List<?>)pf.get(beard))s.append(entry).append(';');
            for(Object entry:(List<?>)jf.get(beard)){
                JigsawJunction j=(JigsawJunction)entry;
                s.append(j.getSourceX()).append(',').append(j.getSourceGroundY()).append(',').append(j.getSourceZ()).append(',')
                        .append(j.getDeltaY()).append(',').append(j.getDestProjection()).append(';');
            }
            s.append("box=").append(bf.get(beard));
            String previous=beardInputs.putIfAbsent(pos.toString(),s.toString());
            if(previous!=null&&!previous.equals(s.toString()))throw new IllegalStateException("Beardifier inputs changed for "+pos);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
