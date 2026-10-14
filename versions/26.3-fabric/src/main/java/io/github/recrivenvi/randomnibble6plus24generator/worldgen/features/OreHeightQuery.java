package io.github.recrivenvi.randomnibble6plus24generator.worldgen.features;

import io.github.recrivenvi.randomnibble6plus24generator.mixin.NoiseBasedChunkGeneratorAccessor;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/** A certified rejection query, not an approximate replacement for OCEAN_FLOOR_WG. */
public final class OreHeightQuery implements AutoCloseable {
    private final IsolatedGenerationContext context;
    private final Map<Long, Beardifier> structures = new HashMap<>();
    private final Map<Long, ColumnBound> columns = new HashMap<>();
    private final Map<Long, NoiseHeights> noiseChunks = new HashMap<>();
    private long nanos;
    private int rejections;
    private final BiomeManager biomes;
    private final Map<Long, NoiseChunk> carvingQueries = new HashMap<>();

    /** Certifies the final TERRAIN cell, including potential native carving fluid replacement. */
    public boolean definitelyAir(BlockPos pos) {
        if (context.hostLevel().isOutsideBuildHeight(pos)) return false;
        // This also applies the audited density/material guards and fills the column cache.
        definitelyBelow(pos.getX(), pos.getZ(), pos.getY() + 1);
        var column = columns.get(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
        if (column == null || pos.getY() < Math.max(column.noiseHeight(), column.upperHeight()))
            return false;
        var settings = context.generator().generatorSettings().value();
        var range = settings.noiseSettings().clampToHeightAccessor(context.hostLevel());
        if (pos.getY() <= range.minY() || pos.getY() > range.minY() + range.height() - 1 - 7)
            return true;
        var p = ChunkPos.containing(pos);
        var noise =
                carvingQueries.computeIfAbsent(
                        p.pack(),
                        key ->
                                new NoiseChunk(
                                        context.randomState(),
                                        structures.get(key),
                                        settings,
                                        ((NoiseBasedChunkGeneratorAccessor)
                                                        (Object) context.generator())
                                                .randomnibble6plus24generator$globalFluidPicker()
                                                .get(),
                                        Blender.empty(),
                                        new DensityVolume(
                                                16,
                                                range.height(),
                                                16,
                                                p.getMinBlockX(),
                                                range.minY(),
                                                p.getMinBlockZ())));
        // 26.3 applyCarvingMask samples exactly density 0; null leaves the existing cell unchanged.
        // A fluid result is not silently called air, even above the noise/material envelope.
        var replacement = noise.aquifer().computeSubstance(pos.getX(), pos.getY(), pos.getZ(), 0.0);
        return replacement == null
                || replacement == net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    @Override
    public void close() {
        carvingQueries.values().forEach(NoiseChunk::close);
        carvingQueries.clear();
    }

    public OreHeightQuery(
            IsolatedGenerationContext context,
            java.util.function.Function<ChunkPos, net.minecraft.world.level.chunk.ChunkAccess>
                    palettes) {
        this.context = context;
        biomes =
                new BiomeManager(
                        (x, y, z) ->
                                palettes.apply(
                                                new ChunkPos(
                                                        QuartPos.toSection(x),
                                                        QuartPos.toSection(z)))
                                        .getNoiseBiome(x, y, z),
                        BiomeManager.obfuscateSeed(context.worldSeed()));
    }

    public record ColumnBound(
            int x,
            int z,
            int noiseHeight,
            int noiseOceanFloor,
            int upperHeight,
            String densityHash,
            int densitySamples) {}

    public record Metrics(
            int structureChunks,
            int noiseChunks,
            int computedColumns,
            long densitySamples,
            int columns,
            int certifiedRejections,
            long nanos,
            List<ColumnBound> evidence) {}

    private record NoiseHeights(int[] surface, int[] floor, String[] hashes, int depth) {}

    public Metrics metrics() {
        return new Metrics(
                structures.size(),
                noiseChunks.size(),
                noiseChunks.size() * 256,
                noiseChunks.values().stream().mapToLong(n -> (long) n.depth() * 256).sum(),
                columns.size(),
                rejections,
                nanos,
                columns.values().stream()
                        .sorted(
                                Comparator.comparingInt(ColumnBound::z)
                                        .thenComparingInt(ColumnBound::x))
                        .toList());
    }

    public boolean definitelyBelow(int x, int z, int threshold) {
        ChunkPos p = ChunkPos.containing(new BlockPos(x, 0, z));
        if (Math.abs((long) p.x() - context.target().x()) > 2
                || Math.abs((long) p.z() - context.target().z()) > 2)
            throw new IllegalArgumentException("Outside finite A height query " + p);
        var fluid = context.generator().generatorSettings().value().defaultFluid();
        var opaque = net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        if (SharedConstants.DEBUG_AQUIFERS
                || SharedConstants.debugVoidTerrain(p)
                || context.generator()
                                .generatorSettings()
                                .value()
                                .noiseSettings()
                                .clampToHeightAccessor(context.hostLevel())
                                .height()
                        <= 0
                || opaque.test(fluid)
                || (!fluid.isAir() && fluid.getFluidState().isEmpty())
                || opaque.test(net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState())
                || opaque.test(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                || opaque.test(net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState())
                || opaque.test(net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState()))
            return false;
        long started = System.nanoTime();
        try {
            long key = BlockPos.asLong(x, 0, z);
            ColumnBound column = columns.computeIfAbsent(key, k -> sample(x, z, p));
            if (column.upperHeight() < threshold) {
                rejections++;
                return true;
            }
            return false;
        } finally {
            nanos += System.nanoTime() - started;
        }
    }

    private ColumnBound sample(int x, int z, ChunkPos p) {
        NoiseHeights batch = noiseChunks.computeIfAbsent(p.pack(), k -> sampleChunk(p));
        int index = (x & 15) + (z & 15) * 16;
        int noiseHeight = batch.surface()[index], noiseOceanFloor = batch.floor()[index];
        Holder<Biome> surfaceBiome = biomes.getBiome(new BlockPos(x, noiseHeight, z));
        var settings = context.generator().generatorSettings().value();
        // Material rules visit existing non-air/non-fluid cells. With an opaque default block and
        // native liquids these are already below the noise ocean floor. Carving cannot raise that
        // ceiling; native badlands/iceberg extensions are accounted for separately.
        boolean solidBase =
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG
                        .isOpaque()
                        .test(settings.defaultBlock());
        int upper =
                Math.max(
                        solidBase ? noiseOceanFloor : noiseHeight,
                        extensionUpper(x, z, surfaceBiome));
        return new ColumnBound(
                x, z, noiseHeight, noiseOceanFloor, upper, batch.hashes()[index], batch.depth());
    }

    private NoiseHeights sampleChunk(ChunkPos p) {
        var settings = context.generator().generatorSettings().value();
        var range = settings.noiseSettings().clampToHeightAccessor(context.hostLevel());
        if (range.height() <= 0) throw new IllegalStateException("Unaudited empty A noise range");
        Beardifier beard =
                structures.computeIfAbsent(
                        p.pack(),
                        key -> {
                            try (var source =
                                    IsolatedGenerationContext.create(
                                            IsolatedGenerationMode.ISOLATED_MOSAIC,
                                            context.hostLevel(),
                                            context.worldSeed(),
                                            p)) {
                                return Beardifier.forStructuresInChunk(
                                        CompactStructureInputs.resolve(source).manager(), p);
                            }
                        });
        var volume =
                new DensityVolume(
                        16, range.height(), 16, p.getMinBlockX(), range.minY(), p.getMinBlockZ());
        var picker =
                ((NoiseBasedChunkGeneratorAccessor) (Object) context.generator())
                        .randomnibble6plus24generator$globalFluidPicker()
                        .get();
        int[] surface = new int[256], floor = new int[256];
        Arrays.fill(surface, context.hostLevel().getMinY());
        Arrays.fill(floor, context.hostLevel().getMinY());
        String[] hashes = new String[256];
        try (var noise =
                        new NoiseChunk(
                                context.randomState(),
                                beard,
                                settings,
                                picker,
                                Blender.empty(),
                                volume);
                var buffer =
                        noise.cachingSamplers()
                                .get(settings.noiseRouter().finalDensity())
                                .sampleVolume(volume)) {
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int index = x + z * 16, worldX = volume.blockX(x), worldZ = volume.blockZ(z);
                    hashes[index] = densityHash(volume, buffer, worldX, worldZ);
                    boolean surfaceFound = false;
                    for (int y = volume.sizeY() - 1; y >= 0; y--) {
                        int worldY = volume.blockY(y);
                        var state =
                                noise.aquifer()
                                        .computeSubstance(
                                                worldX,
                                                worldY,
                                                worldZ,
                                                buffer.get(volume.indexUnchecked(x, y, z)));
                        if (state == null) state = settings.defaultBlock();
                        if (!surfaceFound && !state.isAir()) {
                            surface[index] = worldY + 1;
                            surfaceFound = true;
                        }
                        if (net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG
                                .isOpaque()
                                .test(state)) {
                            floor[index] = worldY + 1;
                            break;
                        }
                    }
                }
        }
        return new NoiseHeights(surface, floor, hashes, volume.sizeY());
    }

    private int extensionUpper(int x, int z, Holder<Biome> biome) {
        var random = context.randomState();
        if (!biome.is(Biomes.ERODED_BADLANDS)
                && !biome.is(Biomes.FROZEN_OCEAN)
                && !biome.is(Biomes.DEEP_FROZEN_OCEAN)) return Integer.MIN_VALUE;
        double badlands =
                Math.min(
                        Math.abs(
                                (double)
                                                random.getOrCreateNoise(Noises.BADLANDS_SURFACE)
                                                        .get(x, 0.0, z)
                                        * 8.25),
                        (double)
                                (random.getOrCreateNoise(Noises.BADLANDS_PILLAR)
                                                .get(x * 0.2, 0.0, z * 0.2)
                                        * 15.0f));
        int upper = Integer.MIN_VALUE;
        if (biome.is(Biomes.ERODED_BADLANDS) && badlands > 0) {
            double roof =
                    Math.abs(
                            (double)
                                            random.getOrCreateNoise(Noises.BADLANDS_PILLAR_ROOF)
                                                    .get(x * 0.75, 0.0, z * 0.75)
                                    * 1.5);
            upper =
                    Mth.floor(
                                    64.0
                                            + Math.min(
                                                    badlands * badlands * 2.5,
                                                    Math.ceil(roof * 50.0) + 24.0))
                            + 1;
        }
        double iceberg =
                Math.min(
                        Math.abs(
                                (double)
                                                random.getOrCreateNoise(Noises.ICEBERG_SURFACE)
                                                        .get(x, 0.0, z)
                                        * 8.25),
                        (double)
                                (random.getOrCreateNoise(Noises.ICEBERG_PILLAR)
                                                .get(x * 1.28, 0.0, z * 1.28)
                                        * 15.0f));
        if ((biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN))
                && iceberg > 1.8) {
            double roof =
                    Math.abs(
                            (double)
                                            random.getOrCreateNoise(Noises.ICEBERG_PILLAR_ROOF)
                                                    .get(x * 1.17, 0.0, z * 1.17)
                                    * 1.5);
            double top = Math.min(iceberg * iceberg * 1.2, Math.ceil(roof * 40.0) + 14.0);
            // Melting only lowers the extension. The unlowered cap is safe for either outcome.
            upper = Math.max(upper, Mth.ceil(top + context.generator().getSeaLevel()) + 1);
        }
        return upper;
    }

    public static String densityHash(
            DensityVolume volume, ScopedDensityBuffer buffer, int x, int z) {
        int ix = x - volume.minBlockX(), iz = z - volume.minBlockZ();
        ByteBuffer bytes = ByteBuffer.allocate(volume.sizeY() * Integer.BYTES);
        for (int y = 0; y < volume.sizeY(); y++)
            bytes.putInt(Float.floatToRawIntBits(buffer.get(volume.indexUnchecked(ix, y, iz))));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
