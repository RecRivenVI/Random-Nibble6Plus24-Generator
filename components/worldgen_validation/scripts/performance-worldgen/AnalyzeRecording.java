import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Java 25 source-file tool. Aggregates JFR evidence without a profiler dependency. */
class AnalyzeRecording {
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]);Instant first=null,last=null,start=null,end=null;
        try(RecordingFile file=new RecordingFile(input)){
            while(file.hasMoreEvents()){
                RecordedEvent e=file.readEvent();Instant t=e.getStartTime();
                if(first==null||t.isBefore(first))first=t;if(last==null||e.getEndTime().isAfter(last))last=e.getEndTime();
                if(e.getEventType().getName().equals("mosaic.ChunkPhase")){
                    String phase=e.getString("phase");
                    if(phase.startsWith("WORKLOAD_START"))start=t;
                    if(phase.equals("WORKLOAD_END"))end=t;
                }
            }
        }
        if(start==null)start=first;if(end==null)end=last;
        // A manual client can record several worlds in one JVM. Long-lived parked
        // threads can emit events that began before this recording's world load.
        if(args.length>3){start=Instant.parse(args[2]);end=Instant.parse(args[3]);
            if(!end.isAfter(start))throw new IllegalArgumentException("Invalid explicit recording window");}
        if(args.length>2 && args[2].equals("first-packet")) {
            try(RecordingFile file=new RecordingFile(input)) {
                while(file.hasMoreEvents()) {
                    RecordedEvent event=file.readEvent();
                    if(event.getEventType().getName().equals("mosaic.ChunkPhase")
                            && "minecraft:overworld:0,0".equals(event.getString("key"))
                            && "PACKET_ENQUEUE_END".equals(event.getString("phase"))) {
                        end=event.getEndTime();break;
                    }
                }
            }
        }
        Map<String,Long> types=new TreeMap<>(),cpu=new HashMap<>(),cpuAll=new HashMap<>(),cpuThreads=new HashMap<>(),nativeSamples=new HashMap<>();
        Map<String,Double> monitors=new HashMap<>(),parks=new HashMap<>(),sync=new HashMap<>(),allocation=new HashMap<>(),allocationSites=new HashMap<>(),files=new HashMap<>();
        Map<String,Double> syncThreads=new HashMap<>(),serverParkStacks=new HashMap<>();Map<String,Long> syncCounts=new HashMap<>();
        Map<String,List<long[]>> spanPoints=new HashMap<>();
        List<Map<String,Object>> pauses=new ArrayList<>(),slowCalls=new ArrayList<>(),cpuLoadSamples=new ArrayList<>();
        List<Long> afterGcHeap=new ArrayList<>();
        Map<String,String> prepared=new TreeMap<>();long sampleCount=0;double gcMs=0,maxGcMs=0;
        Map<String,Long> cpuBuckets=new TreeMap<>(),workCpu=new TreeMap<>(),biomeCpu=new TreeMap<>(),nativeBiomeCpu=new TreeMap<>();
        Map<String,Double> workMonitors=new TreeMap<>();boolean jfrMarkerTimestamps=false;
        try(RecordingFile file=new RecordingFile(input);var events=Files.newBufferedWriter(Path.of(args[1]+".events.tsv"))){
            events.write("nanos\tkey\tphase\tdetail\tthread\n");
            while(file.hasMoreEvents()){
                RecordedEvent e=file.readEvent();String type=e.getEventType().getName();
                if(e.getEndTime().isBefore(start)||e.getStartTime().isAfter(end))continue;
                types.merge(type,1L,Long::sum);
                Instant a=e.getStartTime().isBefore(start)?start:e.getStartTime();
                Instant b=e.getEndTime().isAfter(end)?end:e.getEndTime();
                double ms=Math.max(0,Duration.between(a,b).toNanos()/1e6);
                List<String> stack=stack(e);
                String site=stack.isEmpty()?"<no stack>":stack.getFirst();
                String gameSite=stack.stream().filter(s->s.startsWith("net.minecraft")||s.startsWith("io.github.recrivenvi"))
                        .findFirst().orElse(site);
                if(type.equals("jdk.ExecutionSample")){
                    sampleCount++;cpu.merge(site,1L,Long::sum);for(String f:new HashSet<>(stack))cpuAll.merge(f,1L,Long::sum);
                    var thread=e.getThread("sampledThread");cpuThreads.merge(thread==null?"unknown":thread.getJavaName(),1L,Long::sum);
                    String bucket;
                    if(stack.stream().anyMatch(f->f.contains("ChunkGeneratorStructureState.lambda$generateRingPositions")||f.contains("ChunkGeneratorStructureState.generateRingPositions")))bucket="strongholdRings";
                    else if(stack.stream().anyMatch(f->f.startsWith("net.minecraft.world.level.levelgen.structure.")||f.contains("ChunkGeneratorStructureState.")||f.contains("ChunkGenerator.createStructures")||f.contains("ChunkGenerator.createReferences")||f.contains("ChunkGenerator.tryGenerateStructure")||f.contains("IsolatedChunkStatusTasks.generateStructure")))bucket="otherStructure";
                    else if(stack.stream().anyMatch(f->f.contains(".levelgen.carver.")||f.contains(".applyCarvers")))bucket="carvers";
                    else if(stack.stream().anyMatch(f->f.contains(".NoiseChunk.")||f.contains(".DensityFunctions$")||f.contains(".SurfaceSystem.")||f.contains("NoiseBasedChunkGenerator.")))bucket="noiseSurfaceBiomes";
                    else if(stack.stream().anyMatch(f->f.contains(".levelgen.feature.")||f.contains(".applyBiomeDecoration")))bucket="features";
                    else bucket="other";
                    cpuBuckets.merge(bucket,1L,Long::sum);
                    // Nearest scope wins: a worker waiting in join can execute another task.
                    String work=workScope(stack);workCpu.merge(work,1L,Long::sum);
                    if(stack.stream().anyMatch(f->f.endsWith("TerrainBiomeInputs.palette")||f.endsWith("TerrainBiomeInputs.rememberGenerated")))
                        biomeCpu.merge(work,1L,Long::sum);
                    if(stack.stream().anyMatch(f->f.endsWith("ChunkGenerator.doCreateBiomes")))
                        nativeBiomeCpu.merge(work,1L,Long::sum);
                }else if(type.equals("jdk.CPULoad")){
                    cpuLoadSamples.add(Map.of("at",e.getStartTime().toString(),
                            "machineTotal",(double)e.getFloat("machineTotal"),
                            "jvmUser",(double)e.getFloat("jvmUser"),
                            "jvmSystem",(double)e.getFloat("jvmSystem")));
                }else if(type.equals("jdk.NativeMethodSample")){
                    nativeSamples.merge(site,1L,Long::sum);
                }else if(type.equals("jdk.JavaMonitorEnter")){
                    String monitor=e.getClass("monitorClass").getName();monitors.merge(monitor+" | "+gameSite,ms,Double::sum);
                    workMonitors.merge(workScope(stack)+" | "+monitor,ms,Double::sum);
                }else if(type.equals("jdk.ThreadPark")){
                    parks.merge((e.getThread()==null?"?":e.getThread().getJavaName())+" | "+gameSite,ms,Double::sum);
                    if(e.getThread()!=null&&e.getThread().getJavaName().equals("Server thread"))
                        serverParkStacks.merge(String.join(" -> ",stack),ms,Double::sum);
                }else if(type.equals("jdk.ObjectAllocationSample")){
                    double bytes=e.getLong("weight");allocation.merge(e.getClass("objectClass").getName(),bytes,Double::sum);
                    allocationSites.merge(gameSite,bytes,Double::sum);
                }else if(type.equals("jdk.GCPhasePause")){
                    gcMs+=ms;maxGcMs=Math.max(maxGcMs,ms);pauses.add(Map.of("milliseconds",ms,"at",e.getStartTime().toString()));
                }else if(type.equals("jdk.GCHeapSummary") && e.getString("when").equals("After GC")){
                    afterGcHeap.add(e.getLong("heapUsed"));
                }else if(type.equals("jdk.FileRead")||type.equals("jdk.FileWrite")){
                    files.merge(type+" | "+e.getString("path"),ms,Double::sum);
                }else if(type.equals("mosaic.SyncSpan")){
                    String phase=e.getString("phase");sync.merge(phase,ms,Double::sum);
                    syncThreads.merge(phase+" | "+e.getThread().getJavaName(),ms,Double::sum);syncCounts.merge(phase,1L,Long::sum);
                    var points=spanPoints.computeIfAbsent(phase,k->new ArrayList<>());
                    points.add(new long[]{Duration.between(start,a).toNanos(),1});points.add(new long[]{Duration.between(start,b).toNanos(),-1});
                    if(ms>=50)slowCalls.add(Map.of("phase",phase,"key",e.getString("key"),"ms",ms,"thread",e.getThread().getJavaName(),"start",e.getStartTime().toString()));
                }else if(type.equals("mosaic.ChunkPhase")){
                    if(e.getString("phase").equals("PREPARED_METRICS"))prepared.put(e.getString("key"),e.getString("detail"));
                    long markerNanos;
                    if(e.hasField("elapsedNanos"))markerNanos=e.getLong("elapsedNanos");
                    else {markerNanos=Duration.between(start,e.getStartTime()).toNanos();jfrMarkerTimestamps=true;}
                    events.write(markerNanos+"\t"+e.getString("key")+"\t"+e.getString("phase")+"\t"+
                            Objects.toString(e.getString("detail"),"").replace('\t',' ').replace('\n',' ')+"\t"+
                            (e.getThread()==null?"?":e.getThread().getJavaName())+"\n");
                }
            }
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("windowStart",start.toString());out.put("windowEnd",end.toString());
        out.put("wallSeconds",Duration.between(start,end).toNanos()/1e9);out.put("eventCounts",types);out.put("cpuSamples",sampleCount);
        out.put("cpuLoadSamples",cpuLoadSamples);
        var machineCpuMean=cpuLoadSamples.stream()
                .mapToDouble(row->((Number)row.get("machineTotal")).doubleValue()).average();
        out.put("machineCpuLoadSampleMean",machineCpuMean.isPresent()?machineCpuMean.getAsDouble():null);
        out.put("missingElapsedNanosUsesJfrWindowTimestamps",jfrMarkerTimestamps);
        out.put("cpuLeafTop",top(cpu,35));out.put("cpuInclusiveTop",top(cpuAll,50));out.put("cpuThreads",top(cpuThreads,25));
        Map<String,Long> focused=new TreeMap<>();
        cpuAll.forEach((key,value)->{if(key.contains("StructureGenerationLocks.supports")
                || key.contains("LocalDensityTiles$Audit.") || key.contains("LocalTerrainColumns$Batch.")
                || key.contains("ScopedStructureSources.source") || key.contains("DetachedBiomeInputs.generate")
                || key.contains(".RandomState.") || key.contains("SeedLocalNoiseGenerator.copy")
                || key.contains("IsolatedGenerationContext.<init>") || key.contains("CanonicalChunkArtifact.")
                || key.contains("NoiseBasedChunkGenerator.getOrigin") || key.contains("NoiseSpawnFinder."))focused.put(key,value);});
        out.put("focusedCpuSamples",focused);
        out.put("workCpuSamples",workCpu);out.put("storedBiomeCpuSamples",biomeCpu);
        out.put("nativeBiomeCpuSamples",nativeBiomeCpu);
        out.put("workMonitorBlockedMs",workMonitors);
        out.put("exclusiveCpuBuckets",cpuBuckets);
        out.put("nativeWallSamplesNotCpu",top(nativeSamples,20));
        out.put("monitorBlockedMsTop",top(monitors,30));out.put("threadParkMsTop",top(parks,30));
        out.put("serverParkStacksMs",top(serverParkStacks,8));
        out.put("allocationWeightedBytesTop",top(allocation,25));out.put("allocationSitesWeightedBytesTop",top(allocationSites,30));
        out.put("allocationWeightedBytesTotal",allocation.values().stream().mapToDouble(Double::doubleValue).sum());
        var sortedHeap=afterGcHeap.stream().sorted().toList();
        out.put("afterGcHeapUsed",sortedHeap.isEmpty()?null:Map.of("samples",sortedHeap.size(),
                "median",sortedHeap.get(sortedHeap.size()/2),"maximum",sortedHeap.getLast()));
        out.put("gcPauseMs",gcMs);out.put("maxGcPauseMs",maxGcMs);out.put("gcPauses",pauses);out.put("fileIoMsTop",top(files,20));
        out.put("syncSpanTotalMs",sync);out.put("preparedTimingNs",prepared);
        out.put("syncSpanCount",syncCounts);out.put("syncSpanThreadsMs",syncThreads);
        Map<String,Integer> peaks=new TreeMap<>();spanPoints.forEach((phase,points)->{
            points.sort(Comparator.<long[]>comparingLong(p->p[0]).thenComparingLong(p->p[1]));
            int active=0,peak=0;for(var p:points){active+=(int)p[1];peak=Math.max(peak,active);}peaks.put(phase,peak);
        });out.put("peakConcurrentSpans",peaks);
        slowCalls.sort(Comparator.comparingDouble(m->-((Number)m.get("ms")).doubleValue()));out.put("slowCalls",slowCalls.subList(0,Math.min(60,slowCalls.size())));
        Files.writeString(Path.of(args[1]),json(out));
    }
    static String workScope(List<String> stack){
        for(String frame:stack){
            if(frame.contains(".BFeaturePreparation."))return "canonicalB";
            if(frame.contains(".PhysicalBiomePreparation."))return "physicalBiomes";
            if(frame.contains(".DetachedTerrainInputs."))return "physicalTerrain";
            if(frame.contains(".PhysicalPreFeaturesPreparation."))return "physicalPreFeatures";
        }
        return "other";
    }
    static List<String> stack(RecordedEvent e){
        List<String> result=new ArrayList<>();if(e.getStackTrace()==null)return result;
        for(var f:e.getStackTrace().getFrames())result.add(f.getMethod().getType().getName()+"."+f.getMethod().getName());return result;
    }
    static Map<String,Number> top(Map<String,? extends Number> values,int n){
        Map<String,Number> out=new LinkedHashMap<>();values.entrySet().stream().sorted(Comparator.comparingDouble(e->-e.getValue().doubleValue()))
                .limit(n).forEach(e->out.put(e.getKey(),e.getValue()));return out;
    }
    static String json(Object v){
        if(v==null)return "null";if(v instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
        if(v instanceof Map<?,?> m){List<String> a=new ArrayList<>();m.forEach((k,x)->a.add(json(k.toString())+":"+json(x)));return "{"+String.join(",",a)+"}";}
        if(v instanceof Collection<?> c)return "["+String.join(",",c.stream().map(AnalyzeRecording::json).toList())+"]";return v.toString();
    }
}
