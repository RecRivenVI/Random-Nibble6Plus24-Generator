package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.StructureManager;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;

/** Explicit detached artifact roundtrip acceptance; inactive without its JVM property. */
public final class Phase2DVerification {

    public static final String VERIFY_PROPERTY = "randomnibble6plus24generator.phase2d.verify";
    private static final List<Class<?>> FORBIDDEN_ARTIFACT_TYPES = List.of(
            ServerLevel.class,
            WorldGenRegion.class,
            ChunkAccess.class,
            ProtoChunk.class,
            LevelChunk.class,
            GenerationChunkHolder.class,
            StaticCache2D.class,
            RandomState.class,
            StructureManager.class,
            StructureCheck.class,
            LevelLightEngine.class,
            IsolatedGenerationContext.class);

    private Phase2DVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String mode = System.getProperty(VERIFY_PROPERTY, "");
        if (mode.isBlank()) return;
        List<Fixture> fixtures = mode.equals("smoke") ? List.of(fixtures().get(0)) : fixtures();
        if (!mode.equals("smoke") && !mode.equals("full")) {
            throw new IllegalArgumentException("Unknown Phase 2D verification mode " + mode);
        }
        long started = System.nanoTime();
        long heapBefore = usedHeap();
        long peakHeap = heapBefore;
        JsonArray results = new JsonArray();
        Map<Fixture, String> semanticHashes = new java.util.HashMap<>();
        for (Fixture fixture : fixtures) {
            Result result = runFixture(server, fixture, true);
            results.add(result.json());
            semanticHashes.put(fixture, result.semanticHash());
            peakHeap = Math.max(peakHeap, usedHeap());
        }
        if (mode.equals("full")) runParallel(server, fixtures.subList(0, 5), semanticHashes);
        if (GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Context leak after Phase 2D: " + GenerationContextRegistry.bindingCount());
        }
        JsonObject summary = new JsonObject();
        summary.addProperty("status", "PASS");
        summary.addProperty("mode", mode);
        summary.addProperty("fixtures", fixtures.size());
        summary.addProperty("runtimeMs", (System.nanoTime() - started) / 1_000_000L);
        summary.addProperty("heapBeforeMiB", heapBefore / 1024L / 1024L);
        summary.addProperty("coarsePeakHeapMiB", peakHeap / 1024L / 1024L);
        summary.addProperty("heapAfterMiB", usedHeap() / 1024L / 1024L);
        summary.add("results", results);
        String output = System.getProperty("randomnibble6plus24generator.phase2d.output");
        if (output != null && !output.isBlank()) write(Path.of(output), summary.toString());
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 2D {} PASS fixtures={} runtimeMs={} heapBeforeMiB={} peakMiB={} heapAfterMiB={}",
                mode, fixtures.size(), (System.nanoTime() - started) / 1_000_000L,
                heapBefore / 1024L / 1024L, peakHeap / 1024L / 1024L, usedHeap() / 1024L / 1024L);
        if (Boolean.parseBoolean(System.getProperty("randomnibble6plus24generator.phase2d.autoStop", "false"))) {
            server.execute(() -> server.halt(false));
        }
    }

    private static Result runFixture(MinecraftServer server, Fixture fixture, boolean aliasTests) {
        ServerLevel level = server.getLevel(fixture.dimension());
        if (level == null) throw new IllegalStateException("Missing artifact fixture dimension " + fixture.dimension());
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long localSeed = new MosaicSeedResolver(profile)
                .resolveLocalWorldSeed(fixture.masterSeed(), fixture.dimension(), fixture.target());
        StructurePieceSerializationContext structureContext = StructurePieceSerializationContext.fromLevel(level);
        Phase2C1FVerification.PhysicalHostSnapshot physicalBefore =
                Phase2C1FVerification.PhysicalHostSnapshot.capture(level, fixture.target());

        CanonicalChunkArtifact artifact;
        FeatureStableSnapshot sourceSnapshot;
        CompoundTag sourceOracle;
        String artifactFingerprint;
        long captureNanos;
        long sessionHeap;
        ProtoChunk source;
        try (IsolatedGenerationContext context = IsolatedGenerationContext.create(
                IsolatedGenerationMode.ISOLATED_MOSAIC, level, localSeed, fixture.target())) {
            FeatureStableGenerationRun run = context.generateFeaturesStable();
            if (run.metrics().virtualStorageScanCount() != 0L) {
                throw new IllegalStateException("Artifact source generation read physical storage");
            }
            source = (ProtoChunk) run.targetChunk();
            sourceSnapshot = FeatureStableSnapshot.capture(
                    fixture.dimension().identifier().toString(), source, level.registryAccess());
            sourceOracle = normalizedVanillaOracle(level, source);
            long captureStarted = System.nanoTime();
            artifact = CanonicalChunkArtifact.capture(
                    run,
                    fixture.dimension().identifier().toString(),
                    localSeed,
                    profile,
                    level.registryAccess(),
                    structureContext);
            captureNanos = System.nanoTime() - captureStarted;
            artifactFingerprint = artifact.rawFingerprint();
            sessionHeap = usedHeap();
            if (aliasTests) mutateSourceAfterCapture(source);
        }

        if (aliasTests && GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Artifact requires a closed generation binding");
        }
        assertForbiddenReferences(artifact);
        byte[] callerCopy = artifact.encodedPayloadCopy();
        if (callerCopy.length > 0) callerCopy[0] ^= 0x7f;
        if (!artifactFingerprint.equals(artifact.rawFingerprint())) {
            throw new IllegalStateException("Artifact payload getter exposed mutable state");
        }
        CanonicalChunkArtifact transported = CanonicalChunkArtifact.fromDetachedTransport(
                artifact.dimension(),
                artifact.chunkPos().x(), artifact.chunkPos().z(),
                artifact.minY(), artifact.height(), artifact.localWorldSeed(),
                artifact.mosaicFormatVersion(), artifact.seedDerivationAlgorithmVersion(),
                artifact.featureOrderingAlgorithmVersion(), artifact.encodedPayloadCopy(),
                artifact.instantiatedBlockEntityPositionsCopy());

        long rehydrateStarted = System.nanoTime();
        ProtoChunk stagingA = onWorker(() -> transported.rehydrate(level.registryAccess(), structureContext));
        long rehydrateNanos = System.nanoTime() - rehydrateStarted;
        FeatureStableSnapshot stagingSnapshot = FeatureStableSnapshot.capture(
                fixture.dimension().identifier().toString(), stagingA, level.registryAccess());
        assertSnapshotEqual(sourceSnapshot, stagingSnapshot, fixture);
        assertRawTransport(sourceSnapshot, stagingSnapshot, fixture);
        CompoundTag stagingOracle = normalizedVanillaOracle(level, stagingA);
        if (!sourceOracle.equals(stagingOracle)) {
            throw new IllegalStateException(
                    "SerializableChunkData oracle mismatch for " + fixture
                            + " source=" + sourceOracle + " staging=" + stagingOracle);
        }

        if (aliasTests) stagingA.setBlockState(
                new BlockPos(stagingA.getPos().getMiddleBlockX(), stagingA.getMinY() + 1,
                        stagingA.getPos().getMiddleBlockZ()),
                Blocks.GOLD_BLOCK.defaultBlockState(), 0);
        ProtoChunk stagingB = transported.rehydrate(level.registryAccess(), structureContext);
        FeatureStableSnapshot stagingBHash = FeatureStableSnapshot.capture(
                fixture.dimension().identifier().toString(), stagingB, level.registryAccess());
        assertSnapshotEqual(sourceSnapshot, stagingBHash, fixture);
        if (!artifactFingerprint.equals(transported.rawFingerprint())) {
            throw new IllegalStateException("Rehydration mutated artifact " + fixture);
        }
        if (stagingB.getPersistedStatus() != ChunkStatus.FEATURES || stagingB.isLightCorrect()) {
            throw new IllegalStateException("Detached staging crossed light/status boundary");
        }
        if (stagingB.getSkyLightSources().getHighestLowestSourceY()
                != source.getSkyLightSources().getHighestLowestSourceY()) {
            throw new IllegalStateException("SkyLightSources pre-initialize state differs");
        }
        Phase2C1FVerification.PhysicalHostSnapshot physicalAfter =
                Phase2C1FVerification.PhysicalHostSnapshot.capture(level, fixture.target());
        if (!physicalBefore.equals(physicalAfter)) {
            throw new IllegalStateException("Artifact flow mutated physical host for " + fixture);
        }

        JsonObject json = new JsonObject();
        json.addProperty("name", fixture.name());
        json.addProperty("dimension", fixture.dimension().identifier().toString());
        json.addProperty("masterSeed", Long.toString(fixture.masterSeed()));
        json.addProperty("localSeed", Long.toString(localSeed));
        json.addProperty("chunkX", fixture.target().x());
        json.addProperty("chunkZ", fixture.target().z());
        json.addProperty("semanticHash", sourceSnapshot.hash());
        json.addProperty("rawArtifactFingerprint", artifact.rawFingerprint());
        json.addProperty("encodedBytes", artifact.encodedSize());
        json.addProperty("captureMicros", captureNanos / 1_000L);
        json.addProperty("rehydrateMicros", rehydrateNanos / 1_000L);
        json.addProperty("sessionHeapMiB", sessionHeap / 1024L / 1024L);
        json.addProperty("rawEntities", sourceSnapshot.rawEntityNbt().size());
        json.addProperty("blockEntities", sourceSnapshot.blockEntityCount());
        json.addProperty("instantiatedBlockEntities", sourceSnapshot.instantiatedBlockEntityCount());
        json.addProperty("pendingBlockEntityNbt", sourceSnapshot.pendingBlockEntityNbtCount());
        json.addProperty("blockTicks", sourceSnapshot.blockTickCount());
        json.addProperty("fluidTicks", sourceSnapshot.fluidTickCount());
        json.addProperty("postProcessing", sourceSnapshot.postProcessingCount());
        json.addProperty("lightCorrect", stagingB.isLightCorrect());
        json.addProperty("upgradeDataEmpty", stagingB.getUpgradeData().isEmpty());
        json.addProperty("blendingDataPresent", stagingB.getBlendingData() != null);
        json.addProperty("belowZeroRetrogenPresent", stagingB.getBelowZeroRetrogen() != null);
        json.addProperty("physicalStorageScans", 0);
        json.addProperty("vanillaOracle", "PASS");
        return new Result(sourceSnapshot.hash(), json);
    }

    private static void runParallel(
            MinecraftServer server,
            List<Fixture> fixtures,
            Map<Fixture, String> expected) {
        ExecutorService executor = Executors.newFixedThreadPool(fixtures.size());
        try {
            List<Future<Map.Entry<Fixture, String>>> futures = new ArrayList<>();
            for (Fixture fixture : fixtures) {
                futures.add(executor.submit(() -> Map.entry(
                        fixture, runFixture(server, fixture, false).semanticHash())));
            }
            for (Future<Map.Entry<Fixture, String>> future : futures) {
                Map.Entry<Fixture, String> result;
                try {
                    result = future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException("Parallel artifact roundtrip failed", exception);
                }
                if (!expected.get(result.getKey()).equals(result.getValue())) {
                    throw new IllegalStateException("Parallel artifact hash mismatch " + result.getKey());
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static void mutateSourceAfterCapture(ProtoChunk source) {
        source.setBlockState(
                new BlockPos(source.getPos().getMiddleBlockX(), source.getMinY() + 1,
                        source.getPos().getMiddleBlockZ()),
                Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
        if (!source.getEntities().isEmpty()) source.getEntities().get(0).putString("phase2d_mutation", "source");
        if (!source.getBlockEntityNbts().isEmpty()) {
            source.getBlockEntityNbts().values().iterator().next().putString("phase2d_mutation", "source");
        }
        var heightmaps = source.getHeightmaps().iterator();
        if (heightmaps.hasNext()) {
            var entry = heightmaps.next();
            source.setHeightmap(entry.getKey(), new long[entry.getValue().getRawData().length]);
        }
        if (!source.getAllReferences().isEmpty()) {
            source.getAllReferences().values().iterator().next().clear();
        }
    }

    private static void assertSnapshotEqual(
            FeatureStableSnapshot expected,
            FeatureStableSnapshot actual,
            Fixture fixture) {
        FeatureStageSnapshot.Diff diff = expected.diff(actual);
        if (!diff.equivalent()) {
            throw new IllegalStateException(
                    "Artifact semantic roundtrip mismatch " + fixture
                            + " first=" + diff.firstDifference()
                            + " blocks=" + diff.differingBlocks()
                            + " metadata=" + expected.deterministicMetadataDifference(actual));
        }
    }

    private static void assertRawTransport(
            FeatureStableSnapshot expected,
            FeatureStableSnapshot actual,
            Fixture fixture) {
        if (!expected.rawEntityNbt().equals(actual.rawEntityNbt())
                || !expected.blockEntityNbt().equals(actual.blockEntityNbt())
                || expected.instantiatedBlockEntityCount() != actual.instantiatedBlockEntityCount()
                || expected.pendingBlockEntityNbtCount() != actual.pendingBlockEntityNbtCount()
                || !expected.blockTickData().equals(actual.blockTickData())
                || !expected.fluidTickData().equals(actual.fluidTickData())
                || !expected.structureStartData().equals(actual.structureStartData())) {
            throw new IllegalStateException("Artifact raw transport mismatch " + fixture);
        }
    }

    private static CompoundTag normalizedVanillaOracle(ServerLevel level, ChunkAccess chunk) {
        // copyOf retains ProtoChunk entity-tag references; freeze the oracle before alias tests mutate the source.
        CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write().copy();
        tag.putLong("LastUpdate", 0L);
        tag.putLong("InhabitedTime", 0L);
        tag.remove("isLightOn");
        var sections = tag.getListOrEmpty("sections");
        for (int index = 0; index < sections.size(); index++) {
            sections.getCompound(index).ifPresent(section -> {
                section.remove("BlockLight");
                section.remove("SkyLight");
            });
        }
        List<CompoundTag> blockEntities = new ArrayList<>();
        tag.getListOrEmpty("block_entities").forEach(value ->
                value.asCompound().ifPresent(blockEntities::add));
        blockEntities.sort(java.util.Comparator
                .comparingInt((CompoundTag value) -> value.getIntOr("y", 0))
                .thenComparingInt(value -> value.getIntOr("z", 0))
                .thenComparingInt(value -> value.getIntOr("x", 0)));
        ListTag sortedBlockEntities = new ListTag();
        sortedBlockEntities.addAll(blockEntities);
        tag.put("block_entities", sortedBlockEntities);
        return tag;
    }

    private static void assertForbiddenReferences(CanonicalChunkArtifact artifact) {
        for (Field field : CanonicalChunkArtifact.class.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            for (Class<?> forbidden : FORBIDDEN_ARTIFACT_TYPES) {
                if (forbidden.isAssignableFrom(fieldType)) {
                    throw new IllegalStateException(
                            "Artifact field " + field.getName() + " retains forbidden " + forbidden.getName());
                }
            }
        }
    }

    private static <T> T onWorker(java.util.concurrent.Callable<T> operation) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(operation).get();
        } catch (Exception exception) {
            throw new IllegalStateException("Detached worker handoff failed", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<Fixture> fixtures() {
        return List.of(
                new Fixture("origin-golden", 123456789L, Level.OVERWORLD, ChunkPos.ZERO),
                new Fixture("non-origin-golden", 123456789L, Level.OVERWORLD, new ChunkPos(125, -37)),
                new Fixture("nether", -1L, Level.NETHER, new ChunkPos(10_000, -20_000)),
                new Fixture("end", 0L, Level.END, new ChunkPos(10_000, -20_000)),
                new Fixture("end-spike", 0L, Level.END, new ChunkPos(-1, -3)),
                new Fixture("mineshaft-entity", 123456789L, Level.OVERWORLD, new ChunkPos(-20_000, 30_000)),
                new Fixture("pale-moss", 123456789L, Level.OVERWORLD, new ChunkPos(-8532, -4457)),
                new Fixture("capped-processor", 1L, Level.OVERWORLD, new ChunkPos(3699, -6116)),
                new Fixture("pending-be", Long.MIN_VALUE, Level.OVERWORLD, ChunkPos.ZERO),
                new Fixture("beehive", Long.MAX_VALUE, Level.OVERWORLD, ChunkPos.ZERO),
                new Fixture("dungeon-be", Long.MIN_VALUE, Level.OVERWORLD, new ChunkPos(-1, 1)),
                new Fixture("post-heavy", 0L, Level.OVERWORLD, new ChunkPos(125, -37)),
                new Fixture("fluid-ticks", Long.MAX_VALUE, Level.NETHER, new ChunkPos(-125, 37)),
                new Fixture("lush-heavy", Long.MAX_VALUE, Level.OVERWORLD, new ChunkPos(125, -37)),
                new Fixture("vault", -987654321L, Level.OVERWORLD, new ChunkPos(1, 0)),
                new Fixture("end-chorus", Long.MAX_VALUE, Level.END, new ChunkPos(10_000, -20_000)));
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void write(Path output, String value) {
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 2D result " + output, exception);
        }
    }

    private record Fixture(String name, long masterSeed, net.minecraft.resources.ResourceKey<Level> dimension, ChunkPos target) {
    }

    private record Result(String semanticHash, JsonObject json) {
    }
}
