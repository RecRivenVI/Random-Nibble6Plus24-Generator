package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.nbt.CompoundTag;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator.MosaicChunkGenerator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ConcentricRingStateAccess;

public final class IsolatedGenerationContext implements AutoCloseable {

    private final IsolatedGenerationMode mode;
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
    private final LevelLightEngine localLightEngine;
    private final long contextSetupNanos;
    private final long structureStateNanos;
    private final Set<ChunkStatus> executedStages = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicLong suppressedPhysicalPoiUpdates = new AtomicLong();
    private final AtomicLong carverStageInvocationCount = new AtomicLong();
    private final AtomicReference<Long> observedCarverSeed = new AtomicReference<>();
    private final AtomicReference<Long> observedFeatureSeed = new AtomicReference<>();
    private final List<ChunkPos> requestedFeatureWriters = new CopyOnWriteArrayList<>();
    private final List<ChunkPos> completedFeatureWriters = new CopyOnWriteArrayList<>();
    private final List<String> featureVisibleBiomeSequence = new CopyOnWriteArrayList<>();
    private final AtomicInteger activeFeatureWriters = new AtomicInteger();
    private final AtomicInteger maxConcurrentFeatureWriters = new AtomicInteger();
    private final AtomicLong paleMossGeneratorRedirects = new AtomicLong();
    private final AtomicLong cappedProcessorSeedRedirects = new AtomicLong();
    private final AtomicLong decorationSeedReads = new AtomicLong();
    private final AtomicLong featureSeedInvocationCount = new AtomicLong();
    private final AtomicLong featureSeedSequenceHash = new AtomicLong(0xcbf29ce484222325L);
    private final AtomicLong localUncachedBiomeReads = new AtomicLong();
    private final Map<String, FeatureWriteAccumulator> featureWrites = new TreeMap<>();
    private final Map<String, Long> physicalLevelEscapes = new TreeMap<>();
    private volatile Set<Long> expectedFeatureWriters = Set.of();
    private volatile int carverSourceChunkCount;
    private volatile int configuredCarverCount;
    private volatile int carverChangedBlockCount;
    private volatile int carvingMaskBitCount;
    private boolean closed;

    private IsolatedGenerationContext(
            IsolatedGenerationMode mode,
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
        if (mode == IsolatedGenerationMode.ISOLATED_MOSAIC) {
            ((ConcentricRingStateAccess) structureState).randomnibble6plus24generator$setRingScope(
                    ConcentricRingScope.forV2Target(target, worldSeed,
                            generator.getBiomeSource(), structureState.possibleStructureSets()));
        }
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
        this.localLightEngine = new LevelLightEngine(new LightChunkGetter() {
            @Override
            public net.minecraft.world.level.chunk.LightChunk getChunkForLighting(int x, int z) {
                return chunkMap.chunkForLighting(x, z);
            }

            @Override
            public net.minecraft.world.level.BlockGetter getLevel() {
                // The engine uses this as the dimension height accessor. No physical chunk lookup is delegated.
                return hostLevel;
            }
        }, true, hostLevel.dimensionType().hasSkyLight());
        GenerationContextRegistry.bind(worldGenContext, this);
        this.contextSetupNanos = System.nanoTime() - setupStarted;
    }

