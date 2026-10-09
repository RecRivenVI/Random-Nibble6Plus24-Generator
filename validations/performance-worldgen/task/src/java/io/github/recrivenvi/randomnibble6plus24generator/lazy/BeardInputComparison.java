package io.github.recrivenvi.randomnibble6plus24generator.lazy;

import java.util.*;
import java.util.regex.*;
import com.google.gson.*;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.*;

/** Diagnostic only. Never sorts the native generator's inputs or changes density calculations. */
public final class BeardInputComparison {
    private static final Pattern BOX=Pattern.compile("BoundingBox\\{minX=(-?\\d+), minY=(-?\\d+), minZ=(-?\\d+), maxX=(-?\\d+), maxY=(-?\\d+), maxZ=(-?\\d+)\\}");
    private static final Pattern RIGID=Pattern.compile("Rigid\\[box=(BoundingBox\\{[^}]+\\}), terrainAdjustment=([A-Z_]+), groundLevelDelta=(-?\\d+)\\]");
    public static JsonObject compare(JsonObject expected,JsonObject actual,LevelHeightAccessor height){
        if(!expected.keySet().equals(actual.keySet()))throw new IllegalStateException("Beardifier query domains differ");
        JsonObject result=new JsonObject();JsonArray changed=new JsonArray();long samples=0;
        for(String key:expected.keySet()){
            String a=expected.get(key).getAsString(),b=actual.get(key).getAsString();
            if(a.equals(b))continue;
            // Vanilla references use HashMap<Structure,...> and Structure identity hash codes.
            // Preserve raw order in evidence, compare every field and duplicate multiplicity,
            // then verify native double results. Sorting alone is NOT an equivalence test.
            if(!Arrays.stream(a.split(";")).sorted().toList().equals(Arrays.stream(b.split(";")).sorted().toList()))
                throw new IllegalStateException("Different Beardifier pieces/junctions/affected box at "+key);
            Beardifier left=decode(a),right=decode(b);
            String[] coords=key.substring(1,key.length()-1).split(", ");
            int x=Integer.parseInt(coords[0])*16,z=Integer.parseInt(coords[1])*16;
            // Includes the next cell boundary sampled by native NoiseChunk interpolation.
            for(int dx=0;dx<=16;dx++)for(int dz=0;dz<=16;dz++)for(int y=height.getMinY();y<=height.getMaxY()+1;y++){
                var point=new DensityFunction.SinglePointContext(x+dx,y,z+dz);
                double va=left.compute(point),vb=right.compute(point);samples++;
                if(Double.doubleToRawLongBits(va)!=Double.doubleToRawLongBits(vb))
                    throw new IllegalStateException("Beardifier raw density differs at "+point+" A="+Double.toHexString(va)+" B="+Double.toHexString(vb));
            }
            changed.add(key);
        }
        result.add("nativeMapOrderDifferentChunks",changed);result.addProperty("rawDoubleSamplesCompared",samples);
        result.addProperty("differentRawDoubles",0);return result;
    }
    private static Beardifier decode(String dump){
        List<Beardifier.Rigid> pieces=new ArrayList<>();List<JigsawJunction> junctions=new ArrayList<>();BoundingBox affected=null;
        boolean foundBox=false;
        for(String token:dump.split(";")){
            if(token.startsWith("Rigid[")){
                Matcher m=RIGID.matcher(token);if(!m.matches())throw new IllegalArgumentException(token);
                pieces.add(new Beardifier.Rigid(box(m.group(1)),TerrainAdjustment.valueOf(m.group(2)),Integer.parseInt(m.group(3))));
            }else if(token.startsWith("box=")){
                foundBox=true;affected=token.equals("box=null")?null:box(token.substring(4));
            }else{
                String[] fields=token.split(",");if(fields.length!=5)throw new IllegalArgumentException(token);
                junctions.add(new JigsawJunction(Integer.parseInt(fields[0]),Integer.parseInt(fields[1]),Integer.parseInt(fields[2]),Integer.parseInt(fields[3]),StructureTemplatePool.Projection.valueOf(fields[4])));
            }
        }
        if(!foundBox)throw new IllegalArgumentException("No native affected box");
        return new Beardifier(pieces,junctions,affected);
    }
    private static BoundingBox box(String token){
        Matcher m=BOX.matcher(token);if(!m.matches())throw new IllegalArgumentException(token);
        return new BoundingBox(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)),Integer.parseInt(m.group(6)));
    }
}
