package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.StructureCheck;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;

public final class SurfaceGenerationContext implements AutoCloseable {

    private final SurfaceGenerationMode mode;
    private final ServerLevel hostLevel;
    private final ResourceKey<Level> dimension;
    private final ChunkPos target;
    private final long worldSeed;
    private final NoiseBasedChunkGenerator generator;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState structureState;
    private final EmptyVirtualChunkScanAccess chunkScanAccess;
    private final StructureCheck structureCheck;
    private final StructureManager structureManager;
    private final BiomeManager biomeManager;
    private final WorldGenContext worldGenContext;
    private final VirtualGeneratingChunkMap chunkMap;
    private final long contextSetupNanos;
    private final long structureStateNanos;
    private final Set<ChunkStatus> executedStages = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicLong suppressedPhysicalPoiUpdates = new AtomicLong();
    private final AtomicLong carverStageInvocationCount = new AtomicLong();
    private final AtomicReference<Long> observedCarverSeed = new AtomicReference<>();
    private volatile int carverSourceChunkCount;
    private volatile int configuredCarverCount;
    private volatile int carverChangedBlockCount;
    private volatile int carvingMaskBitCount;
    private boolean closed;

    private SurfaceGenerationContext(
            SurfaceGenerationMode mode,
            ServerLevel hostLevel,
            long worldSeed,
            ChunkPos target) {
        long setupStarted = System.nanoTime();
        this.mode = mode;
        this.hostLevel = hostLevel;
        this.dimension = hostLevel.dimension();
        this.target = target;
        this.worldSeed = worldSeed;
        this.generator = copyNoiseGenerator(hostLevel.getChunkSource().getGenerator());

        MinecraftServer server = hostLevel.getServer();
        RegistryAccess registryAccess = hostLevel.registryAccess();
        this.randomState = RandomState.create(
                generator.generatorSettings().value(),
                registryAccess.lookupOrThrow(Registries.NOISE),
                worldSeed);
        long structureStateStarted = System.nanoTime();
        this.structureState = generator.createState(
                registryAccess.lookupOrThrow(Registries.STRUCTURE_SET),
                randomState,
                worldSeed);
        this.structureState.ensureStructuresGenerated();
        this.structureStateNanos = System.nanoTime() - structureStateStarted;
        this.chunkScanAccess = new EmptyVirtualChunkScanAccess();
        this.structureCheck = new StructureCheck(
                chunkScanAccess,
                registryAccess,
                server.getStructureManager(),
                dimension,
                generator,
                randomState,
                hostLevel,
                generator.getBiomeSource(),
                worldSeed,
                server.getFixerUpper());
        WorldOptions physicalOptions = server.getWorldGenSettings().options();
        this.structureManager = new StructureManager(
                hostLevel,
                new WorldOptions(worldSeed, physicalOptions.generateStructures(), false),
                structureCheck);
        this.biomeManager = new BiomeManager(
                (quartX, quartY, quartZ) -> generator.getBiomeSource().getNoiseBiome(
                        quartX,
                        quartY,
                        quartZ,
                        randomState.sampler()),
                BiomeManager.obfuscateSeed(worldSeed));
        this.worldGenContext = new WorldGenContext(
                hostLevel,
                generator,
                server.getStructureManager(),
                hostLevel.getChunkSource().getLightEngine(),
                server,
                ignored -> {
                });
        this.chunkMap = new VirtualGeneratingChunkMap(this);
        GenerationContextRegistry.bind(worldGenContext, this);
        this.contextSetupNanos = System.nanoTime() - setupStarted;
    }

    public static SurfaceGenerationContext create(
            SurfaceGenerationMode mode,
            ServerLevel hostLevel,
            long worldSeed,
            ChunkPos target) {
        return new SurfaceGenerationContext(mode, hostLevel, worldSeed, target);
    }

    public SurfaceGenerationRun generate() {
        GenerationResult result = generateTo(ChunkStatus.SURFACE);
        return new SurfaceGenerationRun(result.targetChunk(), result.metrics(), result.executedStages());
    }

    public CarverGenerationRun generateCarvers() {
        GenerationResult result = generateTo(ChunkStatus.CARVERS);
        Long observedSeed = observedCarverSeed.get();
        if (observedSeed == null) {
            throw new IllegalStateException("CARVERS completed without observing its world seed");
        }
        return new CarverGenerationRun(
                result.targetChunk(),
                result.metrics(),
                result.executedStages(),
                new CarverTrace(
                        observedSeed,
                        Math.toIntExact(carverStageInvocationCount.get()),
                        carverSourceChunkCount,
                        configuredCarverCount,
                        carverChangedBlockCount,
                        carvingMaskBitCount));
    }

