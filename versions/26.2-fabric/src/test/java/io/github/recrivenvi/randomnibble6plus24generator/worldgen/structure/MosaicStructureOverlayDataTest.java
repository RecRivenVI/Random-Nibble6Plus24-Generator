package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class MosaicStructureOverlayDataTest {

    @Test
    void emptyReplacementRemovesProjectionAndMarksSavedDataDirty() {
        MosaicStructureOverlayData data = new MosaicStructureOverlayData();
        CompoundTag start = new CompoundTag();
        start.putString("id", "minecraft:monument");
        data.put("minecraft:overworld:1", new MosaicStructureOverlayData.ChunkProjection(
                0, 0, 123L, List.of(start)));

        data.setDirty(false);
        assertTrue(data.remove("minecraft:overworld:1"));
        assertNull(data.get("minecraft:overworld:1"));
        assertTrue(data.isDirty());
        assertFalse(data.remove("minecraft:overworld:1"));
    }

    @Test
    void identicalProjectionReplacementIsIdempotent() {
        MosaicStructureOverlayData data = new MosaicStructureOverlayData();
        MosaicStructureOverlayData.ChunkProjection projection =
                new MosaicStructureOverlayData.ChunkProjection(2, -3, 9L, List.of());
        data.put("owner", projection);
        data.setDirty(false);
        data.put("owner", projection);
        assertFalse(data.isDirty());
    }
}
