package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MosaicStructureIndexDataTest {

    @Test
    void ownerReplacementIsIdempotentAndRemovable() {
        MosaicStructureIndexData data = new MosaicStructureIndexData();
        MosaicStructureIndexData.IndexEntry entry = new MosaicStructureIndexData.IndexEntry(
                "minecraft:overworld", "minecraft:monument", 3, 4, 3, 4, 9L,
                56, 50, 72, 48, 40, 64, 64, 60, 80);
        data.replaceOwner("minecraft:overworld", 3, 4, List.of(entry));
        assertEquals(1, data.entries().size());
        data.setDirty(false);
        data.replaceOwner("minecraft:overworld", 3, 4, List.of(entry));
        assertFalse(data.isDirty());
        assertTrue(data.removeOwner("minecraft:overworld", 3, 4));
        assertTrue(data.entries().isEmpty());
    }
}