    private GenerationResult generateTo(ChunkStatus targetStatus) {
        ensureOpen();
        long started = System.nanoTime();
        long physicalSeedBefore = hostLevel.getSeed();
        int loadedChunksBefore = hostLevel.getChunkSource().getLoadedChunksCount();
        ChunkGenerationTask task = chunkMap.scheduleGenerationTask(targetStatus, target);

        CompletableFutureDriver.runToCompletion(task);

        ChunkAccess targetChunk = task.getCenter().getChunkIfPresentUnchecked(targetStatus);
        if (targetChunk == null || targetChunk.getPersistedStatus() != targetStatus) {
            throw new IllegalStateException("Target did not reach " + targetStatus + ": " + target);
        }
        if (hostLevel.getSeed() != physicalSeedBefore) {
            throw new PhysicalWorldAccessException("physical ServerLevel seed mutation");
        }
        int loadedChunksAfter = hostLevel.getChunkSource().getLoadedChunksCount();
        long elapsed = System.nanoTime() - started;
        return new GenerationResult(
                targetChunk,
                new SurfaceGenerationMetrics(
                        contextSetupNanos,
                        structureStateNanos,
                        elapsed,
                        chunkMap.virtualChunkCount(),
                        chunkScanAccess.scanCount(),
                        suppressedPhysicalPoiUpdates.get(),
                        loadedChunksBefore,
                        loadedChunksAfter),
                executedStages());
    }

    public SurfaceGenerationMode mode() {
        return mode;
    }

    public ServerLevel hostLevel() {
        return hostLevel;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public ChunkPos target() {
        return target;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public NoiseBasedChunkGenerator generator() {
        return generator;
    }

    public RandomState randomState() {
        return randomState;
    }

    public ChunkGeneratorStructureState structureState() {
        return structureState;
    }

    public StructureCheck structureCheck() {
        return structureCheck;
    }

    public StructureManager structureManager() {
        return structureManager;
    }

    public BiomeManager biomeManager() {
        return biomeManager;
    }

    public WorldGenContext worldGenContext() {
        return worldGenContext;
    }

    public Set<ChunkStatus> executedStages() {
        return Set.copyOf(executedStages);
    }

    public void recordStage(ChunkStatus status) {
        executedStages.add(status);
    }

    public void recordSuppressedPhysicalPoiUpdate() {
        suppressedPhysicalPoiUpdates.incrementAndGet();
    }

    public void recordCarverStageSeed(long seed) {
        if (seed != worldSeed) {
            throw new PhysicalWorldAccessException(
                    "CARVERS received world seed " + seed + " instead of local world seed " + worldSeed);
        }
        observedCarverSeed.compareAndSet(null, seed);
        if (observedCarverSeed.get() != seed) {
            throw new IllegalStateException("Conflicting CARVERS world seeds in one session");
        }
        carverStageInvocationCount.incrementAndGet();
    }

    public void recordTargetCarverTrace(
            int sourceChunkCount,
            int configuredCarverCount,
            int changedBlockCount,
            int carvingMaskBitCount) {
        this.carverSourceChunkCount = sourceChunkCount;
        this.configuredCarverCount = configuredCarverCount;
        this.carverChangedBlockCount = changedBlockCount;
        this.carvingMaskBitCount = carvingMaskBitCount;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            GenerationContextRegistry.unbind(this);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Surface generation context is closed");
        }
    }

    private static NoiseBasedChunkGenerator copyNoiseGenerator(ChunkGenerator generator) {
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            return new NoiseBasedChunkGenerator(
                    noiseGenerator.getBiomeSource(),
                    noiseGenerator.generatorSettings());
        }
        if (generator instanceof MosaicChunkGenerator mosaicGenerator) {
            return new NoiseBasedChunkGenerator(
                    mosaicGenerator.getBiomeSource(),
                    mosaicGenerator.generatorSettings());
        }
        throw new IllegalArgumentException(
                "Isolated generation through CARVERS requires a noise-based generator configuration, found "
                        + generator.getClass().getName());
    }

    private record GenerationResult(
            ChunkAccess targetChunk,
            SurfaceGenerationMetrics metrics,
            Set<ChunkStatus> executedStages) {
    }

    private static final class CompletableFutureDriver {
        private CompletableFutureDriver() {
        }

        private static void runToCompletion(ChunkGenerationTask task) {
            while (true) {
                java.util.concurrent.CompletableFuture<?> wait = task.runUntilWait();
                if (wait == null) {
                    return;
                }
                wait.join();
            }
        }
    }
}
