package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Read-only observations of actual native intermediate buffers; no second algorithm or sampling.
 */
public final class TerrainProbeEvidence {
    private static volatile State active;
    private static final ThreadLocal<Boolean> carving = new ThreadLocal<>();

    private TerrainProbeEvidence() {}

    public static void begin(
            Path output, ChunkPos target, long seed, net.minecraft.server.level.ServerLevel level) {
        active = new State(output, target, seed, level);
    }

    public static boolean target(ChunkAccess chunk) {
        return active != null && active.target.equals(chunk.getPos());
    }

    public static void close() {
        active = null;
        carving.remove();
    }

    public static void holderCreated() {
        if (active != null) active.holders.incrementAndGet();
    }

    public static int virtualHolders() {
        return active == null ? 0 : active.holders.get();
    }

    public static void enterCarvers(ChunkAccess chunk, RandomState random) {
        boolean selected = target(chunk);
        carving.set(selected);
        if (selected && random.seed() != active.seed)
            throw new IllegalStateException("Carver seed mismatch");
    }

    public static void leaveCarvers() {
        carving.remove();
    }

    public static void mask(CarvingMask mask) {
        if (Boolean.TRUE.equals(carving.get())) active.mask = mask;
    }

    public static void source(ChunkPos pos, Holder<Biome> biome) {
        if (!Boolean.TRUE.equals(carving.get())) return;
        JsonObject source = new JsonObject();
        source.addProperty("x", pos.x());
        source.addProperty("z", pos.z());
        source.addProperty("biome", biome.unwrapKey().orElseThrow().identifier().toString());
        active.sources.add(source);
    }

    public static void biomes(ChunkAccess chunk) {
        State state = active;
        if (state == null
                || Math.abs(chunk.getPos().x() - state.target.x()) > 1
                || Math.abs(chunk.getPos().z() - state.target.z()) > 1) return;
        JsonArray values = new JsonArray();
        for (int y = QuartPos.fromBlock(chunk.getMinY());
                y < QuartPos.fromBlock(chunk.getMinY() + chunk.getHeight());
                y++)
            for (int z = 0; z < 4; z++)
                for (int x = 0; x < 4; x++)
                    // createBiomes future completes before ChunkStep publishes BIOMES status. Read
                    // the filled native containers here without pretending the status already
                    // changed.
                    values.add(
                            (chunk
                                                    instanceof
                                                    io.github.recrivenvi
                                                            .randomnibble6plus24generator.worldgen
                                                            .terrain.BiomePaletteChunk
                                            ? chunk.getNoiseBiome(
                                                    QuartPos.fromBlock(
                                                                    chunk.getPos().getMinBlockX())
                                                            + x,
                                                    y,
                                                    QuartPos.fromBlock(
                                                                    chunk.getPos().getMinBlockZ())
                                                            + z)
                                            : chunk.getSection(
                                                            chunk.getSectionIndex(
                                                                    QuartPos.toBlock(y)))
                                                    .getNoiseBiome(x, y & 3, z))
                                    .unwrapKey()
                                    .orElseThrow()
                                    .identifier()
                                    .toString());
        synchronized (state) {
            if (state.biomes.putIfAbsent(chunk.getPos().toString(), values) != null)
                throw new IllegalStateException("Duplicate biome frontier");
        }
    }