    public static IsolatedGenerationContext create(
            IsolatedGenerationMode mode,
            ServerLevel hostLevel,
            long worldSeed,
            ChunkPos target) {
        return new IsolatedGenerationContext(mode, hostLevel, worldSeed, target);
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

    /**
     * Completes the finite V1 writer frontier in its frozen absolute Z/X order.
     * A plain single-center generate-to-FEATURES operation is intentionally not exposed.
     */
    public FeatureStableGenerationRun generateFeaturesStable() {
        ensureOpen();
        FeatureOrderingPlan plan = FeatureOrderingPlan.targetLocalZxRowMajorV1(target);
        expectedFeatureWriters = plan.packedWriterSet();
        requestedFeatureWriters.clear();
        completedFeatureWriters.clear();
        requestedFeatureWriters.addAll(plan.writers());

        long started = System.nanoTime();
        long physicalSeedBefore = hostLevel.getSeed();
        int loadedChunksBefore = hostLevel.getChunkSource().getLoadedChunksCount();
        for (ChunkPos writer : plan.writers()) {
            ChunkGenerationTask task = chunkMap.scheduleGenerationTask(ChunkStatus.FEATURES, writer);
            CompletableFutureDriver.runToCompletion(task);
            ChunkAccess writerChunk = task.getCenter().getChunkIfPresentUnchecked(ChunkStatus.FEATURES);
            if (writerChunk == null || !writerChunk.getPersistedStatus().isOrAfter(ChunkStatus.FEATURES)) {
                throw new IllegalStateException("FEATURES writer did not reach FEATURES: " + writer);
            }
        }

        ChunkAccess targetChunk = chunkMap.chunkAt(target, ChunkStatus.FEATURES);
        if (targetChunk == null || targetChunk.getPersistedStatus() != ChunkStatus.FEATURES) {
            throw new IllegalStateException("Stable target did not stop at FEATURES: " + target);
        }
        Set<ChunkPos> actualFeatureChunks = chunkMap.chunksAtOrBeyond(ChunkStatus.FEATURES);
        Set<ChunkPos> expectedFeatureChunks = plan.writers().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!actualFeatureChunks.equals(expectedFeatureChunks)) {
            throw new IllegalStateException(
                    "FeatureOrderingAlgorithmVersion 1 frontier violation; expected="
                            + expectedFeatureChunks + ", actual=" + actualFeatureChunks);
        }
        if (!completedFeatureWriters.equals(plan.writers())) {
            throw new IllegalStateException(
                    "FEATURES execution order violation; requested=" + plan.writers()
                            + ", completed=" + completedFeatureWriters);
        }
        Long observedSeed = observedFeatureSeed.get();
        if (observedSeed == null || observedSeed != worldSeed) {
            throw new PhysicalWorldAccessException(
                    "FEATURES did not observe local world seed " + worldSeed + "; observed=" + observedSeed);
        }
        if (hostLevel.getSeed() != physicalSeedBefore) {
            throw new PhysicalWorldAccessException("physical ServerLevel seed mutation during FEATURES");
        }

        int loadedChunksAfter = hostLevel.getChunkSource().getLoadedChunksCount();
        long elapsed = System.nanoTime() - started;
        IsolatedGenerationMetrics metrics = new IsolatedGenerationMetrics(
                contextSetupNanos,
                structureStateNanos,
                elapsed,
                chunkMap.virtualChunkCount(),
                chunkScanAccess.scanCount(),
                suppressedPhysicalPoiUpdates.get(),
                loadedChunksBefore,
                loadedChunksAfter);
        return new FeatureStableGenerationRun(
                targetChunk,
                metrics,
                executedStages(),
                new FeatureGenerationTrace(
                        plan.algorithmVersion(),
                        plan.blockStateWriteRadius(),
                        requestedFeatureWriters,
                        completedFeatureWriters,
                        actualFeatureChunks,
                        maxConcurrentFeatureWriters.get(),
                        observedSeed,
                        paleMossGeneratorRedirects.get(),
                        cappedProcessorSeedRedirects.get(),
                        decorationSeedReads.get(),
                        featureSeedInvocationCount.get(),
                        featureSeedSequenceHash.get(),
                        localUncachedBiomeReads.get(),
                        featureVisibleBiomeSequence,
                        chunkMap.statusDistribution(),
                        featureWriteSummary(),
                        physicalLevelEscapeSummary()));
    }

