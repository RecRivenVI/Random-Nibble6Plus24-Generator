package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;

/** Detached stored palettes only: no Chunk, NoiseChunk, RNG, server or session references. */
public final class SpawnBiomeSnapshot {
    private final ResourceKey<Level> dimension;
    private final ChunkPos target;
    private final long localSeed;
    private final MosaicWorldProfile profile;
    private final Map<ChunkPos, Palette> palettes;

    private SpawnBiomeSnapshot(ResourceKey<Level> dimension, ChunkPos target, long localSeed,
            MosaicWorldProfile profile, Map<ChunkPos, Palette> palettes) {
        this.dimension = Objects.requireNonNull(dimension);
        this.target = Objects.requireNonNull(target);
        this.localSeed = localSeed;
        this.profile = Objects.requireNonNull(profile);
        this.palettes = Map.copyOf(palettes);
    }

    public static SpawnBiomeSnapshot capture(ResourceKey<Level> dimension, ChunkPos target, long localSeed,
            MosaicWorldProfile profile, Function<ChunkPos, ChunkAccess> chunks) {
        Map<ChunkPos, Palette> copies = new LinkedHashMap<>();
        for (ChunkPos pos : neighbors(target)) {
            ChunkAccess chunk = Objects.requireNonNull(chunks.apply(pos), "Missing stored SPAWN biome chunk " + pos);
            if (!chunk.getPos().equals(pos) || chunk.getPersistedStatus().isBefore(ChunkStatus.BIOMES)) {
                throw new IllegalStateException("Unready/misplaced stored SPAWN biomes: " + pos);
            }
            int minQuartY = QuartPos.fromBlock(chunk.getMinY());
            int quartHeight = QuartPos.fromBlock(chunk.getHeight());
            List<Holder<Biome>> cells = new ArrayList<>(quartHeight * 16);
            for (int y = minQuartY; y < minQuartY + quartHeight; y++) {
                for (int z = 0; z < 4; z++) for (int x = 0; x < 4; x++) {
                    cells.add(chunk.getNoiseBiome(pos.x() * 4 + x, y, pos.z() * 4 + z));
                }
            }
            copies.put(pos, new Palette(chunk.getMinY(), chunk.getHeight(), List.copyOf(cells)));
        }
        return new SpawnBiomeSnapshot(dimension, target, localSeed, profile, copies);
    }

    public void validate(ResourceKey<Level> expectedDimension, ChunkPos expectedTarget,
            long expectedSeed, MosaicWorldProfile expectedProfile) {
        if (!dimension.equals(expectedDimension) || !target.equals(expectedTarget)
                || localSeed != expectedSeed || !profile.equals(expectedProfile)) {
            throw new IllegalStateException("SPAWN biome provenance mismatch; refusing cross-universe reuse at " + expectedTarget);
        }
    }

    public Set<ChunkPos> positions() { return palettes.keySet(); }
    public int minY(ChunkPos pos) { return palette(pos).minY(); }
    public int height(ChunkPos pos) { return palette(pos).height(); }
    public int cellCount() { return palettes.values().stream().mapToInt(p -> p.cells().size()).sum(); }

    public Holder<Biome> biome(ChunkPos pos, int quartX, int quartY, int quartZ) {
        Palette palette = palette(pos);
        int min = QuartPos.fromBlock(palette.minY());
        int y = Math.clamp(quartY, min, min + QuartPos.fromBlock(palette.height()) - 1) - min;
        return palette.cells().get((y * 4 + (quartZ & 3)) * 4 + (quartX & 3));
    }

    private Palette palette(ChunkPos pos) {
        return Objects.requireNonNull(palettes.get(pos), "Outside same-seed SPAWN biome snapshot: " + pos);
    }

    public static List<ChunkPos> neighbors(ChunkPos target) {
        List<ChunkPos> result = new ArrayList<>(8);
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) {
            if (x != 0 || z != 0) result.add(new ChunkPos(target.x() + x, target.z() + z));
        }
        return List.copyOf(result);
    }

    private record Palette(int minY, int height, List<Holder<Biome>> cells) { }
}
