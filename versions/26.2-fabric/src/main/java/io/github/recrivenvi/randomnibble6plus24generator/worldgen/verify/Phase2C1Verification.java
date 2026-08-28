package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/** Explicit FEATURES verification harness; never runs without its JVM property. */
public final class Phase2C1Verification {

    public static final String VERIFY_PROPERTY = "randomnibble6plus24generator.phase2c1.verify";
    private static final Set<ChunkStatus> EXPECTED_STAGES = Set.of(
            ChunkStatus.EMPTY,
            ChunkStatus.STRUCTURE_STARTS,
            ChunkStatus.STRUCTURE_REFERENCES,
            ChunkStatus.BIOMES,
            ChunkStatus.NOISE,
            ChunkStatus.SURFACE,
            ChunkStatus.CARVERS,
            ChunkStatus.FEATURES);

    private Phase2C1Verification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(VERIFY_PROPERTY, "");
        if (mode.isBlank()) {
            return;
        }
        long started = System.nanoTime();
        if (mode.equals("smoke")) {
            runSmoke(server);
        } else if (mode.equals("full")) {
            runFull(server);
        } else if (mode.equals("golden-order-debug")) {
            runGoldenOrderDebug(server);
        } else {
            throw new IllegalArgumentException("Unknown Phase 2C1 verification mode: " + mode);
        }
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context leak after FEATURES verification: "
                    + GenerationContextRegistry.bindingCount());
        }
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 {} verification PASS totalMs={}",
                mode,
                (System.nanoTime() - started) / 1_000_000L);
    }

    private static void runSmoke(MinecraftServer server) {
        Fixture fixture = new Fixture(
                Long.getLong("randomnibble6plus24generator.phase2c1.smoke.masterSeed", 123456789L),
                Level.OVERWORLD,
                new ChunkPos(
                        Integer.getInteger("randomnibble6plus24generator.phase2c1.smoke.chunkX", 0),
                        Integer.getInteger("randomnibble6plus24generator.phase2c1.smoke.chunkZ", 0)));
        Result result = run(server, fixture);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 smoke PASS dimension={} masterSeed={} localSeed={} target={} hash={} "
                        + "writers={} radius={} maxConcurrentWriters={} virtualChunks={} runtimeMs={} "
                        + "blockEntities={} blockTicks={} fluidTicks={} postProcessing={} entities={} totalMs={}",
                fixture.dimension().identifier(),
                fixture.masterSeed(),
                result.localSeed(),
                fixture.target(),
                result.snapshot().hash(),
                result.run().featureTrace().requestedWriters().size(),
                result.run().featureTrace().blockStateWriteRadius(),
                result.run().featureTrace().maxConcurrentFeatureWriters(),
                result.run().metrics().virtualChunkCount(),
                result.run().metrics().elapsedNanos() / 1_000_000L,
                result.snapshot().blockEntityCount(),
                result.snapshot().blockTickCount(),
                result.snapshot().fluidTickCount(),
                result.snapshot().postProcessingCount(),
                result.snapshot().entityCount(),
                result.run().metrics().elapsedNanos() / 1_000_000L);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 smoke RNG trace invocations={} sequenceHash={} decorationSeedReads={}",
                result.run().featureTrace().featureSeedInvocationCount(),
                Long.toUnsignedString(result.run().featureTrace().featureSeedSequenceHash(), 16),
                result.run().featureTrace().decorationSeedReads());
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 smoke feature writes {}",
                result.run().featureTrace().featureWriteSummary());
    }

    private static void runFull(MinecraftServer server) {
        List<Fixture> fixtures = matrixFixtures();
        Map<Fixture, String> hashes = new HashMap<>();
        Stats stats = new Stats();
        for (Fixture fixture : fixtures) {
            Result result = run(server, fixture);
            hashes.put(fixture, result.snapshot().hash());
            stats.accept(fixture, result);
        }
        if (fixtures.size() != 72 || hashes.size() != 72) {
            throw new IllegalStateException("Expected 72 unique FEATURES fixtures, found "
                    + fixtures.size() + "/" + hashes.size());
        }

        List<Fixture> replay = List.of(
                fixtures.get(0),
                fixtures.get(8),
                fixtures.get(39),
                fixtures.get(40),
                fixtures.get(56));
        assertReplay(server, replay, hashes, "forward");
        List<Fixture> reverse = new ArrayList<>(replay);
        Collections.reverse(reverse);
        assertReplay(server, reverse, hashes, "reverse");
        runParallelReplay(server, replay, hashes);
        stats.verifyEntityUuidExclusion(server, hashes);

        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 matrix summary fixtures={} dimensions={} avgRuntimeMs={} "
                        + "virtualChunksMin={} virtualChunksMax={} blockEntities={} blockTicks={} "
                        + "fluidTicks={} postProcessing={} entities={}",
                fixtures.size(),
                stats.dimensionCounts,
                stats.elapsedNanos / fixtures.size() / 1_000_000L,
                stats.minVirtualChunks,
                stats.maxVirtualChunks,
                stats.blockEntities,
                stats.blockTicks,
                stats.fluidTicks,
                stats.postProcessing,
                stats.entities);
    }

    private static void runGoldenOrderDebug(MinecraftServer server) {
        Fixture golden = new Fixture(123456789L, Level.OVERWORLD, ChunkPos.ZERO);
        Result baseline = run(server, golden);
        run(server, new Fixture(0L, Level.OVERWORLD, ChunkPos.ZERO));
        run(server, new Fixture(1L, Level.OVERWORLD, ChunkPos.ZERO));
        run(server, new Fixture(-1L, Level.OVERWORLD, ChunkPos.ZERO));
        Result replay = run(server, golden);
        String output = System.getProperty("randomnibble6plus24generator.phase2c1.debug.snapshot");
        if (output != null && !output.isBlank()) {
            baseline.snapshot().write(java.nio.file.Path.of(output));
        }
        FeatureStageSnapshot.Diff diff = baseline.snapshot().diff(replay.snapshot());
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 golden order debug baselineHash={} replayHash={} equivalent={} "
                + "firstDifference={} differingBlocks={} categories={} baselineRawEntities={} replayRawEntities={}",
                baseline.snapshot().hash(),
                replay.snapshot().hash(),
                diff.equivalent(),
                diff.firstDifference(),
                diff.differingBlocks(),
                diff.blockCategories(),
                baseline.snapshot().rawEntityNbt(),
                replay.snapshot().rawEntityNbt());
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2C1 golden trace localSeed={} paleMossRedirects={} cappedProcessorRedirects={} "
                        + "decorationSeedReads={} featureSeedInvocations={} featureSeedSequenceHash={} "
                        + "physicalSeed={} virtualChunks={}",
                baseline.localSeed(),
                baseline.run().featureTrace().paleMossGeneratorRedirects(),
                baseline.run().featureTrace().cappedProcessorSeedRedirects(),
                baseline.run().featureTrace().decorationSeedReads(),
                baseline.run().featureTrace().featureSeedInvocationCount(),
                Long.toUnsignedString(baseline.run().featureTrace().featureSeedSequenceHash(), 16),
                server.getWorldGenSettings().options().seed(),
                baseline.run().metrics().virtualChunkCount());
        if (!diff.equivalent()) {
            throw new IllegalStateException("FEATURES query-order leakage: " + diff);
        }
    }

    private static List<Fixture> matrixFixtures() {
        List<Fixture> fixtures = new ArrayList<>(72);
        long[] originSeeds = {
            0L, 1L, -1L, 123456789L, -987654321L,
            Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEFL
        };
        for (long seed : originSeeds) {
            fixtures.add(new Fixture(seed, Level.OVERWORLD, ChunkPos.ZERO));
        }
        ChunkPos[] overworldPositions = {
            new ChunkPos(1, 0),
            new ChunkPos(-1, 1),
            new ChunkPos(125, -37),
            new ChunkPos(-20_000, 30_000)
        };
        for (long seed : originSeeds) {
            for (ChunkPos pos : overworldPositions) {
                fixtures.add(new Fixture(seed, Level.OVERWORLD, pos));
            }
        }
        long[] dimensionSeeds = {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE};
        ChunkPos[] dimensionPositions = {
            ChunkPos.ZERO,
            new ChunkPos(1, -1),
            new ChunkPos(-125, 37),
            new ChunkPos(10_000, -20_000)
        };
        for (long seed : dimensionSeeds) {
            for (ChunkPos pos : dimensionPositions) {
                fixtures.add(new Fixture(seed, Level.NETHER, pos));
            }
        }
        for (long seed : dimensionSeeds) {
            for (ChunkPos pos : dimensionPositions) {
                fixtures.add(new Fixture(seed, Level.END, pos));
            }
        }
        return List.copyOf(fixtures);
    }

    private static void assertReplay(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expected,
            String label) {
        for (Fixture fixture : fixtures) {
            String actual = run(server, fixture).snapshot().hash();
            if (!expected.get(fixture).equals(actual)) {
                throw new IllegalStateException(label + " FEATURES replay mismatch for " + fixture);
            }
        }
    }

    private static void runParallelReplay(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expected) {
        ExecutorService workers = Executors.newFixedThreadPool(fixtures.size());
        try {
            List<Future<Map.Entry<Fixture, String>>> futures = new ArrayList<>();
            for (Fixture fixture : fixtures) {
                futures.add(workers.submit(() -> Map.entry(fixture, run(server, fixture).snapshot().hash())));
            }
            for (Future<Map.Entry<Fixture, String>> future : futures) {
                Map.Entry<Fixture, String> result;
                try {
                    result = future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException("Concurrent FEATURES verification failed", exception);
                }
                if (!expected.get(result.getKey()).equals(result.getValue())) {
                    throw new IllegalStateException("Concurrent FEATURES mismatch for " + result.getKey());
                }
            }
        } finally {
            workers.shutdownNow();
        }
    }

    private static Result run(MinecraftServer server, Fixture fixture) {
        ServerLevel level = server.getLevel(fixture.dimension());
        if (level == null) {
            throw new IllegalStateException("Missing verification dimension " + fixture.dimension().identifier());
        }
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long localSeed = new MosaicSeedResolver(profile).resolveLocalWorldSeed(
                fixture.masterSeed(), fixture.dimension(), fixture.target());
        FeatureStableGenerationRun run = new IsolatedGenerationSession(profile)
                .generateFeaturesStable(level, fixture.masterSeed(), fixture.target());
        if (!run.executedStages().equals(EXPECTED_STAGES)) {
            throw new IllegalStateException("Unexpected FEATURES stage boundary: " + run.executedStages());
        }
        if (run.featureTrace().observedWorldSeed() != localSeed) {
            throw new IllegalStateException("FEATURES observed incorrect world seed");
        }
        if (run.featureTrace().maxConcurrentFeatureWriters() != 1) {
            throw new IllegalStateException("FEATURES writer serialization failed: " + run.featureTrace());
        }
        if (run.featureTrace().requestedWriters().size() != 9
                || !run.featureTrace().requestedWriters().equals(run.featureTrace().completedWriters())
                || !run.featureTrace().chunksAtOrBeyondFeatures().equals(
                        Set.copyOf(run.featureTrace().requestedWriters()))) {
            throw new IllegalStateException("FEATURES writer frontier/order mismatch: " + run.featureTrace());
        }
        if (run.metrics().physicalLoadedChunksBefore() != run.metrics().physicalLoadedChunksAfter()) {
            throw new IllegalStateException("Isolated FEATURES changed physical loaded-chunk count: " + run.metrics());
        }
        FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                fixture.dimension().identifier().toString(),
                run.targetChunk(),
                level.registryAccess());
        return new Result(localSeed, run, snapshot);
    }

    private record Fixture(long masterSeed, ResourceKey<Level> dimension, ChunkPos target) {
    }

    private record Result(long localSeed, FeatureStableGenerationRun run, FeatureStableSnapshot snapshot) {
    }

    private static void assertHash(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(label + " mismatch; expected=" + expected + ", actual=" + actual);
        }
    }

    private static final class Stats {
        private final Map<String, Integer> dimensionCounts = new HashMap<>();
        private long elapsedNanos;
        private int minVirtualChunks = Integer.MAX_VALUE;
        private int maxVirtualChunks;
        private int blockEntities;
        private int blockTicks;
        private int fluidTicks;
        private int postProcessing;
        private int entities;
        private Fixture entityFixture;
        private List<String> entityRawNbt = List.of();
        private long paleMossRedirects;

        private void accept(Fixture fixture, Result result) {
            dimensionCounts.merge(fixture.dimension().identifier().toString(), 1, Integer::sum);
            elapsedNanos += result.run().metrics().elapsedNanos();
            minVirtualChunks = Math.min(minVirtualChunks, result.run().metrics().virtualChunkCount());
            maxVirtualChunks = Math.max(maxVirtualChunks, result.run().metrics().virtualChunkCount());
            blockEntities += result.snapshot().blockEntityCount();
            blockTicks += result.snapshot().blockTickCount();
            fluidTicks += result.snapshot().fluidTickCount();
            postProcessing += result.snapshot().postProcessingCount();
            entities += result.snapshot().entityCount();
            paleMossRedirects += result.run().featureTrace().paleMossGeneratorRedirects();
            if (result.snapshot().entityCount() > 0 && entityFixture == null) {
                entityFixture = fixture;
                entityRawNbt = result.snapshot().rawEntityNbt();
                RandomNibble6Plus24Generator.LOGGER.info(
                        "Phase 2C1 entity coverage fixture={} entityCount={} rawEntityNbt={}",
                        fixture,
                        result.snapshot().entityCount(),
                        result.snapshot().rawEntityNbt());
            }
            if (result.snapshot().blockEntityCount() > 0
                    || result.snapshot().blockTickCount() > 0
                    || result.snapshot().fluidTickCount() > 0
                    || result.snapshot().postProcessingCount() > 0
                    || result.run().featureTrace().paleMossGeneratorRedirects() > 0) {
                RandomNibble6Plus24Generator.LOGGER.debug(
                        "Phase 2C1 coverage fixture={} blockEntities={} blockTicks={} fluidTicks={} "
                                + "postProcessing={} paleMossRedirects={}",
                        fixture,
                        result.snapshot().blockEntityCount(),
                        result.snapshot().blockTickCount(),
                        result.snapshot().fluidTickCount(),
                        result.snapshot().postProcessingCount(),
                        result.run().featureTrace().paleMossGeneratorRedirects());
            }
        }

        private void verifyEntityUuidExclusion(MinecraftServer server, Map<Fixture, String> hashes) {
            if (entityFixture == null) {
                throw new IllegalStateException("FEATURES matrix did not exercise ProtoChunk entity NBT");
            }
            Result replay = run(server, entityFixture);
            assertHash(hashes.get(entityFixture), replay.snapshot().hash(), "entity canonical replay");
            if (entityRawNbt.equals(replay.snapshot().rawEntityNbt())) {
                throw new IllegalStateException(
                        "Entity coverage fixture did not demonstrate raw UUID nondeterminism: " + entityFixture);
            }
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Phase 2C1 entity UUID exclusion PASS fixture={} canonicalHash={} rawNbtChanged=true",
                    entityFixture,
                    replay.snapshot().hash());
        }
    }
}
