package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.ticks.SavedTick;

/** Complete deterministic ProtoChunk state used only by the native FEATURES audit. */
public final class FeatureStageSnapshot {

    private static final int FORMAT_VERSION = 1;

    public record Diff(
            boolean equivalent,
            int differingBlocks,
            String firstDifference,
            Map<String, Integer> blockCategories) {
    }

    private final String dimension;
    private final ChunkPos chunkPos;
    private final int minY;
    private final int height;
    private final String status;
    private final String[] blocks;
    private final String[] biomes;
    private final Map<String, long[]> heightmaps;
    private final Map<String, String> structureStarts;
    private final Map<String, long[]> structureReferences;
    private final boolean carvingMaskPresent;
    private final long[] carvingMask;
    private final Map<String, String> blockEntities;
    private final List<String> blockTicks;
    private final List<String> fluidTicks;
    private final Map<Integer, short[]> postProcessing;
    private final List<String> entities;
    private final String hash;
    private final String worldgenDataHash;

    private FeatureStageSnapshot(
            String dimension,
            ChunkPos chunkPos,
            int minY,
            int height,
            String status,
            String[] blocks,
            String[] biomes,
            Map<String, long[]> heightmaps,
            Map<String, String> structureStarts,
            Map<String, long[]> structureReferences,
            boolean carvingMaskPresent,
            long[] carvingMask,
            Map<String, String> blockEntities,
            List<String> blockTicks,
            List<String> fluidTicks,
            Map<Integer, short[]> postProcessing,
            List<String> entities) {
        this.dimension = dimension;
        this.chunkPos = chunkPos;
        this.minY = minY;
        this.height = height;
        this.status = status;
        this.blocks = blocks;
        this.biomes = biomes;
        this.heightmaps = heightmaps;
        this.structureStarts = structureStarts;
        this.structureReferences = structureReferences;
        this.carvingMaskPresent = carvingMaskPresent;
        this.carvingMask = carvingMask;
        this.blockEntities = blockEntities;
        this.blockTicks = blockTicks;
        this.fluidTicks = fluidTicks;
        this.postProcessing = postProcessing;
        this.entities = entities;
        this.hash = calculateHash();
        this.worldgenDataHash = calculateHash(false);
    }

    public static FeatureStageSnapshot capture(
            String dimension,
            ChunkAccess chunk,
            RegistryAccess registryAccess) {
        return capture(dimension, chunk, registryAccess, false);
    }

    static FeatureStageSnapshot captureWithCanonicalEntityUuids(
            String dimension,
            ChunkAccess chunk,
            RegistryAccess registryAccess) {
        return capture(dimension, chunk, registryAccess, true);
    }

    private static FeatureStageSnapshot capture(
            String dimension,
            ChunkAccess chunk,
            RegistryAccess registryAccess,
            boolean normalizeEntityUuids) {
        if (!(chunk instanceof ProtoChunk protoChunk)) {
            throw new IllegalArgumentException(
                    "Stage-exact FEATURES snapshot requires ProtoChunk, found " + chunk.getClass().getName());
        }
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinY();
        int height = chunk.getHeight();
        String[] blocks = captureBlocks(chunk);
        String[] biomes = captureBiomes(chunk);
        Map<String, long[]> heightmaps = new TreeMap<>();
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            heightmaps.put(entry.getKey().getSerializationKey(), entry.getValue().getRawData().clone());
        }

