package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.concurrent.CompletableFuture;
import java.util.BitSet;
import java.util.EnumSet;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.Heightmap;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureFrontierEvidence;

public final class IsolatedChunkStatusTasks {

    private IsolatedChunkStatusTasks() {
    }

    public static CompletableFuture<ChunkAccess> generateStructureStarts(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.STRUCTURE_STARTS);
        if (context.hostLevel().getServer().getWorldGenSettings().options().generateStructures()) {
            context.generator().createStructures(
                    context.hostLevel().registryAccess(),
                    context.structureState(),
                    context.structureManager(),
                    chunk,
                    context.worldGenContext().structureManager(),
                    context.dimension());
        }
        context.structureCheck().onStructureLoad(chunk.getPos(), chunk.getAllStarts());
        return CompletableFuture.completedFuture(chunk);
    }

    public static CompletableFuture<ChunkAccess> generateStructureReferences(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.STRUCTURE_REFERENCES);
        WorldGenRegion region = region(context, step, cache, chunk);
        context.generator().createReferences(
                region,
                context.structureManager().forWorldGenRegion(region),
                chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    public static CompletableFuture<ChunkAccess> generateBiomes(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.BIOMES);
        WorldGenRegion region = region(context, step, cache, chunk);
        StructureManager structureManager = context.structureManager().forWorldGenRegion(region);
        return context.generator().createBiomes(
                context.randomState(),
                Blender.of(region),
                structureManager,
                chunk);
    }

    public static CompletableFuture<ChunkAccess> generateNoise(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.NOISE);
        WorldGenRegion region = region(context, step, cache, chunk);
        return context.generator().fillFromNoise(
                Blender.of(region),
                context.randomState(),
                context.structureManager().forWorldGenRegion(region),
                chunk);
    }

    public static CompletableFuture<ChunkAccess> generateSurface(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.SURFACE);
        WorldGenRegion region = region(context, step, cache, chunk);
        context.generator().buildSurface(
                region,
                context.structureManager().forWorldGenRegion(region),
                context.randomState(),
                chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    public static CompletableFuture<ChunkAccess> generateCarvers(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.CARVERS);
        context.recordCarverStageSeed(context.worldSeed());
        WorldGenRegion region = region(context, step, cache, chunk);
        if (chunk instanceof ProtoChunk protoChunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(region, protoChunk);
        }

        boolean target = chunk.getPos().equals(context.target());
        BlockState[] before = target ? captureBlocks(chunk) : null;
        int configuredCarvers = target ? countConfiguredCarvers(context, region, chunk) : 0;
        context.generator().applyCarvers(
                region,
                context.worldSeed(),
                context.randomState(),
                context.biomeManager(),
                context.structureManager().forWorldGenRegion(region),
                chunk);
        if (target) {
            ProtoChunk protoChunk = (ProtoChunk) chunk;
            context.recordTargetCarverTrace(
                    17 * 17,
                    configuredCarvers,
                    countBlockChanges(before, chunk),
                    BitSet.valueOf(protoChunk.getOrCreateCarvingMask().toArray()).cardinality());
        }
        return CompletableFuture.completedFuture(chunk);
    }

    public static CompletableFuture<ChunkAccess> generateFeatures(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        FeatureFrontierEvidence.capture(
                FeatureFrontierEvidence.Mode.ISOLATED,
                context.worldGenContext(),
                cache,
                chunk,
                FeatureFrontierEvidence.Phase.PRE);
        context.recordStage(ChunkStatus.FEATURES);
        context.beginFeatureWriter(chunk.getPos());
        try {
            Heightmap.primeHeightmaps(
                    chunk,
                    EnumSet.of(
                            Heightmap.Types.MOTION_BLOCKING,
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Heightmap.Types.OCEAN_FLOOR,
                            Heightmap.Types.WORLD_SURFACE));
            WorldGenRegion region = region(context, step, cache, chunk);
            context.recordFeatureWorldSeed(region.getSeed());
            if (!SharedConstants.DEBUG_DISABLE_FEATURES) {
                context.generator().applyBiomeDecoration(
                        region,
                        chunk,
                        context.structureManager().forWorldGenRegion(region));
            }
            Blender.generateBorderTicks(region, chunk);
            FeatureFrontierEvidence.capture(
                    FeatureFrontierEvidence.Mode.ISOLATED,
                    context.worldGenContext(),
                    cache,
                    chunk,
                    FeatureFrontierEvidence.Phase.POST);
            return CompletableFuture.completedFuture(chunk);
        } finally {
            context.completeFeatureWriter(chunk.getPos());
        }
    }

    private static WorldGenRegion region(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        GenerationContextRegistry.bind(cache, context);
        WorldGenRegion region = new WorldGenRegion(context.hostLevel(), cache, step, chunk);
        GenerationContextRegistry.bind(region, context);
        return region;
    }

    private static int countConfiguredCarvers(
            IsolatedGenerationContext context,
            WorldGenRegion region,
            ChunkAccess target) {
        int count = 0;
        ChunkPos targetPos = target.getPos();
        for (int offsetX = -8; offsetX <= 8; offsetX++) {
            for (int offsetZ = -8; offsetZ <= 8; offsetZ++) {
                ChunkPos sourcePos = new ChunkPos(targetPos.x() + offsetX, targetPos.z() + offsetZ);
                ChunkAccess source = region.getChunk(sourcePos.x(), sourcePos.z());
                BiomeGenerationSettings settings = source.carverBiome(() ->
                        context.generator().getBiomeGenerationSettings(
                                context.generator().getBiomeSource().getNoiseBiome(
                                        QuartPos.fromBlock(sourcePos.getMinBlockX()),
                                        0,
                                        QuartPos.fromBlock(sourcePos.getMinBlockZ()),
                                        context.randomState().sampler())));
                for (var ignored : settings.getCarvers()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static BlockState[] captureBlocks(ChunkAccess chunk) {
        BlockState[] states = new BlockState[16 * 16 * chunk.getHeight()];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    states[index++] = chunk.getBlockState(cursor.set(
                            chunk.getPos().getMinBlockX() + x,
                            y,
                            chunk.getPos().getMinBlockZ() + z));
                }
            }
        }
        return states;
    }

    private static int countBlockChanges(BlockState[] before, ChunkAccess chunk) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int index = 0;
        int changed = 0;
        for (int y = chunk.getMinY(); y < chunk.getMinY() + chunk.getHeight(); y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState after = chunk.getBlockState(cursor.set(
                            chunk.getPos().getMinBlockX() + x,
                            y,
                            chunk.getPos().getMinBlockZ() + z));
                    if (!before[index].equals(after)) {
                        changed++;
                    }
                    index++;
                }
            }
        }
        return changed;
    }
}