    public static void density(
            ChunkAccess chunk, DensityVolume volume, ScopedDensityBuffer buffer) {
        if (!target(chunk)) return;
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            for (int z = 0; z < volume.sizeZ(); z++)
                for (int x = 0; x < volume.sizeX(); x++)
                    for (int y = 0; y < volume.sizeY(); y++)
                        output.writeInt(
                                Float.floatToRawIntBits(
                                        buffer.get(volume.indexUnchecked(x, y, z))));
            Files.createDirectories(active.output);
            Files.write(active.output.resolve("density.bin"), bytes.toByteArray());
            active.densitySamples = volume.size();
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    public static void snapshot(String name, ChunkAccess chunk) {
        if (!target(chunk)) return;
        FeatureStageSnapshot.capture(active.dimension, chunk, active.registry)
                .write(active.output.resolve(name + ".bin.gz"));
    }

    public static void finish() {
        State state = active;
        if (state == null) return;
        if (state.biomes.size() != 9
                || state.densitySamples <= 0
                || state.sources.size() != 289
                || state.mask == null)
            throw new IllegalStateException(
                    "Incomplete terrain internals: palettes="
                            + state.biomes.size()
                            + " sources="
                            + state.sources.size());
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            AtomicInteger carved = new AtomicInteger();
            state.mask.visit(
                    (x, z, bottom, top) -> {
                        try {
                            output.writeInt(x);
                            output.writeInt(z);
                            output.writeInt(bottom);
                            output.writeInt(top);
                        } catch (IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                        carved.addAndGet(top - bottom + 1);
                    });
            Files.write(state.output.resolve("carving-mask.bin"), bytes.toByteArray());
            JsonObject biomeData = new JsonObject();
            state.biomes.forEach(biomeData::add);
            PreFeaturesVerifier.write(state.output.resolve("biome-frontier.json"), biomeData);
            PreFeaturesVerifier.write(state.output.resolve("carver-sources.json"), state.sources);
            JsonObject summary = new JsonObject();
            summary.addProperty("densitySamples", state.densitySamples);
            summary.addProperty("carverSourceQueries", state.sources.size());
            summary.addProperty("carvingMaskBlocks", carved.get());
            summary.addProperty("biomePalettes", state.biomes.size());
            var noise = FeatureStageSnapshot.read(state.output.resolve("noise.bin.gz"));
            var surface = FeatureStageSnapshot.read(state.output.resolve("surface.bin.gz"));
            var carvedSnapshot = FeatureStageSnapshot.read(state.output.resolve("carved.bin.gz"));
            summary.addProperty("surfaceChangedBlocks", noise.diff(surface).differingBlocks());
            summary.addProperty(
                    "carverChangedBlocks", surface.diff(carvedSnapshot).differingBlocks());
            PreFeaturesVerifier.write(state.output.resolve("internals.json"), summary);
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    public static void compare(Path left, Path right, JsonArray differences) {
        try {
            for (String name :
                    List.of(
                            "density.bin",
                            "carving-mask.bin",
                            "biome-frontier.json",
                            "carver-sources.json",
                            "internals.json")) {
                int index =
                        Arrays.mismatch(
                                Files.readAllBytes(left.resolve(name)),
                                Files.readAllBytes(right.resolve(name)));
                if (index != -1) {
                    JsonObject diff = new JsonObject();
                    diff.addProperty("stage", name);
                    diff.addProperty("firstByte", index);
                    differences.add(diff);
                }
            }
            for (String stage : List.of("noise", "surface", "carved")) {
                var diff =
                        FeatureStageSnapshot.read(left.resolve(stage + ".bin.gz"))
                                .diff(FeatureStageSnapshot.read(right.resolve(stage + ".bin.gz")));
                if (!diff.equivalent()) {
                    JsonObject value = new JsonObject();
                    value.addProperty("stage", stage);
                    value.addProperty("firstDifference", diff.firstDifference());
                    value.addProperty("differingBlocks", diff.differingBlocks());
                    differences.add(value);
                }
            }
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    private static final class State {
        final Path output;
        final ChunkPos target;
        final long seed;
        final String dimension;
        final net.minecraft.core.RegistryAccess registry;
        final AtomicInteger holders = new AtomicInteger();
        final TreeMap<String, JsonArray> biomes = new TreeMap<>();
        final JsonArray sources = new JsonArray();
        CarvingMask mask;
        int densitySamples;

        State(
                Path output,
                ChunkPos target,
                long seed,
                net.minecraft.server.level.ServerLevel level) {
            this.output = output;
            this.target = target;
            this.seed = seed;
            dimension = level.dimension().identifier().toString();
            registry = level.registryAccess();
        }
    }
}
