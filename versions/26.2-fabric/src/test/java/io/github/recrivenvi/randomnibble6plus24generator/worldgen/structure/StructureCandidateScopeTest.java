package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StructureCandidateScopeTest {
    @Test void retainsConservativeV2DependencyScope() {
        assertEquals(11, StructureCandidateScope.forV2Target(ChunkPos.ZERO, 3, List.of()).metrics().radius());
    }
    @Test void everyNegativeAndPositiveSourceBelongsToEnumeratedCells() {
        for (int spacing : new int[]{1,2,3,16,32,80,4096}) {
            for (ChunkPos target : List.of(ChunkPos.ZERO,new ChunkPos(-1,-1),new ChunkPos(-4097,4095),new ChunkPos(125,-37))) {
                var cells=StructureCandidateScope.cellRange(target,11,spacing,0);
                assertNotNull(cells);
                for(int z=target.z()-11;z<=target.z()+11;z++)for(int x=target.x()-11;x<=target.x()+11;x++){
                    int cx=Math.floorDiv(x,spacing),cz=Math.floorDiv(z,spacing);
                    assertTrue(cx>=cells.minX()&&cx<=cells.maxX()&&cz>=cells.minZ()&&cz<=cells.maxZ());
                }
            }
        }
    }
    @Test void rejectsIntegerOverflowInsteadOfLosingWrappedCandidates() {
        assertNull(StructureCandidateScope.cellRange(new ChunkPos(Integer.MIN_VALUE,0),11,32,8));
        assertNull(StructureCandidateScope.cellRange(new ChunkPos(Integer.MAX_VALUE,0),11,32,8));
        assertNull(StructureCandidateScope.cellRange(new ChunkPos(Integer.MIN_VALUE+1,0),0,3,0));
        assertNotNull(StructureCandidateScope.cellRange(new ChunkPos(-1875000,1875000),11,4096,4095));
    }
    @Test void invalidProgrammaticGridsAreNotSilentlyCorrected() {
        assertNull(StructureCandidateScope.cellRange(ChunkPos.ZERO,11,0,0));
        assertNull(StructureCandidateScope.cellRange(ChunkPos.ZERO,11,2,2));
        assertNull(StructureCandidateScope.cellRange(ChunkPos.ZERO,11,2,-1));
    }
    @Test void unprovenScopeUsesTheOriginalList() {
        var definitions=List.<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.StructureSet>>of();
        var scope=new StructureCandidateScope(ChunkPos.ZERO,3,65,definitions);
        assertSame(definitions,scope.forSource(ChunkPos.ZERO,definitions));
        assertEquals(1,scope.metrics().queryFallbacks());
    }
    @Test void changedDefinitionViewAndOutOfScopeQueriesFallback() {
        var definitions=new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.StructureSet>>();
        var other=new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.StructureSet>>();
        var scope=StructureCandidateScope.forV2Target(ChunkPos.ZERO,3,definitions);
        assertSame(other,scope.forSource(ChunkPos.ZERO,other));
        assertSame(definitions,scope.forSource(new ChunkPos(12,0),definitions));
        assertEquals(2,scope.metrics().queryFallbacks());
    }
}