        Registry<Structure> structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Map<String, String> starts = new TreeMap<>();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            starts.put(
                    Objects.toString(structures.getKey(entry.getKey())),
                    "valid=" + start.isValid()
                            + ",start=" + start.getChunkPos()
                            + ",references=" + start.getReferences()
                            + ",pieces=" + start.getPieces().size()
                            + ",box=" + (start.isValid() ? start.getBoundingBox() : "null"));
        }
        Map<String, long[]> references = new TreeMap<>();
        for (var entry : chunk.getAllReferences().entrySet()) {
            long[] values = entry.getValue().toLongArray();
            Arrays.sort(values);
            references.put(Objects.toString(structures.getKey(entry.getKey())), values);
        }

        CarvingMask mask = protoChunk.getCarvingMask();
        Map<String, String> blockEntities = captureBlockEntities(protoChunk, registryAccess);
        ChunkAccess.PackedTicks packedTicks = protoChunk.getTicksForSerialization(0L);
        List<String> blockTicks = packedTicks.blocks().stream()
                .map(FeatureStageSnapshot::normalizeBlockTick)
                .toList();
        List<String> fluidTicks = packedTicks.fluids().stream()
                .map(FeatureStageSnapshot::normalizeFluidTick)
                .toList();
        Map<Integer, short[]> postProcessing = new TreeMap<>();
        var packedPostProcessing = protoChunk.getPostProcessing();
        for (int section = 0; section < packedPostProcessing.length; section++) {
            if (packedPostProcessing[section] != null && !packedPostProcessing[section].isEmpty()) {
                postProcessing.put(section, packedPostProcessing[section].toShortArray());
            }
        }
        List<String> entities = protoChunk.getEntities().stream()
                .map(tag -> canonicalTag(tag, normalizeEntityUuids, true))
                .toList();

        return new FeatureStageSnapshot(
                dimension,
                chunkPos,
                minY,
                height,
                chunk.getPersistedStatus().toString(),
                blocks,
                biomes,
                immutableLongMap(heightmaps),
                immutableStringMap(starts),
                immutableLongMap(references),
                mask != null,
                mask == null ? new long[0] : mask.toArray().clone(),
                immutableStringMap(blockEntities),
                List.copyOf(blockTicks),
                List.copyOf(fluidTicks),
                immutableShortMap(postProcessing),
                List.copyOf(entities));
    }

    public void write(Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(path))))) {
                output.writeInt(FORMAT_VERSION);
                output.writeUTF(dimension);
                output.writeInt(chunkPos.x());
                output.writeInt(chunkPos.z());
                output.writeInt(minY);
                output.writeInt(height);
                output.writeUTF(status);
                writeStrings(output, blocks);
                writeStrings(output, biomes);
                writeLongMap(output, heightmaps);
                writeStringMap(output, structureStarts);
                writeLongMap(output, structureReferences);
                output.writeBoolean(carvingMaskPresent);
                writeLongs(output, carvingMask);
                writeStringMap(output, blockEntities);
                writeStringList(output, blockTicks);
                writeStringList(output, fluidTicks);
                writeShortMap(output, postProcessing);
                writeStringList(output, entities);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write FEATURES snapshot " + path, exception);
        }
    }

    public static FeatureStageSnapshot read(Path path) {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(path))))) {
            int version = input.readInt();
            if (version != FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported FEATURES snapshot format " + version);
            }
            return new FeatureStageSnapshot(
                    input.readUTF(),
                    new ChunkPos(input.readInt(), input.readInt()),
                    input.readInt(),
                    input.readInt(),
                    input.readUTF(),
                    readStrings(input),
                    readStrings(input),
                    readLongMap(input),
                    readStringMap(input),
                    readLongMap(input),
                    input.readBoolean(),
                    readLongs(input),
                    readStringMap(input),
                    readStringList(input),
                    readStringList(input),
                    readShortMap(input),
                    readStringList(input));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read FEATURES snapshot " + path, exception);
        }
    }

    public Diff diff(FeatureStageSnapshot other) {
        String metaDifference = firstMetaDifference(other);
        int differingBlocks = 0;
        String firstDifference = metaDifference;
        Map<String, Integer> categories = new TreeMap<>();
        if (blocks.length != other.blocks.length) {
            return new Diff(false, Math.max(blocks.length, other.blocks.length),
                    "Block array length " + blocks.length + " != " + other.blocks.length, Map.of());
        }
        for (int index = 0; index < blocks.length; index++) {
            if (!blocks[index].equals(other.blocks[index])) {
                differingBlocks++;
                categories.merge(classify(blocks[index], other.blocks[index]), 1, Integer::sum);
                if (firstDifference == null) {
                    int y = minY + index / 256;
                    int layerIndex = index % 256;
                    int z = layerIndex / 16;
                    int x = layerIndex % 16;
                    firstDifference = "BlockState at "
                            + (chunkPos.getMinBlockX() + x) + "," + y + "," + (chunkPos.getMinBlockZ() + z)
                            + ": " + blocks[index] + " != " + other.blocks[index];
                }
            }
        }
        if (firstDifference == null) {
            firstDifference = firstNonBlockDifference(other);
        }
        return new Diff(firstDifference == null, differingBlocks, firstDifference, Map.copyOf(categories));
    }

    public String hash() {
        return hash;
    }

    public String worldgenDataHash() {
        return worldgenDataHash;
    }

    public ChunkPos chunkPos() {
        return chunkPos;
    }

    public int blockEntityCount() {
        return blockEntities.size();
    }

    public int blockTickCount() {
        return blockTicks.size();
    }

    public int fluidTickCount() {
        return fluidTicks.size();
    }

    public int entityCount() {
        return entities.size();
    }

    public List<String> entityNbt() {
        return entities;
    }

    public int postProcessingCount() {
        return postProcessing.values().stream().mapToInt(values -> values.length).sum();
    }

    public Map<String, String> blockEntityNbt() {
        return blockEntities;
    }

    public List<String> blockTickData() {
        return blockTicks;
    }

    public List<String> fluidTickData() {
        return fluidTicks;
    }

    public Map<String, String> structureStartData() {
        return structureStarts;
    }

    private String firstMetaDifference(FeatureStageSnapshot other) {
        if (!dimension.equals(other.dimension)) return "Dimension " + dimension + " != " + other.dimension;
        if (!chunkPos.equals(other.chunkPos)) return "ChunkPos " + chunkPos + " != " + other.chunkPos;
        if (minY != other.minY || height != other.height) return "Height range differs";
        if (!status.equals(other.status)) return "Status " + status + " != " + other.status;
        return null;
    }

    private String firstNonBlockDifference(FeatureStageSnapshot other) {
        if (!Arrays.equals(biomes, other.biomes)) return firstArrayDifference("Biome", biomes, other.biomes);
        String mapDifference = firstLongMapDifference("Heightmap", heightmaps, other.heightmaps);
        if (mapDifference != null) return mapDifference;
        if (!structureStarts.equals(other.structureStarts)) return "Structure starts differ";
        mapDifference = firstLongMapDifference("Structure references", structureReferences, other.structureReferences);
        if (mapDifference != null) return mapDifference;
        if (carvingMaskPresent != other.carvingMaskPresent || !Arrays.equals(carvingMask, other.carvingMask)) {
            return "CarvingMask differs";
        }
        if (!blockEntities.equals(other.blockEntities)) return "BlockEntities differ";
        if (!blockTicks.equals(other.blockTicks)) return "Scheduled block ticks differ";
        if (!fluidTicks.equals(other.fluidTicks)) return "Scheduled fluid ticks differ";
        String shortDifference = firstShortMapDifference(postProcessing, other.postProcessing);
        if (shortDifference != null) return shortDifference;
        if (!entities.equals(other.entities)) return "ProtoChunk entities differ";
        return null;
    }

    public String deterministicMetadataDifference(FeatureStageSnapshot other) {
        return firstMetaDifference(other) != null ? firstMetaDifference(other) : firstNonBlockDifference(other);
    }

    private String calculateHash() {
        return calculateHash(true);
    }

    private String calculateHash(boolean includeStatus) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, dimension);
            update(digest, chunkPos.x());
            update(digest, chunkPos.z());
            update(digest, minY);
            update(digest, height);
            if (includeStatus) update(digest, status);
            for (String value : blocks) update(digest, value);
            for (String value : biomes) update(digest, value);
            updateLongMap(digest, heightmaps);
            for (var entry : structureStarts.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
            }
            updateLongMap(digest, structureReferences);
            digest.update((byte) (carvingMaskPresent ? 1 : 0));
            for (long value : carvingMask) update(digest, value);
            for (var entry : blockEntities.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue());
            }
            for (String value : blockTicks) update(digest, value);
            for (String value : fluidTicks) update(digest, value);
            for (var entry : postProcessing.entrySet()) {
                update(digest, entry.getKey());
                for (short value : entry.getValue()) update(digest, value);
            }
            for (String value : entities) update(digest, value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String[] captureBlocks(ChunkAccess chunk) {
        String[] values = new String[16 * 16 * chunk.getHeight()];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = chunk.getBlockState(cursor.set(
                            chunk.getPos().getMinBlockX() + x,
                            y,
                            chunk.getPos().getMinBlockZ() + z));
                    values[index++] = state.toString();
                }
            }
        }
        return values;
    }

    private static String[] captureBiomes(ChunkAccess chunk) {
        int quartHeight = chunk.getHeight() / QuartPos.SIZE;
        String[] values = new String[4 * 4 * quartHeight];
        int index = 0;
        int minQuartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int minQuartY = QuartPos.fromBlock(chunk.getMinY());
        int minQuartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        for (int y = 0; y < quartHeight; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    Holder<Biome> biome = chunk.getNoiseBiome(minQuartX + x, minQuartY + y, minQuartZ + z);
                    values[index++] = biome.unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElseGet(() -> biome.value().toString());
                }
            }
        }
        return values;
    }

    private static Map<String, String> captureBlockEntities(
            ProtoChunk chunk,
            RegistryAccess registryAccess) {
        Set<BlockPos> positions = new TreeSet<>((left, right) -> {
            int compareY = Integer.compare(left.getY(), right.getY());
            if (compareY != 0) return compareY;
            int compareZ = Integer.compare(left.getZ(), right.getZ());
            if (compareZ != 0) return compareZ;
            return Integer.compare(left.getX(), right.getX());
        });
        positions.addAll(chunk.getBlockEntities().keySet());
        positions.addAll(chunk.getBlockEntityNbts().keySet());
        Map<String, String> result = new TreeMap<>();
        for (BlockPos pos : positions) {
            CompoundTag tag = chunk.getBlockEntityNbtForSaving(pos, registryAccess);
            result.put(pos.getX() + "," + pos.getY() + "," + pos.getZ(), tag == null ? "null" : canonicalTag(tag));
        }
        return result;
    }

    private static String canonicalTag(Tag tag) {
        return canonicalTag(tag, false, false);
    }

    private static String canonicalTag(Tag tag, boolean normalizeEntityUuids, boolean entityRoot) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (String key : new TreeSet<>(compound.keySet())) {
                if (!first) builder.append(',');
                first = false;
                builder.append(key).append(':');
                if (normalizeEntityUuids && entityRoot && isEntityUuidKey(key)) {
                    builder.append("<excluded-entity-uuid>");
                } else {
                    builder.append(canonicalTag(compound.get(key), normalizeEntityUuids, false));
                }
            }
            return builder.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) builder.append(',');
                builder.append(canonicalTag(list.get(index), normalizeEntityUuids, false));
            }
            return builder.append(']').toString();
        }
        return tag.getId() + ":" + tag;
    }

    private static boolean isEntityUuidKey(String key) {
        return key.equals("UUID") || key.equals("UUIDMost") || key.equals("UUIDLeast");
    }

    private static String normalizeBlockTick(SavedTick<net.minecraft.world.level.block.Block> tick) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(tick.type());
        return Objects.toString(id) + "@" + tick.pos() + ":" + tick.delay() + ":" + tick.priority();
    }

    private static String normalizeFluidTick(SavedTick<net.minecraft.world.level.material.Fluid> tick) {
        Identifier id = BuiltInRegistries.FLUID.getKey(tick.type());
        return Objects.toString(id) + "@" + tick.pos() + ":" + tick.delay() + ":" + tick.priority();
    }

    private static String classify(String left, String right) {
        String value = (left + ' ' + right).toLowerCase(java.util.Locale.ROOT);
        if (value.contains("_log") || value.contains("_wood")) return "logs";
        if (value.contains("leaves")) return "leaves";
        if (value.contains("ore")) return "ores";
        if (value.contains("grass") || value.contains("flower") || value.contains("bush")
                || value.contains("vine") || value.contains("mushroom") || value.contains("sapling")) {
            return "vegetation";
        }
        if (value.contains("stone") || value.contains("deepslate") || value.contains("dirt")
                || value.contains("gravel") || value.contains("sand")) return "stone_variants";
        if (value.contains("chest") || value.contains("spawner") || value.contains("brick")
                || value.contains("planks")) return "structure_blocks";
        return "other";
    }

    private static String firstArrayDifference(String label, String[] left, String[] right) {
        if (left.length != right.length) return label + " length differs";
        for (int index = 0; index < left.length; index++) {
            if (!left[index].equals(right[index])) return label + " index " + index + " differs";
        }
        return null;
    }

    private static String firstLongMapDifference(String label, Map<String, long[]> left, Map<String, long[]> right) {
        if (!left.keySet().equals(right.keySet())) return label + " keys differ";
        for (String key : left.keySet()) {
            if (!Arrays.equals(left.get(key), right.get(key))) return label + " " + key + " differs";
        }
        return null;
    }

    private static String firstShortMapDifference(Map<Integer, short[]> left, Map<Integer, short[]> right) {
        if (!left.keySet().equals(right.keySet())) return "Post-processing section keys differ";
        for (Integer key : left.keySet()) {
            if (!Arrays.equals(left.get(key), right.get(key))) return "Post-processing section " + key + " differs";
        }
        return null;
    }

    private static Map<String, long[]> immutableLongMap(Map<String, long[]> source) {
        Map<String, long[]> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(key, value.clone()));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source) {
        return java.util.Collections.unmodifiableMap(new TreeMap<>(source));
    }

    private static Map<Integer, short[]> immutableShortMap(Map<Integer, short[]> source) {
        Map<Integer, short[]> copy = new TreeMap<>();
        source.forEach((key, value) -> copy.put(key, value.clone()));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static void writeStrings(DataOutputStream output, String[] values) throws IOException {
        output.writeInt(values.length);
        for (String value : values) output.writeUTF(value);
    }

    private static String[] readStrings(DataInputStream input) throws IOException {
        String[] values = new String[input.readInt()];
        for (int index = 0; index < values.length; index++) values[index] = input.readUTF();
        return values;
    }

    private static void writeStringList(DataOutputStream output, List<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) output.writeUTF(value);
    }

    private static List<String> readStringList(DataInputStream input) throws IOException {
        List<String> values = new ArrayList<>();
        int size = input.readInt();
        for (int index = 0; index < size; index++) values.add(input.readUTF());
        return List.copyOf(values);
    }

    private static void writeStringMap(DataOutputStream output, Map<String, String> values) throws IOException {
        output.writeInt(values.size());
        for (var entry : new TreeMap<>(values).entrySet()) {
            output.writeUTF(entry.getKey());
            output.writeUTF(entry.getValue());
        }
    }

    private static Map<String, String> readStringMap(DataInputStream input) throws IOException {
        Map<String, String> values = new TreeMap<>();
        int size = input.readInt();
        for (int index = 0; index < size; index++) values.put(input.readUTF(), input.readUTF());
        return immutableStringMap(values);
    }

    private static void writeLongMap(DataOutputStream output, Map<String, long[]> values) throws IOException {
        output.writeInt(values.size());
        for (var entry : new TreeMap<>(values).entrySet()) {
            output.writeUTF(entry.getKey());
            writeLongs(output, entry.getValue());
        }
    }

    private static Map<String, long[]> readLongMap(DataInputStream input) throws IOException {
        Map<String, long[]> values = new TreeMap<>();
        int size = input.readInt();
        for (int index = 0; index < size; index++) values.put(input.readUTF(), readLongs(input));
        return immutableLongMap(values);
    }

    private static void writeShortMap(DataOutputStream output, Map<Integer, short[]> values) throws IOException {
        output.writeInt(values.size());
        for (var entry : new TreeMap<>(values).entrySet()) {
            output.writeInt(entry.getKey());
            output.writeInt(entry.getValue().length);
            for (short value : entry.getValue()) output.writeShort(value);
        }
    }

    private static Map<Integer, short[]> readShortMap(DataInputStream input) throws IOException {
        Map<Integer, short[]> values = new TreeMap<>();
        int size = input.readInt();
        for (int index = 0; index < size; index++) {
            int key = input.readInt();
            short[] packed = new short[input.readInt()];
            for (int valueIndex = 0; valueIndex < packed.length; valueIndex++) packed[valueIndex] = input.readShort();
            values.put(key, packed);
        }
        return immutableShortMap(values);
    }

    private static void writeLongs(DataOutputStream output, long[] values) throws IOException {
        output.writeInt(values.length);
        for (long value : values) output.writeLong(value);
    }

    private static long[] readLongs(DataInputStream input) throws IOException {
        long[] values = new long[input.readInt()];
        for (int index = 0; index < values.length; index++) values[index] = input.readLong();
        return values;
    }

    private static void updateLongMap(MessageDigest digest, Map<String, long[]> values) {
        for (var entry : values.entrySet()) {
            update(digest, entry.getKey());
            for (long value : entry.getValue()) update(digest, value);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void update(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void update(MessageDigest digest, short value) {
        digest.update(ByteBuffer.allocate(Short.BYTES).putShort(value).array());
    }
}
