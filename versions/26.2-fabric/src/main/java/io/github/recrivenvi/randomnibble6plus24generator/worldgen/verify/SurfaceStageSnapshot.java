package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class SurfaceStageSnapshot {

    public record StructureStartState(
            boolean valid,
            ChunkPos startChunk,
            int references,
            int pieceCount,
            BoundingBox boundingBox) {
    }

    private final ChunkPos chunkPos;
    private final int minY;
    private final int height;
    private final ChunkStatus status;
    private final BlockState[] blocks;
    private final String[] biomes;
    private final Map<String, long[]> heightmaps;
    private final Map<String, StructureStartState> structureStarts;
    private final Map<String, long[]> structureReferences;
    private final String hash;

    private SurfaceStageSnapshot(
            ChunkPos chunkPos,
            int minY,
            int height,
            ChunkStatus status,
            BlockState[] blocks,
            String[] biomes,
            Map<String, long[]> heightmaps,
            Map<String, StructureStartState> structureStarts,
            Map<String, long[]> structureReferences) {
        this.chunkPos = chunkPos;
        this.minY = minY;
        this.height = height;
        this.status = status;
        this.blocks = blocks;
        this.biomes = biomes;
        this.heightmaps = heightmaps;
        this.structureStarts = structureStarts;
        this.structureReferences = structureReferences;
        this.hash = calculateHash();
    }

    public static SurfaceStageSnapshot capture(ChunkAccess chunk, RegistryAccess registryAccess) {
        ChunkPos chunkPos = chunk.getPos();
        int minY = chunk.getMinY();
        int height = chunk.getHeight();
        BlockState[] blocks = new BlockState[16 * 16 * height];
        int blockIndex = 0;
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = minY; y < minY + height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    blocks[blockIndex++] = chunk.getBlockState(cursor.set(minBlockX + x, y, minBlockZ + z));
                }
            }
        }

        int minQuartX = QuartPos.fromBlock(minBlockX);
        int minQuartY = QuartPos.fromBlock(minY);
        int minQuartZ = QuartPos.fromBlock(minBlockZ);
        int quartHeight = height / QuartPos.SIZE;
        String[] biomes = new String[4 * 4 * quartHeight];
        int biomeIndex = 0;
        for (int quartY = 0; quartY < quartHeight; quartY++) {
            for (int quartZ = 0; quartZ < 4; quartZ++) {
                for (int quartX = 0; quartX < 4; quartX++) {
                    Holder<Biome> biome = chunk.getNoiseBiome(
                            minQuartX + quartX,
                            minQuartY + quartY,
                            minQuartZ + quartZ);
                    biomes[biomeIndex++] = biome.unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElseGet(() -> biome.value().toString());
                }
            }
        }

        Map<String, long[]> heightmaps = new TreeMap<>();
        for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            heightmaps.put(entry.getKey().getSerializationKey(), entry.getValue().getRawData().clone());
        }

        Registry<Structure> structures = registryAccess.lookupOrThrow(Registries.STRUCTURE);
        Map<String, StructureStartState> starts = new TreeMap<>();
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            Identifier id = structures.getKey(entry.getKey());
            StructureStart start = entry.getValue();
            starts.put(
                    Objects.toString(id),
                    new StructureStartState(
                            start.isValid(),
                            start.getChunkPos(),
                            start.getReferences(),
                            start.getPieces().size(),
                            start.isValid() ? start.getBoundingBox() : null));
        }

        Map<String, long[]> references = new TreeMap<>();
        for (var entry : chunk.getAllReferences().entrySet()) {
            long[] packedPositions = entry.getValue().toLongArray();
            Arrays.sort(packedPositions);
            references.put(Objects.toString(structures.getKey(entry.getKey())), packedPositions);
        }

        return new SurfaceStageSnapshot(
                chunkPos,
                minY,
                height,
                chunk.getPersistedStatus(),
                blocks,
                biomes,
                immutableSortedCopy(heightmaps),
                immutableSortedCopy(starts),
                immutableSortedCopy(references));
    }

    public void assertEquivalentTo(SurfaceStageSnapshot expected) {
        if (!chunkPos.equals(expected.chunkPos)) {
            mismatch("ChunkPos", expected.chunkPos, chunkPos);
        }
        if (status != expected.status) {
            mismatch("Chunk status", expected.status, status);
        }
        if (minY != expected.minY || height != expected.height) {
            mismatch("Height range", expected.minY + "+" + expected.height, minY + "+" + height);
        }
        for (int index = 0; index < blocks.length; index++) {
            if (!Objects.equals(blocks[index], expected.blocks[index])) {
                int y = minY + index / 256;
                int inLayer = index % 256;
                int z = inLayer / 16;
                int x = inLayer % 16;
                mismatch(
                        "BlockState at " + (chunkPos.getMinBlockX() + x) + "," + y + "," + (chunkPos.getMinBlockZ() + z),
                        expected.blocks[index],
                        blocks[index]);
            }
        }
        for (int index = 0; index < biomes.length; index++) {
            if (!Objects.equals(biomes[index], expected.biomes[index])) {
                mismatch("Biome cell " + index, expected.biomes[index], biomes[index]);
            }
        }
        assertLongArrayMapsEqual("Heightmap", expected.heightmaps, heightmaps);
        if (!structureStarts.equals(expected.structureStarts)) {
            mismatch("Structure starts", expected.structureStarts, structureStarts);
        }
        assertLongArrayMapsEqual("Structure references", expected.structureReferences, structureReferences);
        if (!hash.equals(expected.hash)) {
            mismatch("Surface hash", expected.hash, hash);
        }
    }

    public String hash() {
        return hash;
    }

    public ChunkPos chunkPos() {
        return chunkPos;
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public int countBlockDifferences(SurfaceStageSnapshot other) {
        if (!chunkPos.equals(other.chunkPos) || minY != other.minY || height != other.height) {
            throw new IllegalArgumentException("Snapshots cover different chunk volumes");
        }
        int changed = 0;
        for (int index = 0; index < blocks.length; index++) {
            if (!Objects.equals(blocks[index], other.blocks[index])) {
                changed++;
            }
        }
        return changed;
    }

    public ChunkStatus status() {
        return status;
    }

    public Map<String, long[]> heightmaps() {
        return heightmaps;
    }

    public Map<String, StructureStartState> structureStarts() {
        return structureStarts;
    }

    public Map<String, long[]> structureReferences() {
        return structureReferences;
    }

    private void assertLongArrayMapsEqual(
            String label,
            Map<String, long[]> expected,
            Map<String, long[]> actual) {
        if (!expected.keySet().equals(actual.keySet())) {
            mismatch(label + " keys", expected.keySet(), actual.keySet());
        }
        for (String key : expected.keySet()) {
            if (!Arrays.equals(expected.get(key), actual.get(key))) {
                mismatch(label + " " + key, Arrays.toString(expected.get(key)), Arrays.toString(actual.get(key)));
            }
        }
    }

    private String calculateHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, chunkPos.toString());
            update(digest, status.toString());
            update(digest, minY);
            update(digest, height);
            for (BlockState block : blocks) {
                update(digest, block.toString());
            }
            for (String biome : biomes) {
                update(digest, biome);
            }
            for (var entry : heightmaps.entrySet()) {
                update(digest, entry.getKey());
                for (long value : entry.getValue()) {
                    update(digest, value);
                }
            }
            for (var entry : structureStarts.entrySet()) {
                update(digest, entry.getKey());
                update(digest, entry.getValue().toString());
            }
            for (var entry : structureReferences.entrySet()) {
                update(digest, entry.getKey());
                for (long value : entry.getValue()) {
                    update(digest, value);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
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

    private static <T> Map<String, T> immutableSortedCopy(Map<String, T> values) {
        return Collections.unmodifiableMap(new TreeMap<>(values));
    }

    private static void mismatch(String field, Object expected, Object actual) {
        throw new SurfaceParityMismatchException(
                field + " mismatch; expected=" + expected + ", actual=" + actual);
    }
}
