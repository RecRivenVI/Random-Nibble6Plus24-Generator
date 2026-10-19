package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact six-stage native input consumer; not registered as a production generator. */
public final class LightweightTerrainGenerator {
    private final IsolatedGenerationContext context;
    private final CompactStructureInputs structures;
    private final ScopedStructureSources sourceReuse;
    private final ProtoChunk target;
    private final Map<Long, ChunkAccess> biomes = new HashMap<>();
    private final Map<Long, CarverSourceChunk> carverSources = new HashMap<>();
    private long storedReads;

    public LightweightTerrainGenerator(
            IsolatedGenerationContext context,
            CompactStructureInputs structures,
            ScopedStructureSources sourceReuse) {
        structures.requireCompatible(context);
        this.context = context;
        this.structures = structures;
        this.sourceReuse = sourceReuse;
        var host = context.hostLevel();
        target =
                new ProtoChunk(
                        context.target(),
                        UpgradeData.EMPTY,
                        host,
                        host.palettedContainerFactory(),
                        null);
    }

    public Result generate(BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        long began = System.nanoTime();
        structures.copyStartsTo(target);
        complete(ChunkStatus.STRUCTURE_STARTS, checkpoint);
        structures.copyReferencesTo(target);
        complete(ChunkStatus.STRUCTURE_REFERENCES, checkpoint);
        long biomesBegan = System.nanoTime();
        context.generator()
                .createBiomes(context.randomState(), Blender.empty(), structures.manager(), target)
                .join();
        biomes.put(target.getPos().pack(), target);
        complete(ChunkStatus.BIOMES, checkpoint);
        int radius =
                ChunkPyramid.GENERATION_PYRAMID
                        .getStepTo(ChunkStatus.SURFACE)
                        .directDependencies()
                        .getRadiusOf(ChunkStatus.BIOMES);
        if (radius != 1)
            throw new IllegalStateException("Unaudited native surface biome radius " + radius);
        for (int z = target.getPos().z() - radius; z <= target.getPos().z() + radius; z++)
            for (int x = target.getPos().x() - radius; x <= target.getPos().x() + radius; x++) {
                var pos = new ChunkPos(x, z);
                if (pos.equals(target.getPos())) continue;
                // 26.1.2 creates a Beardifier even during BIOMES. Resolve this owner's exact
                // references, rather than borrowing target references or physical host structures.
                try (var inputContext =
                        IsolatedGenerationContext.create(
                                IsolatedGenerationMode.ISOLATED_MOSAIC,
                                context.hostLevel(),
                                context.worldSeed(),
                                pos)) {
                    var inputs = CompactStructureInputs.resolve(inputContext, sourceReuse);
                    var palette =
                            new BiomePaletteChunk(
                                    pos,
                                    context.hostLevel(),
                                    context.hostLevel().palettedContainerFactory());
                    inputContext
                            .generator()
                            .createBiomes(
                                    inputContext.randomState(),
                                    Blender.empty(),
                                    inputs.manager(),
                                    palette)
                            .join();
                    biomes.put(pos.pack(), palette);
                }
            }
        long biomeNanos = System.nanoTime() - biomesBegan;
        var manager =
                new BiomeManager(
                        (x, y, z) -> {
                            var source = storedBiome(QuartPos.toSection(x), QuartPos.toSection(z));
                            storedReads++;
                            return source.getNoiseBiome(x, y, z);
                        },
                        BiomeManager.obfuscateSeed(context.worldSeed()));
        long noiseBegan = System.nanoTime();
        context.generator()
                .fillFromNoise(Blender.empty(), context.randomState(), structures.manager(), target)
                .join();
        complete(ChunkStatus.NOISE, checkpoint);
        long noiseNanos = System.nanoTime() - noiseBegan;
        long surfaceBegan = System.nanoTime();
        if (!SharedConstants.debugVoidTerrain(target.getPos())
                && !SharedConstants.DEBUG_DISABLE_SURFACE)
            context.generator()
                    .buildSurface(
                            target,
                            new WorldGenerationContext(context.generator(), context.hostLevel()),
                            context.randomState(),
                            structures.manager(),
                            manager,
                            context.hostLevel().registryAccess().lookupOrThrow(Registries.BIOME),
                            Blender.empty());
        complete(ChunkStatus.SURFACE, checkpoint);
        long surfaceNanos = System.nanoTime() - surfaceBegan;
        long carversBegan = System.nanoTime();
        // This identity-only bridge has no holders and is never used as a chunk provider.
        // All actual source requests dispatch to SourceRegion's native supplier-only carriers.
        StaticCache2D<GenerationChunkHolder> bridge =
                StaticCache2D.create(target.getPos().x(), target.getPos().z(), 0, (x, z) -> null);
        GenerationContextRegistry.bind(bridge, context);
        var region = new SourceRegion(bridge);
        GenerationContextRegistry.bind(region, context);
        Blender.addAroundOldChunksCarvingMaskFilter(region, target);
        context.generator()
                .applyCarvers(
                        region,
                        context.worldSeed(),
                        context.randomState(),
                        context.biomeManager(),
                        structures.manager(),
                        target);
        complete(ChunkStatus.CARVERS, checkpoint);
        return new Result(
                target,
                new Metrics(
                        1,
                        biomes.size() - 1,
                        carverSources.size(),
                        0,
                        storedReads,
                        biomeNanos,
                        noiseNanos,
                        surfaceNanos,
                        System.nanoTime() - carversBegan,
                        System.nanoTime() - began));
    }

    private void complete(ChunkStatus status, BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        target.setPersistedStatus(status);
        checkpoint.accept(status, target);
    }

    public ChunkAccess storedBiome(int x, int z) {
        var source = biomes.get(ChunkPos.pack(x, z));
        if (source == null)
            throw new IllegalStateException(
                    "Stored palette outside evaluated frontier " + x + "," + z);
        return source;
    }

    public record Result(ProtoChunk target, Metrics metrics) {}

    public record Metrics(
            int protoChunks,
            int biomeCarriers,
            int carverSources,
            int generationHolders,
            long storedBiomeReads,
            long biomeNanos,
            long noiseNanos,
            long surfaceNanos,
            long carverNanos,
            long elapsedNanos) {}

    private final class SourceRegion extends WorldGenRegion {
        SourceRegion(StaticCache2D<GenerationChunkHolder> bridge) {
            super(
                    context.hostLevel(),
                    bridge,
                    ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.CARVERS),
                    target);
        }

        @Override
        public ChunkAccess getChunk(int x, int z) {
            int radius =
                    ChunkPyramid.GENERATION_PYRAMID
                            .getStepTo(ChunkStatus.CARVERS)
                            .directDependencies()
                            .getRadiusOf(ChunkStatus.STRUCTURE_STARTS);
            if (radius != 8
                    || Math.abs((long) x - target.getPos().x()) > radius
                    || Math.abs((long) z - target.getPos().z()) > radius)
                throw new IllegalArgumentException("Unaudited carver source " + x + "," + z);
            return carverSources.computeIfAbsent(
                    ChunkPos.pack(x, z),
                    key -> new CarverSourceChunk(new ChunkPos(x, z), context.hostLevel()));
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
            if (status != ChunkStatus.EMPTY)
                throw new UnsupportedOperationException(
                        "Carver sources do not provide generated chunks: " + status);
            return getChunk(x, z);
        }

        @Override
        public boolean isOldChunkAround(ChunkPos pos, int radius) {
            return false;
        }
    }
}
