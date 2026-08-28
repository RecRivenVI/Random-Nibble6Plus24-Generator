package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.CarverGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.CarverTrace;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMetrics;

/** Explicit CARVERS development harness; never runs unless its JVM property is set. */
public final class Phase2BVerification {

    public static final String VERIFY_PROPERTY = "randomnibble6plus24generator.phase2b.verify";
    public static final String SURFACE_GOLDEN =
            "cf8a46342321d8ec0baa089631debd3c38572b3dd8968fbbb66c76dd2e7d5b3f";
    public static final String CARVER_GOLDEN =
            "8f0eb2a9d708ade9fba2dbfe3e21f1a6d3aa318d7c4052db5ea134bd42f0fa0a";

    private static final Fixture SMOKE_FIXTURE =
            new Fixture(123456789L, Level.OVERWORLD, ChunkPos.ZERO);
    private static final Set<ChunkStatus> EXPECTED_CARVER_STAGES = Set.of(
            ChunkStatus.EMPTY,
            ChunkStatus.STRUCTURE_STARTS,
            ChunkStatus.STRUCTURE_REFERENCES,
            ChunkStatus.BIOMES,
            ChunkStatus.NOISE,
            ChunkStatus.SURFACE,
            ChunkStatus.CARVERS);

    private Phase2BVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(VERIFY_PROPERTY, "");
        if (mode.isBlank()) {
            return;
        }
        RandomNibble6Plus24Generator.LOGGER.info("Starting Phase 2B {} verification", mode);
        long started = System.nanoTime();
        long memoryBefore = usedMemory();
        if (mode.equals("smoke")) {
            runSmoke(server);
        } else if (mode.equals("full")) {
            runFullMatrix(server, memoryBefore);
        } else {
            throw new IllegalArgumentException("Unknown Phase 2B verification mode: " + mode);
        }
        assertNoContextLeak("Phase 2B completion");
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2B {} verification PASS in {} ms",
                mode,
                (System.nanoTime() - started) / 1_000_000L);
    }

    private static void runSmoke(MinecraftServer server) {
        ServerLevel level = requireLevel(server, Level.OVERWORLD);
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        long localSeed = new MosaicSeedResolver(profile).resolveLocalWorldSeed(
                SMOKE_FIXTURE.masterSeed(),
                SMOKE_FIXTURE.dimension(),
                SMOKE_FIXTURE.target());

        var surfaceRun = new IsolatedGenerationSession(profile)
                .generateSurface(level, SMOKE_FIXTURE.masterSeed(), SMOKE_FIXTURE.target());
        SurfaceStageSnapshot surface = SurfaceStageSnapshot.capture(surfaceRun.targetChunk(), level.registryAccess());
        assertHash(SURFACE_GOLDEN, surface.hash(), "SURFACE golden before CARVERS");

        FixtureResult first = runFixture(server, SMOKE_FIXTURE, true);
        runIsolated(server, new Fixture(0L, Level.OVERWORLD, ChunkPos.ZERO));
        runIsolated(server, new Fixture(1L, Level.OVERWORLD, ChunkPos.ZERO));
        runIsolated(server, new Fixture(-1L, Level.OVERWORLD, ChunkPos.ZERO));
        FixtureResult after = runFixture(server, SMOKE_FIXTURE, false);
        assertHash(first.hash(), after.hash(), "CARVERS smoke query order");
        assertHash(CARVER_GOLDEN, first.hash(), "cross-process CARVERS golden");

        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2B smoke PASS surfaceHash={} carverHash={} changedBlocks={} maskBits={} "
                        + "configuredCarvers={} sourceChunks={} virtualChunks={} carverMs={} localSeed={}",
                surface.hash(),
                first.hash(),
                first.trace().changedBlockCount(),
                first.trace().carvingMaskBitCount(),
                first.trace().configuredCarverCount(),
                first.trace().sourceChunkCount(),
                first.metrics().virtualChunkCount(),
                first.metrics().elapsedNanos() / 1_000_000L,
                localSeed);
    }

    private static void runFullMatrix(MinecraftServer server, long memoryBefore) {
        VerificationStats stats = new VerificationStats();
        Map<Fixture, String> hashes = new HashMap<>();
        long[] originSeeds = {
            0L,
            1L,
            -1L,
            123456789L,
            -987654321L,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            0x123456789ABCDEFL
        };
        for (long seed : originSeeds) {
            verifyAndRecord(server, new Fixture(seed, Level.OVERWORLD, ChunkPos.ZERO), hashes, stats);
        }

        ChunkPos[] overworldPositions = {
            new ChunkPos(1, 0),
            new ChunkPos(-1, 1),
            new ChunkPos(125, -37),
            new ChunkPos(-20_000, 30_000)
        };
        for (long seed : originSeeds) {
            for (ChunkPos pos : overworldPositions) {
                verifyAndRecord(server, new Fixture(seed, Level.OVERWORLD, pos), hashes, stats);
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
                verifyAndRecord(server, new Fixture(seed, Level.NETHER, pos), hashes, stats);
                verifyAndRecord(server, new Fixture(seed, Level.END, pos), hashes, stats);
            }
        }

        if (stats.fixtureCount != 72 || hashes.size() != 72) {
            throw new IllegalStateException("Expected exactly 72 CARVERS fixtures, found "
                    + stats.fixtureCount + "/" + hashes.size());
        }
        assertHash(CARVER_GOLDEN, hashes.get(SMOKE_FIXTURE), "72-fixture CARVERS golden");

        List<Fixture> orderFixtures = List.of(
                SMOKE_FIXTURE,
                new Fixture(1L, Level.OVERWORLD, new ChunkPos(1, 0)),
                new Fixture(-1L, Level.OVERWORLD, new ChunkPos(-1, 1)),
                new Fixture(Long.MIN_VALUE, Level.NETHER, new ChunkPos(1, -1)),
                new Fixture(Long.MAX_VALUE, Level.END, new ChunkPos(-125, 37)));
        runOrderIndependence(server, orderFixtures, hashes);
        runConcurrentIsolation(server, orderFixtures, hashes);
        runExceptionCleanupAndWorkerReuse(server, orderFixtures.getFirst(), hashes);
        runPhysicalNeighborIsolation(server);
        stats.assertCarvingCoverage();
        assertNoContextLeak("full CARVERS verification");

        long memoryAfter = usedMemory();
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2B matrix summary fixtures={} avgCarverMs={} avgSetupMs={} avgStructureStateMs={} "
                        + "virtualChunksMin={} virtualChunksMax={} memoryDeltaMiB={} dimensions={}",
                stats.fixtureCount,
                stats.carverNanos / stats.fixtureCount / 1_000_000L,
                stats.setupNanos / stats.fixtureCount / 1_000_000L,
                stats.structureStateNanos / stats.fixtureCount / 1_000_000L,
                stats.minVirtualChunks,
                stats.maxVirtualChunks,
                (memoryAfter - memoryBefore) / (1024L * 1024L),
                stats.dimensionStats);
    }

    private static void verifyAndRecord(
            MinecraftServer server,
            Fixture fixture,
            Map<Fixture, String> hashes,
            VerificationStats stats) {
        FixtureResult result = runFixture(server, fixture, true);
        if (hashes.put(fixture, result.hash()) != null) {
            throw new IllegalStateException("Duplicate CARVERS fixture " + fixture);
        }
        stats.accept(fixture, result);
    }

    private static FixtureResult runFixture(
            MinecraftServer server,
            Fixture fixture,
            boolean log) {
        ServerLevel level = requireLevel(server, fixture.dimension());
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        long localSeed = new MosaicSeedResolver(profile).resolveLocalWorldSeed(
                fixture.masterSeed(),
                fixture.dimension(),
                fixture.target());
        if (fixture.dimension() == Level.OVERWORLD
                && fixture.target().equals(ChunkPos.ZERO)
                && localSeed != fixture.masterSeed()) {
            throw new IllegalStateException("Origin preservation failed for " + fixture);
        }
        if (fixture.dimension() == Level.OVERWORLD
                && !fixture.target().equals(ChunkPos.ZERO)
                && localSeed == fixture.masterSeed()) {
            throw new IllegalStateException("Non-origin fixture did not exercise a derived seed: " + fixture);
        }

        CarverGenerationRun isolatedRun = new IsolatedGenerationSession(profile)
                .generateCarvers(level, fixture.masterSeed(), fixture.target());
        CarverGenerationRun controlRun = new VanillaCarverControl()
                .generateCarvers(level, localSeed, fixture.target());
        assertBoundary(isolatedRun);
        assertBoundary(controlRun);
        assertTrace(isolatedRun.carverTrace(), localSeed, fixture, "isolated");
        assertTrace(controlRun.carverTrace(), localSeed, fixture, "virtual-control");
        if (!isolatedRun.carverTrace().equals(controlRun.carverTrace())) {
            throw new SurfaceParityMismatchException(
                    "CARVERS trace mismatch for " + fixture + "; isolated="
                            + isolatedRun.carverTrace() + ", control=" + controlRun.carverTrace());
        }

        CarverStageSnapshot isolated = CarverStageSnapshot.capture(
                isolatedRun.targetChunk(), level.registryAccess());
        CarverStageSnapshot control = CarverStageSnapshot.capture(
                controlRun.targetChunk(), level.registryAccess());
        try {
            isolated.assertEquivalentTo(control);
        } catch (SurfaceParityMismatchException mismatch) {
            throw new SurfaceParityMismatchException(
                    "stage=CARVERS, dimension=" + fixture.dimension().identifier()
                            + ", masterSeed=" + fixture.masterSeed()
                            + ", localWorldSeed=" + localSeed
                            + ", chunkPos=" + fixture.target()
                            + ", firstDivergence=" + mismatch.getMessage());
        }

        if (log) {
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Phase 2B parity PASS dimension={} masterSeed={} localSeed={} chunk={} hash={} "
                            + "changedBlocks={} maskBits={} configuredCarvers={} carverMs={} virtualChunks={}",
                    fixture.dimension().identifier(),
                    fixture.masterSeed(),
                    localSeed,
                    fixture.target(),
                    isolated.hash(),
                    isolatedRun.carverTrace().changedBlockCount(),
                    isolated.carvingMaskBitCount(),
                    isolatedRun.carverTrace().configuredCarverCount(),
                    isolatedRun.metrics().elapsedNanos() / 1_000_000L,
                    isolatedRun.metrics().virtualChunkCount());
        }
        return new FixtureResult(isolated.hash(), isolatedRun.metrics(), isolatedRun.carverTrace());
    }

    private static IsolatedResult runIsolated(MinecraftServer server, Fixture fixture) {
        ServerLevel level = requireLevel(server, fixture.dimension());
        MosaicWorldProfile profile = MosaicWorldProfile.version1();
        long localSeed = new MosaicSeedResolver(profile).resolveLocalWorldSeed(
                fixture.masterSeed(), fixture.dimension(), fixture.target());
        CarverGenerationRun run = new IsolatedGenerationSession(profile)
                .generateCarvers(level, fixture.masterSeed(), fixture.target());
        assertBoundary(run);
        assertTrace(run.carverTrace(), localSeed, fixture, "isolated-replay");
        return new IsolatedResult(
                CarverStageSnapshot.capture(run.targetChunk(), level.registryAccess()),
                run,
                localSeed);
    }

    private static void runOrderIndependence(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expectedHashes) {
        assertSequence(server, fixtures, expectedHashes, "forward");
        List<Fixture> reverse = new ArrayList<>(fixtures);
        Collections.reverse(reverse);
        assertSequence(server, reverse, expectedHashes, "reverse");
        List<Fixture> shuffled = new ArrayList<>(fixtures);
        Collections.shuffle(shuffled, new Random(0x26_2B_CA4EL));
        assertSequence(server, shuffled, expectedHashes, "shuffle");
        assertNoContextLeak("CARVERS order replays");
        RandomNibble6Plus24Generator.LOGGER.info("Phase 2B forward/reverse/shuffle order independence PASS");
    }

    private static void assertSequence(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expectedHashes,
            String label) {
        for (Fixture fixture : fixtures) {
            assertHash(
                    expectedHashes.get(fixture),
                    runIsolated(server, fixture).snapshot().hash(),
                    label + " " + fixture);
        }
    }

    private static void runConcurrentIsolation(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expectedHashes) {
        ExecutorService workers = Executors.newFixedThreadPool(fixtures.size());
        try {
            List<Future<ConcurrentResult>> futures = new ArrayList<>();
            for (Fixture fixture : fixtures) {
                futures.add(workers.submit(() -> new ConcurrentResult(
                        fixture,
                        runIsolated(server, fixture).snapshot().hash())));
            }
            for (Future<ConcurrentResult> future : futures) {
                ConcurrentResult result = get(future);
                assertHash(expectedHashes.get(result.fixture()), result.hash(), "parallel " + result.fixture());
            }
        } finally {
            workers.shutdownNow();
        }
        assertNoContextLeak("parallel CARVERS sessions");
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2B concurrent session isolation PASS sessions={}", fixtures.size());
    }

    private static void runExceptionCleanupAndWorkerReuse(
            MinecraftServer server,
            Fixture validFixture,
            Map<Fixture, String> expectedHashes) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> failure = worker.submit(() -> {
                try {
                    runIsolated(server, new Fixture(
                            17L,
                            Level.OVERWORLD,
                            new ChunkPos(Integer.MAX_VALUE, Integer.MAX_VALUE)));
                    return false;
                } catch (RuntimeException expected) {
                    return true;
                }
            });
            if (!get(failure)) {
                throw new IllegalStateException("Injected CARVERS session did not fail");
            }
            assertNoContextLeak("CARVERS exception path");
            Future<String> reuse = worker.submit(() -> runIsolated(server, validFixture).snapshot().hash());
            assertHash(expectedHashes.get(validFixture), get(reuse), "CARVERS worker reuse");
        } finally {
            worker.shutdownNow();
        }
        assertNoContextLeak("CARVERS worker reuse completion");
        RandomNibble6Plus24Generator.LOGGER.info("Phase 2B exception cleanup and worker reuse PASS");
    }

    private static void runPhysicalNeighborIsolation(MinecraftServer server) {
        ServerLevel level = requireLevel(server, Level.OVERWORLD);
        Fixture fixture = new Fixture(0x510E527FADE682D1L, Level.OVERWORLD, new ChunkPos(50_100, -50_100));
        IsolatedResult absent = runIsolated(server, fixture);

        List<ChunkPos> physicalPositions = square(fixture.target(), 1);
        List<ChunkPos> loadOrder = new ArrayList<>(physicalPositions);
        Collections.shuffle(loadOrder, new Random(0x5048595349432B4CL));
        for (ChunkPos pos : loadOrder) {
            level.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, true);
        }
        IsolatedResult generated = runIsolated(server, fixture);
        assertHash(absent.snapshot().hash(), generated.snapshot().hash(),
                "CARVERS physical neighbors absent versus generated");

        List<BlockPos> markers = new ArrayList<>();
        for (int index = 0; index < physicalPositions.size(); index++) {
            ChunkPos pos = physicalPositions.get(index);
            for (int offsetY = 0; offsetY < 8; offsetY++) {
                BlockPos marker = new BlockPos(
                        pos.getMinBlockX() + 8,
                        level.getMinY() + 48 + index + offsetY,
                        pos.getMinBlockZ() + 8);
                markers.add(marker);
                level.setBlock(
                        marker,
                        offsetY % 2 == 0
                                ? Blocks.CAVE_AIR.defaultBlockState()
                                : Blocks.OBSIDIAN.defaultBlockState(),
                        Block.UPDATE_CLIENTS);
            }
        }
        PhysicalWorldState before = PhysicalWorldState.capture(level, physicalPositions, markers);
        IsolatedResult modified = runIsolated(server, fixture);
        PhysicalWorldState after = PhysicalWorldState.capture(level, physicalPositions, markers);
        assertHash(absent.snapshot().hash(), modified.snapshot().hash(),
                "CARVERS physical neighbors absent versus cave-modified");
        before.assertUnchangedBy(after);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2B physical cave isolation PASS target={} physicalChunks={} modifiedBlocks={} "
                        + "blockTicks={} fluidTicks={}",
                fixture.target(),
                physicalPositions.size(),
                markers.size(),
                after.blockTicks(),
                after.fluidTicks());
    }

    private static void assertBoundary(CarverGenerationRun run) {
        if (!run.executedStages().equals(EXPECTED_CARVER_STAGES)) {
            throw new IllegalStateException("Unexpected CARVERS stage set: " + run.executedStages());
        }
        if (run.targetChunk().getPersistedStatus() != ChunkStatus.CARVERS) {
            throw new IllegalStateException("Target crossed or missed CARVERS: "
                    + run.targetChunk().getPersistedStatus());
        }
        if (run.metrics().physicalLoadedChunksBefore() != run.metrics().physicalLoadedChunksAfter()) {
            throw new PhysicalSideEffectAssertionError(
                    "Physical loaded chunk count changed during CARVERS: " + run.metrics());
        }
    }

    private static void assertTrace(
            CarverTrace trace,
            long localSeed,
            Fixture fixture,
            String path) {
        if (trace.observedWorldSeed() != localSeed) {
            throw new IllegalStateException(path + " CARVERS seed mismatch for " + fixture
                    + "; expected=" + localSeed + ", actual=" + trace.observedWorldSeed());
        }
        if (trace.carverStageInvocationCount() < 1 || trace.sourceChunkCount() != 17 * 17) {
            throw new IllegalStateException(path + " CARVERS trace incomplete for " + fixture + ": " + trace);
        }
    }

    private static ServerLevel requireLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            throw new IllegalStateException("Missing host dimension " + dimension.identifier());
        }
        return level;
    }

    private static List<ChunkPos> square(ChunkPos center, int radius) {
        List<ChunkPos> positions = new ArrayList<>();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                positions.add(new ChunkPos(x, z));
            }
        }
        return positions;
    }

    private static void assertNoContextLeak(String phase) {
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Generation context bridge leaked after " + phase + ": "
                    + GenerationContextRegistry.bindingCount());
        }
    }

    private static void assertHash(String expected, String actual, String label) {
        if (expected == null || !expected.equals(actual)) {
            throw new SurfaceParityMismatchException(
                    label + " hash mismatch; expected=" + expected + ", actual=" + actual);
        }
    }

    private static <T> T get(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Verification interrupted", interrupted);
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Verification worker failed", cause);
        }
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record Fixture(long masterSeed, ResourceKey<Level> dimension, ChunkPos target) {
    }

    private record FixtureResult(
            String hash,
            IsolatedGenerationMetrics metrics,
            CarverTrace trace) {
    }

    private record IsolatedResult(
            CarverStageSnapshot snapshot,
            CarverGenerationRun run,
            long localSeed) {
    }

    private record ConcurrentResult(Fixture fixture, String hash) {
    }

    private record PhysicalWorldState(
            List<SurfaceStageSnapshot> chunkSnapshots,
            List<Boolean> unsavedFlags,
            List<String> poiTypes,
            int blockTicks,
            int fluidTicks,
            int loadedChunks) {

        private static PhysicalWorldState capture(
                ServerLevel level,
                List<ChunkPos> positions,
                List<BlockPos> markers) {
            List<SurfaceStageSnapshot> snapshots = new ArrayList<>();
            List<Boolean> unsaved = new ArrayList<>();
            for (ChunkPos pos : positions) {
                LevelChunk chunk = level.getChunk(pos.x(), pos.z());
                snapshots.add(SurfaceStageSnapshot.capture(chunk, level.registryAccess()));
                unsaved.add(chunk.isUnsaved());
            }
            List<String> poi = markers.stream()
                    .map(marker -> level.getPoiManager().getType(marker)
                            .flatMap(holder -> holder.unwrapKey())
                            .map(key -> key.identifier().toString())
                            .orElse("<none>"))
                    .toList();
            return new PhysicalWorldState(
                    List.copyOf(snapshots),
                    List.copyOf(unsaved),
                    poi,
                    level.getBlockTicks().count(),
                    level.getFluidTicks().count(),
                    level.getChunkSource().getLoadedChunksCount());
        }

        private void assertUnchangedBy(PhysicalWorldState actual) {
            if (chunkSnapshots.size() != actual.chunkSnapshots.size()) {
                throw new PhysicalSideEffectAssertionError("Physical snapshot size changed");
            }
            for (int index = 0; index < chunkSnapshots.size(); index++) {
                try {
                    actual.chunkSnapshots.get(index).assertEquivalentTo(chunkSnapshots.get(index));
                } catch (SurfaceParityMismatchException mismatch) {
                    throw new PhysicalSideEffectAssertionError(
                            "Physical chunk changed at index " + index + ": " + mismatch.getMessage());
                }
            }
            if (!unsavedFlags.equals(actual.unsavedFlags)
                    || !poiTypes.equals(actual.poiTypes)
                    || blockTicks != actual.blockTicks
                    || fluidTicks != actual.fluidTicks
                    || loadedChunks != actual.loadedChunks) {
                throw new PhysicalSideEffectAssertionError(
                        "Physical metadata changed during isolated CARVERS; before=" + this + ", after=" + actual);
            }
        }
    }

    private static final class VerificationStats {
        private int fixtureCount;
        private long carverNanos;
        private long setupNanos;
        private long structureStateNanos;
        private int minVirtualChunks = Integer.MAX_VALUE;
        private int maxVirtualChunks;
        private final Map<String, DimensionStats> dimensionStats = new HashMap<>();

        private void accept(Fixture fixture, FixtureResult result) {
            fixtureCount++;
            carverNanos += result.metrics().elapsedNanos();
            setupNanos += result.metrics().contextSetupNanos();
            structureStateNanos += result.metrics().structureStateNanos();
            minVirtualChunks = Math.min(minVirtualChunks, result.metrics().virtualChunkCount());
            maxVirtualChunks = Math.max(maxVirtualChunks, result.metrics().virtualChunkCount());
            dimensionStats.computeIfAbsent(
                    fixture.dimension().identifier().toString(),
                    ignored -> new DimensionStats()).accept(result.trace());
        }

        private void assertCarvingCoverage() {
            DimensionStats overworld = dimensionStats.get(Level.OVERWORLD.identifier().toString());
            DimensionStats nether = dimensionStats.get(Level.NETHER.identifier().toString());
            DimensionStats end = dimensionStats.get(Level.END.identifier().toString());
            if (overworld == null || overworld.changedFixtures < 3) {
                throw new IllegalStateException("Insufficient real Overworld carving coverage: " + overworld);
            }
            if (nether == null || nether.changedFixtures < 2) {
                throw new IllegalStateException("Insufficient real Nether carving coverage: " + nether);
            }
            if (end == null) {
                throw new IllegalStateException("Missing End CARVERS coverage");
            }
        }
    }

    private static final class DimensionStats {
        private int fixtures;
        private int changedFixtures;
        private long changedBlocks;
        private int maxConfiguredCarvers;
        private int maxMaskBits;

        private void accept(CarverTrace trace) {
            fixtures++;
            if (trace.changedBlockCount() > 0) {
                changedFixtures++;
                changedBlocks += trace.changedBlockCount();
            }
            maxConfiguredCarvers = Math.max(maxConfiguredCarvers, trace.configuredCarverCount());
            maxMaskBits = Math.max(maxMaskBits, trace.carvingMaskBitCount());
        }

        @Override
        public String toString() {
            return "{fixtures=" + fixtures
                    + ", changedFixtures=" + changedFixtures
                    + ", changedBlocks=" + changedBlocks
                    + ", maxConfiguredCarvers=" + maxConfiguredCarvers
                    + ", maxMaskBits=" + maxMaskBits
                    + '}';
        }
    }

    private static final class PhysicalSideEffectAssertionError extends AssertionError {
        private PhysicalSideEffectAssertionError(String message) {
            super(message);
        }
    }
}
