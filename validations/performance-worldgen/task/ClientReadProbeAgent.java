import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Read-only live JVM observer. No bytecode transformation, input, game command, or world mutation. */
public class ClientReadProbeAgent {
    public static void agentmain(String output,Instrumentation instrumentation)throws Exception{
        Class<?> minecraft=Arrays.stream(instrumentation.getAllLoadedClasses())
                .filter(c->c.getName().equals("net.minecraft.client.Minecraft")).findFirst().orElseThrow();
        ClassLoader loader=minecraft.getClassLoader();
        Class<?> posType=loader.loadClass("net.minecraft.core.BlockPos");
        Class<?> statusType=loader.loadClass("net.minecraft.world.level.chunk.status.ChunkStatus");
        Class<?> sectionType=loader.loadClass("net.minecraft.core.SectionPos");
        Object full=statusType.getField("FULL").get(null);
        Thread observer=new Thread(()->{
            try(var writer=Files.newBufferedWriter(Path.of(output))){
                for(int i=0;i<600;i++){
                    Map<String,Object> row=new LinkedHashMap<>();row.put("epochMillis",System.currentTimeMillis());
                    try{
                        Object mc=minecraft.getMethod("getInstance").invoke(null);
                        Object level=field(mc,"level"),player=field(mc,"player"),renderer=field(mc,"levelRenderer");
                        if(level==null||player==null){row.put("state","no-client-world");}
                        else{
                            Object key=call(level,"dimension");row.put("dimension",call(key,"identifier").toString());
                            Object playerPos=call(player,"blockPosition");row.put("player",playerPos.toString());
                            Object source=call(level,"getChunkSource");row.put("loadedClientChunks",call(source,"getLoadedChunksCount"));
                            Object dispatcher=call(renderer,"sectionRenderDispatcher");
                            if(dispatcher!=null){row.put("compileQueue",call(dispatcher,"getCompileQueueSize"));row.put("freeBuffers",call(dispatcher,"getFreeBufferCount"));}
                            Object hit=field(mc,"hitResult");
                            Object pos=hit!=null&&call(hit,"getType").toString().equals("BLOCK")?call(hit,"getBlockPos"):playerPos;
                            row.put("isBlockHit",hit!=null&&call(hit,"getType").toString().equals("BLOCK"));row.put("observedPos",pos.toString());
                            int x=((Number)call(pos,"getX")).intValue()>>4,z=((Number)call(pos,"getZ")).intValue()>>4;
                            Object chunk=level.getClass().getMethod("getChunk",int.class,int.class,statusType,boolean.class).invoke(level,x,z,full,false);
                            row.put("targetChunkPresent",chunk!=null);
                            if(chunk!=null)row.put("blockState",chunk.getClass().getMethod("getBlockState",posType).invoke(chunk,pos).toString());
                            row.put("compiledAndVisible",renderer.getClass().getMethod("isSectionCompiledAndVisible",posType).invoke(renderer,pos));
                            Object area=field(renderer,"viewArea");
                            if(area!=null){
                                Object section=area.getClass().getMethod("getRenderSectionAt",posType).invoke(area,pos);
                                if(section!=null){Object mesh=((AtomicReference<?>)field(section,"sectionMesh")).get();row.put("meshClass",mesh.getClass().getName());}
                            }
                            Object light=call(level,"getLightEngine");List<String> missing=new ArrayList<>();
                            for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++)if(dx!=0||dz!=0){
                                Object n=level.getClass().getMethod("getChunk",int.class,int.class,statusType,boolean.class).invoke(level,x+dx,z+dz,full,false);
                                long node=((Number)sectionType.getMethod("asLong",int.class,int.class,int.class).invoke(null,x+dx,0,z+dz)).longValue();
                                boolean on=(boolean)light.getClass().getMethod("lightOnInColumn",long.class).invoke(light,node);
                                if(n==null||!on)missing.add((x+dx)+","+(z+dz)+":chunk="+(n!=null)+",light="+on);
                            }
                            row.put("missingNeighborColumns",missing);
                        }
                    }catch(Throwable e){row.put("error",e.toString());if(e.getCause()!=null)row.put("cause",e.getCause().toString());}
                    writer.write(json(row));writer.newLine();writer.flush();Thread.sleep(1000);
                }
            }catch(Exception e){System.err.println("READ-ONLY CLIENT PROBE "+e);}
        },"mosaic-client-read-observer");observer.setDaemon(true);observer.start();
    }
    static Object field(Object o,String name)throws Exception{
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object o,String name)throws Exception{return o.getClass().getMethod(name).invoke(o);}
    static String json(Object v){
        if(v==null)return "null";if(v instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";
        if(v instanceof Map<?,?> m){List<String>a=new ArrayList<>();m.forEach((k,x)->a.add(json(k.toString())+":"+json(x)));return "{"+String.join(",",a)+"}";}
        if(v instanceof Collection<?> c)return "["+String.join(",",c.stream().map(ClientReadProbeAgent::json).toList())+"]";return v.toString();
    }
}