    /**
     * Captures same-local-universe starts from the ordered writer frontier whose
     * pieces intersect the target's 16x16 footprint. The returned NBT is
     * detached before the session closes so the runtime overlay never needs to
     * rerun FEATURES or inspect a physical neighbor.
     */
    public List<CompoundTag> captureExternalStructureStarts(
            StructurePieceSerializationContext structureContext) {
        ensureOpen();
        FeatureOrderingPlan plan = FeatureOrderingPlan.targetLocalZxRowMajorV1(target);
        BoundingBox targetBox = new BoundingBox(
                target.getMinBlockX(), hostLevel.getMinY(), target.getMinBlockZ(),
                target.getMaxBlockX(), hostLevel.getMaxY() - 1, target.getMaxBlockZ());
        List<CompoundTag> result = new java.util.ArrayList<>();
        for (ChunkPos writer : plan.writers()) {
            if (writer.equals(target)) continue;
            ChunkAccess chunk = chunkMap.chunkAt(writer, ChunkStatus.FEATURES);
            if (chunk == null) continue;
            for (StructureStart start : chunk.getAllStarts().values()) {
                if (start == null || !start.isValid()) continue;
                boolean intersects = start.getPieces().stream()
                        .anyMatch(piece -> piece.getBoundingBox().intersects(targetBox));
                if (intersects) result.add(start.createTag(structureContext, writer));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Builds only the BIOMES-level virtual inputs needed by the physical SPAWN
     * bridge.  No terrain, carvers, features, or Artifact work is performed.
     */
    public Map<ChunkPos, ChunkAccess> generateBiomesForSpawn(Set<ChunkPos> positions) {
        ensureOpen();
        Map<ChunkPos, ChunkAccess> result = new java.util.LinkedHashMap<>();
        positions.stream().sorted(java.util.Comparator.comparingInt(ChunkPos::z)
                .thenComparingInt(ChunkPos::x)).forEach(pos -> {
            ChunkGenerationTask task = chunkMap.scheduleGenerationTask(ChunkStatus.BIOMES, pos);
            CompletableFutureDriver.runToCompletion(task);
            ChunkAccess chunk = chunkMap.chunkAt(pos, ChunkStatus.BIOMES);
            if (chunk == null || chunk.getPersistedStatus() != ChunkStatus.BIOMES) {
                throw new IllegalStateException("SPAWN BIOMES dependency did not reach BIOMES: " + pos);
            }
            result.put(pos, chunk);
        });
        return Map.copyOf(result);
    }

    /** Copies only the already-generated stored palettes before this session closes. */
    public SpawnBiomeSnapshot captureSpawnBiomes(MosaicWorldProfile profile) {
        ensureOpen();
        if (!chunkMap.chunksAtOrBeyond(ChunkStatus.FEATURES).containsAll(SpawnBiomeSnapshot.neighbors(target))) {
            throw new IllegalStateException("SPAWN input capture requires the completed V2 FEATURES frontier");
        }
        return SpawnBiomeSnapshot.capture(dimension, target, worldSeed, profile,
                pos -> chunkMap.chunkAt(pos, ChunkStatus.BIOMES));
    }

    private GenerationResult generateTo(ChunkStatus targetStatus) {
        ensureOpen();
        if (targetStatus.isOrAfter(ChunkStatus.FEATURES)) {
            throw new IllegalArgumentException(
                    "FEATURES requires generateFeaturesStable() and its complete ordered writer frontier");
        }
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
                new IsolatedGenerationMetrics(
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

    public IsolatedGenerationMode mode() {
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

    public LevelLightEngine localLightEngine() {
        return localLightEngine;
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

    public void beginFeatureWriter(ChunkPos writer) {
        long packed = ChunkPos.pack(writer.x(), writer.z());
        if (!expectedFeatureWriters.contains(packed)) {
            throw new IllegalStateException("Unexpected FEATURES writer outside V1 frontier: " + writer);
        }
        int active = activeFeatureWriters.incrementAndGet();
        maxConcurrentFeatureWriters.accumulateAndGet(active, Math::max);
        if (active != 1) {
            throw new IllegalStateException("Concurrent FEATURES writers inside one isolated session: " + active);
        }
    }

    public void completeFeatureWriter(ChunkPos writer) {
        completedFeatureWriters.add(writer);
        int active = activeFeatureWriters.decrementAndGet();
        if (active != 0) {
            throw new IllegalStateException("FEATURES writer accounting imbalance after " + writer + ": " + active);
        }
    }

    public void recordFeatureWorldSeed(long seed) {
        if (seed != worldSeed) {
            throw new PhysicalWorldAccessException(
                    "FEATURES received world seed " + seed + " instead of local world seed " + worldSeed);
        }
        observedFeatureSeed.compareAndSet(null, seed);
        if (observedFeatureSeed.get() != seed) {
            throw new IllegalStateException("Conflicting FEATURES world seeds in one session");
        }
    }

    public void recordFeatureVisibleBiomes(String signature) {
        featureVisibleBiomeSequence.add(signature);
    }

    public void recordPaleMossGeneratorRedirect() {
        paleMossGeneratorRedirects.incrementAndGet();
    }

    public void recordCappedProcessorSeedRedirect() {
        cappedProcessorSeedRedirects.incrementAndGet();
    }

    public void recordDecorationSeedRead(long seed) {
        if (seed != worldSeed) {
            throw new PhysicalWorldAccessException(
                    "FEATURES decoration seed " + seed + " != local world seed " + worldSeed);
        }
        decorationSeedReads.incrementAndGet();
    }

    public void recordFeatureSeed(long decorationSeed, int featureIndex, int step) {
        featureSeedInvocationCount.incrementAndGet();
        featureSeedSequenceHash.updateAndGet(current -> {
            long mixed = current ^ decorationSeed;
            mixed *= 0x100000001b3L;
            mixed ^= Integer.toUnsignedLong(featureIndex);
            mixed *= 0x100000001b3L;
            mixed ^= Integer.toUnsignedLong(step);
            return mixed * 0x100000001b3L;
        });
    }

    public synchronized void recordFeatureWrite(String feature, BlockPos pos, BlockState state) {
        featureWrites.computeIfAbsent(feature, ignored -> new FeatureWriteAccumulator())
                .record(pos, state);
    }

    public void recordLocalUncachedBiomeRead() {
        localUncachedBiomeReads.incrementAndGet();
    }

    public synchronized void recordPhysicalLevelEscape(String caller) {
        physicalLevelEscapes.merge(caller, 1L, Long::sum);
    }

    private synchronized Map<String, Long> physicalLevelEscapeSummary() {
        return Map.copyOf(physicalLevelEscapes);
    }

    private synchronized Map<String, String> featureWriteSummary() {
        Map<String, String> summary = new TreeMap<>();
        featureWrites.forEach((feature, accumulator) -> summary.put(
                feature,
                accumulator.count + "@" + Long.toUnsignedString(accumulator.hash, 16)));
        return summary;
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
            throw new IllegalStateException("Isolated generation context is closed");
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
                "Isolated generation through FEATURES requires a noise-based generator configuration, found "
                        + generator.getClass().getName());
    }

    private record GenerationResult(
            ChunkAccess targetChunk,
            IsolatedGenerationMetrics metrics,
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

    private static final class FeatureWriteAccumulator {
        private long count;
        private long hash = 0xcbf29ce484222325L;

        private void record(BlockPos pos, BlockState state) {
            count++;
            hash ^= pos.asLong();
            hash *= 0x100000001b3L;
            hash ^= state.toString().hashCode();
            hash *= 0x100000001b3L;
        }
    }
}
