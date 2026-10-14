package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.test.MinecraftTestBootstrap;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.structures.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureGenerationLocksTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureBootstrapped();
    }

    @Test
    void staticPieceStateHasJvmWideButIndependentLocks() {
        Object stronghold = StructureGenerationLocks.sharedPieceLock(StrongholdStructure.class);
        Object fortress = StructureGenerationLocks.sharedPieceLock(NetherFortressStructure.class);
        Object city = StructureGenerationLocks.sharedPieceLock(EndCityStructure.class);
        assertNotNull(stronghold);
        assertNotNull(fortress);
        assertNotNull(city);
        assertSame(stronghold, StructureGenerationLocks.sharedPieceLock(StrongholdStructure.class));
        assertNotSame(stronghold, fortress);
        assertNotSame(fortress, city);
        assertNotSame(stronghold, city);
        assertNull(StructureGenerationLocks.sharedPieceLock(JigsawStructure.class));
        assertNull(StructureGenerationLocks.sharedPieceLock(MineshaftStructure.class));
        assertNull(StructureGenerationLocks.sharedPieceLock(OceanMonumentStructure.class));
    }

    @Test
    void unknownPoolElementIncludingNestedElementsMustFallback() {
        var empty = EmptyPoolElement.INSTANCE;
        var known = new ListPoolElement(List.of(empty), StructureTemplatePool.Projection.RIGID);
        var unknown =
                new ListPoolElement(List.of(empty), StructureTemplatePool.Projection.RIGID) {};
        assertTrue(StructureGenerationLocks.supportsElement(empty));
        assertTrue(StructureGenerationLocks.supportsElement(known));
        assertFalse(StructureGenerationLocks.supportsElement(unknown));
        assertFalse(
                StructureGenerationLocks.supportsElement(
                        new ListPoolElement(
                                List.of(known, unknown), StructureTemplatePool.Projection.RIGID)));
    }
}
