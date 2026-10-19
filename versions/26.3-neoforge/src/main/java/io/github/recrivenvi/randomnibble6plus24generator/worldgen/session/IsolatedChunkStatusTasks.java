package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify.FeatureFrontierEvidence;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

public final class IsolatedChunkStatusTasks {

    private IsolatedChunkStatusTasks() {}

    public static CompletableFuture<ChunkAccess> generateStructureStarts(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.STRUCTURE_STARTS);
        if (context.hostLevel().getServer().getWorldGenSettings().options().generateStructures()) {
            // ChunkGeneratorStructureState creates concentric-ring positions on the
            // background executor.  Resolve those futures before taking the shared
            // StructureTemplateManager monitor; otherwise a spawn burst can fill the
            // common pool with workers waiting on the monitor while the monitor owner
            // waits for one of the same-pool ring futures (a real production deadlock).
            for (var structureSet : context.structureState().possibleStructureSets()) {
                if (structureSet.value().placement()
                        instanceof ConcentricRingsStructurePlacement rings) {
                    context.structureState().getRingPositionsFor(rings);
                }
            }
            // StructureTemplate.Palette lazily populates ordinary HashMap caches. Separate
            // isolated universes may run concurrently, but they share the server's Vanilla
            // StructureTemplateManager; serialize only this cache-populating entry point.
            synchronized (context.hostLevel().getServer().getStructureTemplateManager()) {
                context.generator()
                        .createStructures(
                                context.hostLevel().registryAccess(),
                                context.structureState(),
                                context.structureManager(),
                                chunk,
                                context.worldGenContext().structureManager(),
                                context.dimension());
            }
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
        context.generator()
                .createReferences(
                        region, context.structureManager().forWorldGenRegion(region), chunk);
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
        return context.generator()
                .createBiomes(context.randomState(), Blender.of(region), structureManager, chunk);
    }

    public static CompletableFuture<ChunkAccess> buildTerrain(
            IsolatedGenerationContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        context.recordStage(ChunkStatus.TERRAIN);
        context.recordCarverStageSeed(context.randomState().seed());
        WorldGenRegion region = region(context, step, cache, chunk);
        java.util.Set<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> biomes =
                new java.util.HashSet<>();
        ChunkPos center = chunk.getPos();
        for (int z = center.z() - 1; z <= center.z() + 1; z++) {
            for (int x = center.x() - 1; x <= center.x() + 1; x++) {
                region.getChunk(x, z).collectBiomesInPalette(biomes);
            }
        }
        return context.generator()
                .buildTerrain(
                        chunk,
                        Blender.of(region),
                        context.randomState(),
                        context.structureManager().forWorldGenRegion(region),
                        region.getBiomeManager(),
                        region,
                        biomes)
                .thenApply(
                        generated -> {
                            Heightmap.primeHeightmaps(
                                    generated,
                                    EnumSet.of(
                                            Heightmap.Types.MOTION_BLOCKING,
                                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                            Heightmap.Types.OCEAN_FLOOR,
                                            Heightmap.Types.WORLD_SURFACE));
                            return generated;
                        });
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
            WorldGenRegion region = region(context, step, cache, chunk);
            context.recordFeatureWorldSeed(region.getSeed());
            if (!SharedConstants.DEBUG_DISABLE_FEATURES) {
                context.generator()
                        .applyBiomeDecoration(
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
}
