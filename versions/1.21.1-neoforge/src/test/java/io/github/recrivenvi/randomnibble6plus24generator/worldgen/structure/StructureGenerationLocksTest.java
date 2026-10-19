package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.structures.*;
import org.junit.jupiter.api.Test;

class StructureGenerationLocksTest {
    @Test
    void unknownModsKeepTheConservativePath() {
        assertTrue(
                StructureGenerationLocks.supportsMods(
                        List.of("minecraft", "neoforge", ModConstants.MOD_ID)));
        assertFalse(StructureGenerationLocks.supportsMods(List.of("minecraft", "biomesoplenty")));
        assertFalse(
                StructureGenerationLocks.supportsMods(
                        List.of("minecraft", "customskinloader-bootstrap")));
    }

    @Test
    void pieceStateLocksAreStableDistinctAndNotGlobalForJigsaw() {
        Object stronghold = StructureGenerationLocks.sharedPieceLock(StrongholdStructure.class);
        assertNotNull(stronghold);
        assertSame(stronghold, StructureGenerationLocks.sharedPieceLock(StrongholdStructure.class));
        Object fortress = StructureGenerationLocks.sharedPieceLock(NetherFortressStructure.class);
        Object endCity = StructureGenerationLocks.sharedPieceLock(EndCityStructure.class);
        assertNotNull(fortress);
        assertNotNull(endCity);
        assertNotSame(stronghold, fortress);
        assertNotSame(stronghold, endCity);
        assertNotSame(fortress, endCity);
        assertNull(StructureGenerationLocks.sharedPieceLock(JigsawStructure.class));
        assertNull(StructureGenerationLocks.sharedPieceLock(MineshaftStructure.class));
        assertNull(StructureGenerationLocks.sharedPieceLock(Object.class));
    }
}
