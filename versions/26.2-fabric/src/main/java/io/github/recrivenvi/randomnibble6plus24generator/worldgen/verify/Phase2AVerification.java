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
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMetrics;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SurfaceGenerationRun;

/** Explicit development harness; never runs unless the JVM property is set. */
public final class Phase2AVerification {

    public static final String VERIFY_PROPERTY = "randomnibble6plus24generator.phase2a.verify";

    private static final Fixture SMOKE_FIXTURE =
            new Fixture(123456789L, Level.OVERWORLD, ChunkPos.ZERO);
    private static final String SMOKE_SURFACE_GOLDEN =
            "cf8a46342321d8ec0baa089631debd3c38572b3dd8968fbbb66c76dd2e7d5b3f";

    private static final Set<ChunkStatus> EXPECTED_STAGES = Set.of(
            ChunkStatus.EMPTY,
            ChunkStatus.STRUCTURE_STARTS,
            ChunkStatus.STRUCTURE_REFERENCES,
            ChunkStatus.BIOMES,
            ChunkStatus.NOISE,
            ChunkStatus.SURFACE);

    private Phase2AVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(VERIFY_PROPERTY, "");
        if (mode.isBlank()) {
            return;
        }

        RandomNibble6Plus24Generator.LOGGER.info("Starting Phase 2A {} verification", mode);
        long started = System.nanoTime();
        long memoryBefore = usedMemory();
        if (mode.equals("smoke")) {
            IsolatedResult before = runIsolated(server, SMOKE_FIXTURE);
            runIsolated(server, new Fixture(0L, Level.OVERWORLD, ChunkPos.ZERO));
            runIsolated(server, new Fixture(1L, Level.OVERWORLD, ChunkPos.ZERO));
            runIsolated(server, new Fixture(-1L, Level.OVERWORLD, ChunkPos.ZERO));
            IsolatedResult after = runIsolated(server, SMOKE_FIXTURE);
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Phase 2A smoke direct-order hashes before={} after={}",
                    before.snapshot().hash(),
                    after.snapshot().hash());
            after.snapshot().assertEquivalentTo(before.snapshot());
            assertHash(SMOKE_SURFACE_GOLDEN, before.snapshot().hash(), "cross-process smoke golden");
            assertHash(SMOKE_SURFACE_GOLDEN, runFixture(server, SMOKE_FIXTURE).hash(), "smoke parity golden");
        } else if (mode.equals("full")) {
            runFullMatrix(server, memoryBefore);
        } else {
            throw new IllegalArgumentException("Unknown Phase 2A verification mode: " + mode);
        }
        assertNoContextLeak("verification completion");
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2A {} verification PASS in {} ms",
                mode,
                (System.nanoTime() - started) / 1_000_000L);
    }

    private static void runFullMatrix(MinecraftServer server, long memoryBefore) {
        VerificationStats stats = new VerificationStats();
        Map<Fixture, String> goldenSurfaceHashes = new HashMap<>();
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
            verifyAndRecord(server, new Fixture(seed, Level.OVERWORLD, ChunkPos.ZERO), goldenSurfaceHashes, stats);
        }

        ChunkPos[] overworldPositions = {
            new ChunkPos(1, 0),
            new ChunkPos(-1, 1),
            new ChunkPos(125, -37),
            new ChunkPos(-20_000, 30_000)
        };
        for (long seed : originSeeds) {
            for (ChunkPos pos : overworldPositions) {
                verifyAndRecord(server, new Fixture(seed, Level.OVERWORLD, pos), goldenSurfaceHashes, stats);
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
                verifyAndRecord(server, new Fixture(seed, Level.NETHER, pos), goldenSurfaceHashes, stats);
                verifyAndRecord(server, new Fixture(seed, Level.END, pos), goldenSurfaceHashes, stats);
            }
        }

        if (stats.fixtureCount != 72 || goldenSurfaceHashes.size() != 72) {
            throw new IllegalStateException("Expected exactly 72 independent parity fixtures, found "
                    + stats.fixtureCount + "/" + goldenSurfaceHashes.size());
        }
        assertHash(
                SMOKE_SURFACE_GOLDEN,
                goldenSurfaceHashes.get(SMOKE_FIXTURE),
                "72-fixture matrix smoke golden");

        List<Fixture> orderFixtures = List.of(
                SMOKE_FIXTURE,
                new Fixture(1L, Level.OVERWORLD, new ChunkPos(1, 0)),
                new Fixture(-1L, Level.OVERWORLD, new ChunkPos(-1, 1)),
                new Fixture(Long.MIN_VALUE, Level.NETHER, new ChunkPos(1, -1)),
                new Fixture(Long.MAX_VALUE, Level.END, new ChunkPos(-125, 37)));
        runOrderIndependence(server, orderFixtures, goldenSurfaceHashes);
        runConcurrentIsolation(server, orderFixtures, goldenSurfaceHashes);
        runExceptionCleanupAndWorkerReuse(server, orderFixtures.getFirst(), goldenSurfaceHashes);
        runPhysicalNeighborIsolation(server);
        assertNoContextLeak("full verification completion");

        long memoryAfter = usedMemory();
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2A matrix summary fixtures={} avgIsolatedMs={} avgControlMs={} avgSetupMs={} "
                        + "avgStructureStateMs={} virtualChunksMin={} virtualChunksMax={} memoryDeltaMiB={}",
                stats.fixtureCount,
                stats.isolatedNanos / stats.fixtureCount / 1_000_000L,
                stats.controlNanos / stats.fixtureCount / 1_000_000L,
                stats.setupNanos / (stats.fixtureCount * 2L) / 1_000_000L,
                stats.structureStateNanos / (stats.fixtureCount * 2L) / 1_000_000L,
                stats.minVirtualChunks,
                stats.maxVirtualChunks,
                (memoryAfter - memoryBefore) / (1024L * 1024L));
    }

    private static void verifyAndRecord(
            MinecraftServer server,
            Fixture fixture,
            Map<Fixture, String> goldenSurfaceHashes,
            VerificationStats stats) {
        FixtureResult result = runFixture(server, fixture);
        String previous = goldenSurfaceHashes.put(fixture, result.hash());
        if (previous != null) {
            throw new IllegalStateException("Duplicate parity fixture " + fixture);
        }
        stats.accept(result);
    }

    private static FixtureResult runFixture(MinecraftServer server, Fixture fixture) {
        ServerLevel hostLevel = requireLevel(server, fixture.dimension());
        MosaicWorldProfile profile = MosaicWorldProfile.current();
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
            throw new IllegalStateException("Selected non-origin fixture did not exercise a derived seed: " + fixture);
        }

        SurfaceGenerationRun isolatedRun = new IsolatedGenerationSession(profile)
                .generateSurface(hostLevel, fixture.masterSeed(), fixture.target());
        SurfaceGenerationRun controlRun = new VanillaSurfaceControl()
                .generateSurface(hostLevel, localSeed, fixture.target());
        SurfaceStageSnapshot isolated = SurfaceStageSnapshot.capture(
                isolatedRun.targetChunk(),
                hostLevel.registryAccess());
        SurfaceStageSnapshot control = SurfaceStageSnapshot.capture(
                controlRun.targetChunk(),
                hostLevel.registryAccess());
        try {
            isolated.assertEquivalentTo(control);
        } catch (SurfaceParityMismatchException mismatch) {
            throw new SurfaceParityMismatchException(
                    "stage=SURFACE, dimension=" + fixture.dimension().identifier()
                            + ", masterSeed=" + fixture.masterSeed()
                            + ", localWorldSeed=" + localSeed
                            + ", chunkPos=" + fixture.target()
                            + ", firstDivergence=" + mismatch.getMessage());
        }
        assertStageBoundary(isolatedRun);
        assertStageBoundary(controlRun);
        assertNoPhysicalChunkLoads(isolatedRun);
        assertNoPhysicalChunkLoads(controlRun);

        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2A parity PASS dimension={} masterSeed={} localSeed={} chunk={} hash={} "
                        + "isolatedMs={} controlMs={} virtualChunks={} virtualStorageScans={}",
                fixture.dimension().identifier(),
                fixture.masterSeed(),
                localSeed,
                fixture.target(),
                isolated.hash(),
                isolatedRun.metrics().elapsedNanos() / 1_000_000L,
                controlRun.metrics().elapsedNanos() / 1_000_000L,
                isolatedRun.metrics().virtualChunkCount(),
                isolatedRun.metrics().virtualStorageScanCount());
        return new FixtureResult(isolated.hash(), isolatedRun.metrics(), controlRun.metrics());
    }

    private static void runOrderIndependence(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expectedHashes) {
        assertSequence(server, fixtures, expectedHashes, "forward");

        List<Fixture> reverse = new ArrayList<>(fixtures);
        Collections.reverse(reverse);
        assertSequence(server, reverse, expectedHashes, "reverse");

        List<Fixture> random = new ArrayList<>(fixtures);
        Collections.shuffle(random, new Random(0x26_2A_5EEDL));
        assertSequence(server, random, expectedHashes, "random");
        assertNoContextLeak("order-independence runs");
        RandomNibble6Plus24Generator.LOGGER.info("Phase 2A forward/reverse/random order independence PASS");
    }

    private static void assertSequence(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expectedHashes,
            String label) {
        for (Fixture fixture : fixtures) {
            String actual = runIsolated(server, fixture).snapshot().hash();
            assertHash(expectedHashes.get(fixture), actual, label + " " + fixture);
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
        assertNoContextLeak("parallel runs");
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2A concurrent session isolation PASS sessions={}",
                fixtures.size());
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
                throw new IllegalStateException("Injected invalid-position session did not fail");
            }
            assertNoContextLeak("exception path");

            Future<String> reuse = worker.submit(() -> runIsolated(server, validFixture).snapshot().hash());
            assertHash(expectedHashes.get(validFixture), get(reuse), "worker reuse after failure");
        } finally {
            worker.shutdownNow();
        }
        assertNoContextLeak("worker reuse");
        RandomNibble6Plus24Generator.LOGGER.info("Phase 2A exception cleanup and worker reuse PASS");
    }

    private static void runPhysicalNeighborIsolation(MinecraftServer server) {
        ServerLevel level = requireLevel(server, Level.OVERWORLD);
        Fixture fixture = new Fixture(0x6A09E667F3BCC909L, Level.OVERWORLD, new ChunkPos(50_000, -50_000));

        IsolatedResult absentNeighbors = runIsolated(server, fixture);
        int afterAbsentRun = level.getChunkSource().getLoadedChunksCount();

        List<ChunkPos> physicalPositions = square(fixture.target(), 1);
        List<ChunkPos> shuffledLoadOrder = new ArrayList<>(physicalPositions);
        Collections.shuffle(shuffledLoadOrder, new Random(0x504859534943414CL));
        for (ChunkPos pos : shuffledLoadOrder) {
            level.getChunk(pos.x(), pos.z(), ChunkStatus.FULL, true);
        }
        IsolatedResult generatedNeighbors = runIsolated(server, fixture);
        assertHash(absentNeighbors.snapshot().hash(), generatedNeighbors.snapshot().hash(),
                "physical neighbors absent versus generated");

        List<BlockPos> markers = new ArrayList<>();
        for (int index = 0; index < physicalPositions.size(); index++) {
            ChunkPos pos = physicalPositions.get(index);
            BlockPos marker = new BlockPos(
                    pos.getMinBlockX() + 8,
                    level.getMinY() + 80 + index,
                    pos.getMinBlockZ() + 8);
            markers.add(marker);
            level.setBlock(
                    marker,
                    index % 2 == 0 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.SMITHING_TABLE.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
        }

        PhysicalWorldState before = PhysicalWorldState.capture(level, physicalPositions, markers);
        IsolatedResult modifiedNeighbors = runIsolated(server, fixture);
        PhysicalWorldState after = PhysicalWorldState.capture(level, physicalPositions, markers);
        assertHash(absentNeighbors.snapshot().hash(), modifiedNeighbors.snapshot().hash(),
                "physical neighbors absent versus artificially modified");
        before.assertUnchangedBy(after);
        if (level.getChunkSource().getLoadedChunksCount() < afterAbsentRun) {
            throw new PhysicalSideEffectAssertionError("Physical loaded chunk count unexpectedly decreased");
        }
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2A physical isolation PASS target={} states=[absent,generated,different-terrain,modified] "
                        + "loadOrder=deterministically-shuffled physicalChunksChecked={} blockTicks={} fluidTicks={}",
                fixture.target(),
                physicalPositions.size(),
                after.blockTicks(),
                after.fluidTicks());
    }

    private static IsolatedResult runIsolated(MinecraftServer server, Fixture fixture) {
        ServerLevel hostLevel = requireLevel(server, fixture.dimension());
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long localSeed = new MosaicSeedResolver(profile).resolveLocalWorldSeed(
                fixture.masterSeed(),
                fixture.dimension(),
                fixture.target());
        SurfaceGenerationRun run = new IsolatedGenerationSession(profile)
                .generateSurface(hostLevel, fixture.masterSeed(), fixture.target());
        assertStageBoundary(run);
        assertNoPhysicalChunkLoads(run);
        return new IsolatedResult(
                SurfaceStageSnapshot.capture(run.targetChunk(), hostLevel.registryAccess()),
                run,
                localSeed);
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

    private static void assertStageBoundary(SurfaceGenerationRun run) {
        if (!run.executedStages().equals(EXPECTED_STAGES)) {
            throw new IllegalStateException("Unexpected generation stages: " + run.executedStages());
        }
        if (run.targetChunk().getPersistedStatus() != ChunkStatus.SURFACE) {
            throw new IllegalStateException("Target crossed or missed SURFACE: "
                    + run.targetChunk().getPersistedStatus());
        }
    }

    private static void assertNoPhysicalChunkLoads(SurfaceGenerationRun run) {
        if (run.metrics().physicalLoadedChunksBefore() != run.metrics().physicalLoadedChunksAfter()) {
            throw new PhysicalSideEffectAssertionError("Physical loaded chunk count changed during session: "
                    + run.metrics());
        }
    }

    private static void assertNoContextLeak(String phase) {
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException(
                    "Generation context bridge leaked bindings after " + phase + ": "
                            + GenerationContextRegistry.bindingCount());
        }
    }

    private static void assertHash(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
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
            IsolatedGenerationMetrics isolatedMetrics,
            IsolatedGenerationMetrics controlMetrics) {
    }

    private record IsolatedResult(
            SurfaceStageSnapshot snapshot,
            SurfaceGenerationRun run,
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
                            "Physical chunk changed at snapshot index " + index + ": " + mismatch.getMessage());
                }
            }
            if (!unsavedFlags.equals(actual.unsavedFlags)) {
                throw new PhysicalSideEffectAssertionError(
                        "Physical dirty flags changed; before=" + unsavedFlags + ", after=" + actual.unsavedFlags);
            }
            if (!poiTypes.equals(actual.poiTypes)) {
                throw new PhysicalSideEffectAssertionError(
                        "Physical POI state changed; before=" + poiTypes + ", after=" + actual.poiTypes);
            }
            if (blockTicks != actual.blockTicks || fluidTicks != actual.fluidTicks) {
                throw new PhysicalSideEffectAssertionError(
                        "Physical scheduled ticks changed; block=" + blockTicks + "->" + actual.blockTicks
                                + ", fluid=" + fluidTicks + "->" + actual.fluidTicks);
            }
            if (loadedChunks != actual.loadedChunks) {
                throw new PhysicalSideEffectAssertionError(
                        "Physical loaded chunk count changed; before=" + loadedChunks + ", after=" + actual.loadedChunks);
            }
        }
    }

    private static final class VerificationStats {
        private int fixtureCount;
        private long isolatedNanos;
        private long controlNanos;
        private long setupNanos;
        private long structureStateNanos;
        private int minVirtualChunks = Integer.MAX_VALUE;
        private int maxVirtualChunks;

        private void accept(FixtureResult result) {
            fixtureCount++;
            isolatedNanos += result.isolatedMetrics().elapsedNanos();
            controlNanos += result.controlMetrics().elapsedNanos();
            setupNanos += result.isolatedMetrics().contextSetupNanos()
                    + result.controlMetrics().contextSetupNanos();
            structureStateNanos += result.isolatedMetrics().structureStateNanos()
                    + result.controlMetrics().structureStateNanos();
            minVirtualChunks = Math.min(
                    minVirtualChunks,
                    Math.min(
                            result.isolatedMetrics().virtualChunkCount(),
                            result.controlMetrics().virtualChunkCount()));
            maxVirtualChunks = Math.max(
                    maxVirtualChunks,
                    Math.max(
                            result.isolatedMetrics().virtualChunkCount(),
                            result.controlMetrics().virtualChunkCount()));
        }
    }

    private static final class PhysicalSideEffectAssertionError extends AssertionError {
        private PhysicalSideEffectAssertionError(String message) {
            super(message);
        }
    }
}
