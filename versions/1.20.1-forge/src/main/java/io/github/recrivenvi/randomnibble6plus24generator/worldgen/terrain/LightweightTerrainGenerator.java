package io.github.recrivenvi.randomnibble6plus24generator.worldgen.terrain;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SeedLocalRegionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Exact native six-stage consumer, independent of the physical Chunk scheduler. */
public final class LightweightTerrainGenerator {
    private final StructureGenerationContext context;
    private final CompactStructureInputs structures;
    private final ProtoChunk target;
    private final Map<Long, ChunkAccess> storedBiomes = new HashMap<>();
    private final Map<Long, CarverSourceChunk> carverSources = new HashMap<>();
    private long storedReads;

    public LightweightTerrainGenerator(
            StructureGenerationContext context, CompactStructureInputs structures) {
        structures.requireCompatible(context);
        this.context = context;
        this.structures = structures;
        target =
                new ProtoChunk(
                        context.target(),
                        UpgradeData.EMPTY,
                        context.host(),
                        context.host().registryAccess().registryOrThrow(Registries.BIOME),
                        null);
    }

    public Result generate(BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        context.checkPreparing();
        long began = System.nanoTime();
        structures.copyStartsTo(target);
        complete(ChunkStatus.STRUCTURE_STARTS, checkpoint);
        structures.copyReferencesTo(target);
        complete(ChunkStatus.STRUCTURE_REFERENCES, checkpoint);
        context.generator()
                .createBiomes(
                        Util.backgroundExecutor(),
                        context.randomState(),
                        Blender.empty(),
                        structures.manager(),
                        target)
                .join();
        storedBiomes.put(target.getPos().toLong(), target);
        complete(ChunkStatus.BIOMES, checkpoint);
        int radius = 0;
        for (int distance = 1; distance <= ChunkStatus.SURFACE.getRange(); distance++) {
            var required =
                    ChunkStatus.getStatusAroundFullChunk(
                            ChunkStatus.getDistance(ChunkStatus.SURFACE) + distance);
            if (required.isOrAfter(ChunkStatus.BIOMES)) radius = distance;
        }
        if (radius != 1)
            throw new IllegalStateException("Unaudited surface palette range " + radius);
        for (int z = target.getPos().z - radius; z <= target.getPos().z + radius; z++)
            for (int x = target.getPos().x - radius; x <= target.getPos().x + radius; x++) {
                var pos = new ChunkPos(x, z);
                if (pos.equals(target.getPos())) continue;
                try (var neighbor =
                        new StructureGenerationContext(context.host(), context.seed(), pos)) {
                    // Native createBiomes constructs this owner's Beardifier and climate cache.
                    var input = CompactStructureInputs.resolve(neighbor);
                    var palette =
                            new BiomePaletteChunk(
                                    pos,
                                    context.host(),
                                    context.host()
                                            .registryAccess()
                                            .registryOrThrow(Registries.BIOME));
                    neighbor.generator()
                            .createBiomes(
                                    Util.backgroundExecutor(),
                                    neighbor.randomState(),
                                    Blender.empty(),
                                    input.manager(),
                                    palette)
                            .join();
                    storedBiomes.put(pos.toLong(), palette);
                }
            }
        BiomeManager manager =
                new BiomeManager(
                        (x, y, z) -> {
                            storedReads++;
                            return storedBiome(QuartPos.toSection(x), QuartPos.toSection(z))
                                    .getNoiseBiome(x, y, z);
                        },
                        BiomeManager.obfuscateSeed(context.seed()));
        context.generator()
                .fillFromNoise(
                        Util.backgroundExecutor(),
                        Blender.empty(),
                        context.randomState(),
                        structures.manager(),
                        target)
                .join();
        complete(ChunkStatus.NOISE, checkpoint);
        if (!SharedConstants.debugVoidTerrain(target.getPos())
                && !SharedConstants.DEBUG_DISABLE_SURFACE)
            context.generator()
                    .buildSurface(
                            target,
                            new WorldGenerationContext(context.generator(), context.host()),
                            context.randomState(),
                            structures.manager(),
                            manager,
                            context.host().registryAccess().registryOrThrow(Registries.BIOME),
                            Blender.empty());
        complete(ChunkStatus.SURFACE, checkpoint);
        var region =
                SeedLocalRegionScope.construct(context, structures.manager(), SourceRegion::new);
        if (region.getSeed() != context.seed())
            throw new IllegalStateException("Region seed escaped local context");
        Blender.addAroundOldChunksCarvingMaskFilter(region, target);
        var carverBiomes =
                new BiomeManager(
                        (x, y, z) ->
                                context.generator()
                                        .getBiomeSource()
                                        .getNoiseBiome(x, y, z, context.randomState().sampler()),
                        BiomeManager.obfuscateSeed(context.seed()));
        context.generator()
                .applyCarvers(
                        region,
                        context.seed(),
                        context.randomState(),
                        carverBiomes,
                        structures.manager(),
                        target,
                        GenerationStep.Carving.AIR);
        complete(ChunkStatus.CARVERS, checkpoint);
        return new Result(
                target,
                new Metrics(
                        1,
                        storedBiomes.size() - 1,
                        carverSources.size(),
                        0,
                        storedReads,
                        System.nanoTime() - began));
    }

    private void complete(ChunkStatus status, BiConsumer<ChunkStatus, ChunkAccess> checkpoint) {
        context.checkPreparing();
        target.setStatus(status);
        checkpoint.accept(status, target);
    }

    public ChunkAccess storedBiome(int x, int z) {
        var chunk = storedBiomes.get(ChunkPos.asLong(x, z));
        if (chunk == null)
            throw new IllegalArgumentException("Outside stored-biome frontier " + x + "," + z);
        return chunk;
    }

    public record Result(ProtoChunk target, Metrics metrics) {}

    public record Metrics(
            int protoChunks,
            int biomeCarriers,
            int carverSources,
            int generationHolders,
            long storedBiomeReads,
            long elapsedNanos) {}

    private final class SourceRegion extends WorldGenRegion {
        SourceRegion() {
            // The list supplies only native constructor identity. All neighborhood reads below are
            // explicit.
            super(context.host(), java.util.List.of(target), ChunkStatus.CARVERS, 0);
        }

        @Override
        public ChunkAccess getChunk(int x, int z) {
            int radius = ChunkStatus.CARVERS.getRange();
            if (radius != 8
                    || Math.abs((long) x - target.getPos().x) > radius
                    || Math.abs((long) z - target.getPos().z) > radius)
                throw new IllegalArgumentException("Outside native carver range");
            return carverSources.computeIfAbsent(
                    ChunkPos.asLong(x, z),
                    key -> new CarverSourceChunk(new ChunkPos(x, z), context.host()));
        }

        @Override
        public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean required) {
            if (status != ChunkStatus.EMPTY)
                throw new UnsupportedOperationException("No generated neighbor terrain: " + status);
            return getChunk(x, z);
        }

        @Override
        public boolean isOldChunkAround(ChunkPos pos, int radius) {
            return false;
        }

        @Override
        public ServerLevel getLevel() {
            throw new UnsupportedOperationException(
                    "Carvers may not escape to physical world state");
        }
    }
}
