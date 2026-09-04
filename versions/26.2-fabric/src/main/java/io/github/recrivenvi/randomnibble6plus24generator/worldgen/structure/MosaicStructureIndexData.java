package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;

/** Persisted locators for structure projections already materialized in Mosaic Chunks. */
final class MosaicStructureIndexData extends SavedData {

    private static final Codec<IndexEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(IndexEntry::dimension),
            Codec.STRING.fieldOf("structure").forGetter(IndexEntry::structure),
            Codec.INT.fieldOf("owner_x").forGetter(IndexEntry::ownerX),
            Codec.INT.fieldOf("owner_z").forGetter(IndexEntry::ownerZ),
            Codec.INT.fieldOf("start_x").forGetter(IndexEntry::startX),
            Codec.INT.fieldOf("start_z").forGetter(IndexEntry::startZ),
            Codec.LONG.fieldOf("local_world_seed").forGetter(IndexEntry::localWorldSeed),
            Codec.INT.fieldOf("anchor_x").forGetter(IndexEntry::anchorX),
            Codec.INT.fieldOf("anchor_y").forGetter(IndexEntry::anchorY),
            Codec.INT.fieldOf("anchor_z").forGetter(IndexEntry::anchorZ),
            Codec.INT.fieldOf("piece_min_x").forGetter(IndexEntry::pieceMinX),
            Codec.INT.fieldOf("piece_min_y").forGetter(IndexEntry::pieceMinY),
            Codec.INT.fieldOf("piece_min_z").forGetter(IndexEntry::pieceMinZ),
            Codec.INT.fieldOf("piece_max_x").forGetter(IndexEntry::pieceMaxX),
            Codec.INT.fieldOf("piece_max_y").forGetter(IndexEntry::pieceMaxY),
            Codec.INT.fieldOf("piece_max_z").forGetter(IndexEntry::pieceMaxZ))
            .apply(instance, IndexEntry::new));

    private static final Codec<MosaicStructureIndexData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, ENTRY_CODEC).fieldOf("entries")
                    .forGetter(MosaicStructureIndexData::entries))
            .apply(instance, MosaicStructureIndexData::new));

    static final SavedDataType<MosaicStructureIndexData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(
                    RandomNibble6Plus24Generator.MOD_ID, "mosaic_structure_index"),
            MosaicStructureIndexData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_WORLD_GEN_SETTINGS);

    private final Map<String, IndexEntry> entries;

    MosaicStructureIndexData() {
        this(Map.of());
    }

    private MosaicStructureIndexData(Map<String, IndexEntry> entries) {
        this.entries = new LinkedHashMap<>(entries);
    }

    Map<String, IndexEntry> entries() {
        return Map.copyOf(entries);
    }

    void replaceOwner(String dimension, int ownerX, int ownerZ, java.util.List<IndexEntry> replacement) {
        Map<String, IndexEntry> next = new LinkedHashMap<>();
        replacement.forEach(entry -> next.put(entry.key(), entry));
        Map<String, IndexEntry> current = new LinkedHashMap<>();
        entries.forEach((key, value) -> {
            if (value.dimension().equals(dimension)
                    && value.ownerX() == ownerX
                    && value.ownerZ() == ownerZ) {
                current.put(key, value);
            }
        });
        if (current.equals(next)) return;
        entries.entrySet().removeIf(entry -> {
            IndexEntry value = entry.getValue();
            return value.dimension().equals(dimension)
                    && value.ownerX() == ownerX
                    && value.ownerZ() == ownerZ;
        });
        entries.putAll(next);
        setDirty();
    }

    boolean removeOwner(String dimension, int ownerX, int ownerZ) {
        boolean removed = entries.entrySet().removeIf(entry -> {
            IndexEntry value = entry.getValue();
            return value.dimension().equals(dimension)
                    && value.ownerX() == ownerX
                    && value.ownerZ() == ownerZ;
        });
        if (removed) setDirty();
        return removed;
    }

    record IndexEntry(
            String dimension,
            String structure,
            int ownerX,
            int ownerZ,
            int startX,
            int startZ,
            long localWorldSeed,
            int anchorX,
            int anchorY,
            int anchorZ,
            int pieceMinX,
            int pieceMinY,
            int pieceMinZ,
            int pieceMaxX,
            int pieceMaxY,
            int pieceMaxZ) {

        String key() {
            return dimension + ":" + Long.toUnsignedString(net.minecraft.world.level.ChunkPos.pack(ownerX, ownerZ))
                    + ":" + structure + ":"
                    + Long.toUnsignedString(net.minecraft.world.level.ChunkPos.pack(startX, startZ));
        }
    }
}
