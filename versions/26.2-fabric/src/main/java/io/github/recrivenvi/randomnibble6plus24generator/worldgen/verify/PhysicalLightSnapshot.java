package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Exact physical derived-light state; it is deliberately not a local-world canonical golden. */
public final class PhysicalLightSnapshot {

    private final String dimension;
    private final ChunkPos chunkPos;
    private final String status;
    private final boolean lightCorrect;
    private final String preLightFeatureHash;
    private final Map<Integer, String> blockLight;
    private final Map<Integer, String> skyLight;
    private final int[] skyLightSources;
    private final String hash;
    private final String lightDataHash;

    private PhysicalLightSnapshot(
            String dimension,
            ChunkPos chunkPos,
            String status,
            boolean lightCorrect,
            String preLightFeatureHash,
            Map<Integer, String> blockLight,
            Map<Integer, String> skyLight,
            int[] skyLightSources) {
        this.dimension = dimension;
        this.chunkPos = chunkPos;
        this.status = status;
        this.lightCorrect = lightCorrect;
        this.preLightFeatureHash = preLightFeatureHash;
        this.blockLight = java.util.Collections.unmodifiableMap(new TreeMap<>(blockLight));
        this.skyLight = java.util.Collections.unmodifiableMap(new TreeMap<>(skyLight));
        this.skyLightSources = skyLightSources.clone();
        this.hash = calculateHash(true);
        this.lightDataHash = calculateHash(false);
    }

    public static PhysicalLightSnapshot capture(
            ServerLevel level,
            ChunkAccess chunk,
            String preLightFeatureHash) {
        LevelLightEngine engine = level.getChunkSource().getLightEngine();
        Map<Integer, String> block = new TreeMap<>();
        Map<Integer, String> sky = new TreeMap<>();
        int minSection = engine.getMinLightSection();
        int maxSection = engine.getMaxLightSection();
        for (int sectionY = minSection; sectionY < maxSection; sectionY++) {
            SectionPos sectionPos = SectionPos.of(chunk.getPos(), sectionY);
            block.put(sectionY, layer(engine, LightLayer.BLOCK, sectionPos));
            sky.put(sectionY, layer(engine, LightLayer.SKY, sectionPos));
        }
        ChunkSkyLightSources sources = chunk.getSkyLightSources();
        int[] sourceHeights = new int[16 * 16];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                sourceHeights[z * 16 + x] = sources.getLowestSourceY(x, z);
            }
        }
        return new PhysicalLightSnapshot(
                level.dimension().identifier().toString(),
                chunk.getPos(),
                chunk.getPersistedStatus().getName(),
                chunk.isLightCorrect(),
                preLightFeatureHash,
                block,
                sky,
                sourceHeights);
    }

    private static String layer(LevelLightEngine engine, LightLayer layer, SectionPos sectionPos) {
        DataLayer data = engine.getLayerListener(layer).getDataLayerData(sectionPos);
        return data == null ? "<null>" : HexFormat.of().formatHex(data.getData());
    }

    public String hash() {
        return hash;
    }

    public String lightDataHash() {
        return lightDataHash;
    }

    public String status() {
        return status;
    }

    public boolean lightCorrect() {
        return lightCorrect;
    }

    public String preLightFeatureHash() {
        return preLightFeatureHash;
    }

    public Map<Integer, String> blockLight() {
        return blockLight;
    }

    public Map<Integer, String> skyLight() {
        return skyLight;
    }

    public int[] skyLightSourcesCopy() {
        return skyLightSources.clone();
    }

    public String blockLightHash() {
        return layerMapHash(blockLight);
    }

    public String skyLightHash() {
        return layerMapHash(skyLight);
    }

    public String skyLightSourcesHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int value : skyLightSources) update(digest, value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public long nonNullBlockLightSections() {
        return blockLight.values().stream().filter(value -> !value.equals("<null>")).count();
    }

    public long nonNullSkyLightSections() {
        return skyLight.values().stream().filter(value -> !value.equals("<null>")).count();
    }

    public Map<Integer, String> blockLightLayerHashes() {
        return layerHashes(blockLight);
    }

    public Map<Integer, String> skyLightLayerHashes() {
        return layerHashes(skyLight);
    }

    public int blockLightValue(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getChunkSource().getLightEngine().getLayerListener(LightLayer.BLOCK).getLightValue(pos);
    }

    public int skyLightValue(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return level.getChunkSource().getLightEngine().getLayerListener(LightLayer.SKY).getLightValue(pos);
    }

    private String calculateHash(boolean includePreLightFeatureHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, dimension);
            update(digest, chunkPos.x());
            update(digest, chunkPos.z());
            update(digest, status);
            digest.update((byte) (lightCorrect ? 1 : 0));
            if (includePreLightFeatureHash) update(digest, preLightFeatureHash);
            blockLight.forEach((section, data) -> {
                update(digest, section);
                update(digest, data);
            });
            skyLight.forEach((section, data) -> {
                update(digest, section);
                update(digest, data);
            });
            for (int value : skyLightSources) update(digest, value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String layerMapHash(Map<Integer, String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            values.forEach((section, data) -> {
                update(digest, section);
                update(digest, data);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<Integer, String> layerHashes(Map<Integer, String> values) {
        Map<Integer, String> result = new TreeMap<>();
        values.forEach((section, data) -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                update(digest, data);
                result.put(section, HexFormat.of().formatHex(digest.digest()));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void update(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
