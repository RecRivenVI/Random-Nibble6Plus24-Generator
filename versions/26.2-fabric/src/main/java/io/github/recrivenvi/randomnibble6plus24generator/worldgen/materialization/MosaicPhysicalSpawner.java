package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.PhysicalWorldAccessException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;

/** Runs only Vanilla's local-seed SPAWN task against an already-lighted target. */
public final class MosaicPhysicalSpawner {

    private MosaicPhysicalSpawner() {
    }

    public static CompletableFuture<ChunkAccess> generateSpawn(
            WorldGenContext physicalContext,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        ServerLevel level = physicalContext.level();
        if (!MosaicPhysicalMaterializer.isPhysicalMosaic(level)) {
            return CompletableFuture.completedFuture(chunk);
        }
        if (step.targetStatus() != ChunkStatus.SPAWN) {
            throw new IllegalArgumentException("Physical Mosaic SPAWN bridge received " + step.targetStatus());
        }
        if (!(level.getChunkSource().getGenerator() instanceof MosaicChunkGenerator mosaicGenerator)) {
            throw new IllegalStateException("Mosaic SPAWN bridge requires MosaicChunkGenerator identity");
        }
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context during SPAWN"));
        ChunkPos target = chunk.getPos();
        long localSeed = runtime.resolveLocalWorldSeed(level.dimension(), target);
        RandomState randomState = RandomState.create(
                mosaicGenerator.generatorSettings().value(),
                level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
                localSeed);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(
                mosaicGenerator.getBiomeSource(), mosaicGenerator.generatorSettings());
        ChunkHolder physicalTargetHolder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(target.x(), target.z()));
        if (physicalTargetHolder == null) {
            throw new IllegalStateException("Physical Mosaic SPAWN target holder is missing: " + target);
        }
        Map<ChunkPos, ChunkAccess> localBiomeChunks;
        try (IsolatedGenerationContext biomeContext = IsolatedGenerationContext.create(
                IsolatedGenerationMode.ISOLATED_MOSAIC, level, localSeed, target)) {
            localBiomeChunks = biomeContext.generateBiomesForSpawn(spawnNeighbors(target));
        }
        MosaicSpawnGenerationContext context = new MosaicSpawnGenerationContext(
                level,
                target,
                localSeed,
                generator,
                randomState,
                localBiomeChunks);
        WorldGenRegion region = null;
        PhysicalMosaicTrace.beginPhysicalStage(level, ChunkStatus.SPAWN, chunk, false);
        StaticCache2D<GenerationChunkHolder> spawnCache = StaticCache2D.create(
                target.x(),
                target.z(),
                1,
                (x, z) -> {
                    ChunkPos requested = new ChunkPos(x, z);
                    if (requested.equals(target)) return physicalTargetHolder;
                    ChunkAccess biome = localBiomeChunks.get(requested);
                    if (biome == null) {
                        throw new IllegalStateException("Missing local SPAWN BIOMES dependency: " + requested);
                    }
                    return new MosaicSpawnBiomeHolder(biome);
                });
        MosaicSpawnContextRegistry.bind(spawnCache, context);
        try {
            region = new WorldGenRegion(level, spawnCache, step, chunk);
            MosaicSpawnContextRegistry.bind(region, context);
            if (region.getSeed() != localSeed) {
                throw new IllegalStateException(
                        "Physical Mosaic SPAWN received seed " + region.getSeed()
                                + " instead of local seed " + localSeed);
            }
            PhysicalMosaicTrace.recordSpawnSeed(level, target, region.getSeed(), localSeed);
            generator.spawnOriginalMobs(region);
            if (context.hasNonTargetChunkReads()) {
                throw new PhysicalWorldAccessException(
                        "Physical Mosaic SPAWN read a non-target physical chunk: " + context.chunkReads());
            }
            PhysicalMosaicTrace.recordSpawnChunkReads(level, target, context.chunkReads());
            return CompletableFuture.completedFuture(chunk);
        } finally {
            MosaicSpawnContextRegistry.unbind(context);
            context.close();
        }
    }

    private static Set<ChunkPos> spawnNeighbors(ChunkPos target) {
        java.util.Set<ChunkPos> positions = new java.util.HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) continue;
                positions.add(new ChunkPos(target.x() + dx, target.z() + dz));
            }
        }
        return Set.copyOf(positions);
    }
}
