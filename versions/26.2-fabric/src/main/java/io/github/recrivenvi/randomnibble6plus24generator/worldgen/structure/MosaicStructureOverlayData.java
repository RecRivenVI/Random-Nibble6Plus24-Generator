package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;

/** Persisted external-piece projections for the loaded-Chunk Structure Overlay V1. */
final class MosaicStructureOverlayData extends SavedData {

    private static final Codec<ChunkProjection> CHUNK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("chunk_x").forGetter(ChunkProjection::chunkX),
            Codec.INT.fieldOf("chunk_z").forGetter(ChunkProjection::chunkZ),
            Codec.LONG.fieldOf("local_world_seed").forGetter(ChunkProjection::localWorldSeed),
            CompoundTag.CODEC.listOf().fieldOf("external_starts").forGetter(ChunkProjection::externalStarts))
            .apply(instance, ChunkProjection::new));

    private static final Codec<MosaicStructureOverlayData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, CHUNK_CODEC).fieldOf("chunks")
                    .forGetter(MosaicStructureOverlayData::chunks))
            .apply(instance, MosaicStructureOverlayData::new));

    static final SavedDataType<MosaicStructureOverlayData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(
                    RandomNibble6Plus24Generator.MOD_ID, "mosaic_structure_overlay"),
            MosaicStructureOverlayData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS);

    private final Map<String, ChunkProjection> chunks;

    MosaicStructureOverlayData() {
        this(Map.of());
    }

    private MosaicStructureOverlayData(Map<String, ChunkProjection> chunks) {
        this.chunks = new LinkedHashMap<>(chunks);
    }

    Map<String, ChunkProjection> chunks() {
        return Map.copyOf(chunks);
    }

    ChunkProjection get(String key) {
        return chunks.get(key);
    }

    void put(String key, ChunkProjection projection) {
        if (Objects.equals(chunks.get(key), projection)) return;
        chunks.put(key, projection);
        setDirty();
    }

    boolean remove(String key) {
        if (chunks.remove(key) == null) return false;
        setDirty();
        return true;
    }

    record ChunkProjection(int chunkX, int chunkZ, long localWorldSeed, List<CompoundTag> externalStarts) {
        ChunkProjection {
            externalStarts = externalStarts.stream().map(CompoundTag::copy).toList();
        }
    }
}
