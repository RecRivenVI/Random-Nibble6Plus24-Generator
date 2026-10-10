package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.MosaicSpawnGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.PhysicalWorldAccessException;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
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

/** Runs only Vanilla's local-seed SPAWN task against an already-lighted target. */
public final class MosaicPhysicalSpawner {

    private MosaicPhysicalSpawner() {}

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
            throw new IllegalArgumentException(
                    "Physical Mosaic SPAWN bridge received " + step.targetStatus());
        }
        if (!(level.getChunkSource().getGenerator()
                instanceof MosaicChunkGenerator mosaicGenerator)) {
            throw new IllegalStateException(
                    "Mosaic SPAWN bridge requires MosaicChunkGenerator identity");
        }
        MosaicRuntimeContext runtime =
                MosaicWorldIdentity.runtimeContext(level)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Missing Mosaic runtime context during SPAWN"));
        ChunkPos target = chunk.getPos();
        long localSeed = runtime.resolveLocalWorldSeed(level.dimension(), target);
        RandomState randomState =
                RandomState.create(
                        level.registryAccess()
                                .lookupOrThrow(net.minecraft.core.registries.Registries.NOISE),
                        localSeed,
                        mosaicGenerator.generatorSettings().value());
        NoiseBasedChunkGenerator generator =
                new NoiseBasedChunkGenerator(
                        mosaicGenerator.getBiomeSource(), mosaicGenerator.generatorSettings());
        ChunkHolder physicalTargetHolder =
                level.getChunkSource()
                        .chunkMap
                        .getUpdatingChunkIfPresent(ChunkPos.pack(target.x(), target.z()));
        if (physicalTargetHolder == null) {
            throw new IllegalStateException(
                    "Physical Mosaic SPAWN target holder is missing: " + target);
        }
        Map<ChunkPos, ChunkAccess> localBiomeChunks = biomeInputs(level, chunk, localSeed, runtime);
        MosaicSpawnGenerationContext context =
                new MosaicSpawnGenerationContext(
                        level, target, localSeed, generator, randomState, localBiomeChunks);
        WorldGenRegion region = null;
        PhysicalMosaicTrace.beginPhysicalStage(level, ChunkStatus.SPAWN, chunk, false);
        StaticCache2D<GenerationChunkHolder> spawnCache =
                StaticCache2D.create(
                        target.x(),
                        target.z(),
                        1,
                        (x, z) -> {
                            ChunkPos requested = new ChunkPos(x, z);
                            if (requested.equals(target)) return physicalTargetHolder;
                            ChunkAccess biome = localBiomeChunks.get(requested);
                            if (biome == null) {
                                throw new IllegalStateException(
                                        "Missing local SPAWN BIOMES dependency: " + requested);
                            }
                            return new MosaicSpawnBiomeHolder(biome);
                        });
        MosaicSpawnContextRegistry.bind(spawnCache, context);
        try {
            region = new WorldGenRegion(level, spawnCache, step, chunk);
            MosaicSpawnContextRegistry.bind(region, context);
            if (region.getSeed() != localSeed) {
                throw new IllegalStateException(
                        "Physical Mosaic SPAWN received seed "
                                + region.getSeed()
                                + " instead of local seed "
                                + localSeed);
            }
            PhysicalMosaicTrace.recordSpawnSeed(level, target, region.getSeed(), localSeed);
            generator.spawnOriginalMobs(region);
            if (context.hasNonTargetChunkReads()) {
                throw new PhysicalWorldAccessException(
                        "Physical Mosaic SPAWN read a non-target physical chunk: "
                                + context.chunkReads());
            }
            PhysicalMosaicTrace.recordSpawnChunkReads(level, target, context.chunkReads());
            return CompletableFuture.completedFuture(chunk);
        } finally {
            MosaicSpawnContextRegistry.unbind(context);
            context.close();
            ((MosaicSpawnBiomeCarrier) chunk).randomnibble6plus24generator$spawnBiomes(null);
        }
    }

    private static Map<ChunkPos, ChunkAccess> biomeInputs(
            ServerLevel level, ChunkAccess chunk, long localSeed, MosaicRuntimeContext runtime) {
        SpawnBiomeSnapshot snapshot =
                ((MosaicSpawnBiomeCarrier) chunk).randomnibble6plus24generator$spawnBiomes();
        if (snapshot == null) {
            // A saved FEATURES/LIGHT ProtoChunk has no transient input. Retain the
            // exact V2 cold-recovery path; never regenerate Artifact or terrain.
            try (IsolatedGenerationContext context =
                    IsolatedGenerationContext.create(
                            IsolatedGenerationMode.ISOLATED_MOSAIC,
                            level,
                            localSeed,
                            chunk.getPos())) {
                return context.generateBiomesForSpawn(
                        Set.copyOf(SpawnBiomeSnapshot.neighbors(chunk.getPos())));
            }
        }
        snapshot.validate(level.dimension(), chunk.getPos(), localSeed, runtime.profile());
        Map<ChunkPos, ChunkAccess> inputs = new java.util.LinkedHashMap<>();
        for (ChunkPos pos : snapshot.positions()) {
            inputs.put(
                    pos, new MosaicSpawnBiomeView(pos, snapshot, level.palettedContainerFactory()));
        }
        return Map.copyOf(inputs);
    }
}
