package io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;

/** Native SavedData envelope for the frozen owner-scoped V1 structure projection fields. */
public final class MosaicStructureOverlayData extends SavedData {
    public static final String ID = ModConstants.MOD_ID + "/mosaic_structure_overlay";
    private static final Codec<Projection> ENTRY =
            RecordCodecBuilder.create(
                    instance ->
                            instance.group(
                                            Codec.INT.fieldOf("chunk_x").forGetter(Projection::x),
                                            Codec.INT.fieldOf("chunk_z").forGetter(Projection::z),
                                            Codec.LONG
                                                    .fieldOf("local_world_seed")
                                                    .forGetter(Projection::seed),
                                            CompoundTag.CODEC
                                                    .listOf()
                                                    .fieldOf("external_starts")
                                                    .forGetter(Projection::starts))
                                    .apply(instance, Projection::new));
    private static final Codec<MosaicStructureOverlayData> CODEC =
            Codec.unboundedMap(Codec.STRING, ENTRY)
                    .fieldOf("chunks")
                    .codec()
                    .xmap(MosaicStructureOverlayData::new, data -> data.chunks);
    public static final Factory<MosaicStructureOverlayData> FACTORY =
            new Factory<>(
                    MosaicStructureOverlayData::new,
                    (tag, registry) -> CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    private final Map<String, Projection> chunks;

    public MosaicStructureOverlayData() {
        this(Map.of());
    }

    private MosaicStructureOverlayData(Map<String, Projection> chunks) {
        this.chunks = new LinkedHashMap<>(chunks);
    }

    public Projection get(String key) {
        return chunks.get(key);
    }

    public void replace(String key, Projection projection) {
        if (projection.starts().isEmpty()) {
            if (chunks.remove(key) != null) setDirty();
        } else if (!projection.equals(chunks.get(key))) {
            chunks.put(key, projection);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag ignored, HolderLookup.Provider registry) {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    public record Projection(int x, int z, long seed, List<CompoundTag> starts) {
        public Projection {
            starts = starts.stream().map(CompoundTag::copy).toList();
        }

        @Override
        public List<CompoundTag> starts() {
            return starts.stream().map(CompoundTag::copy).toList();
        }
    }
}
