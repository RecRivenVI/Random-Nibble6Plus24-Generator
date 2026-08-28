package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureOrderingPlan;

/** Fresh-world, ordinary physical ChunkMap control for frozen FEATURES ordering V1. */
public final class NativeVanillaFeatureControlHarness {

    private static final String PREFIX = "randomnibble6plus24generator.phase2c1.native.";
    private static final AtomicBoolean EARLY_COMPLETED = new AtomicBoolean();

    private NativeVanillaFeatureControlHarness() {
    }

    public static void runBeforeInitialSpawnIfRequested(MinecraftServer server) {
        if (!Boolean.getBoolean(PREFIX + "runBeforeInitialSpawn")
                || System.getProperty(PREFIX + "masterSeed") == null) {
            return;
        }
        produce(server, false);
        if (!EARLY_COMPLETED.compareAndSet(false, true)) {
            throw new IllegalStateException("Early native FEATURES producer completed twice");
        }
    }

    public static void runIfRequested(MinecraftServer server) {
        String masterText = System.getProperty(PREFIX + "masterSeed");
        if (masterText == null) {
            return;
        }
        if (Boolean.getBoolean(PREFIX + "runBeforeInitialSpawn")) {
            if (!EARLY_COMPLETED.compareAndSet(true, false)) {
                throw new IllegalStateException("Early native FEATURES producer did not complete");
            }
            server.execute(() -> server.halt(false));
            return;
        }
        produce(server, true);
    }

    private static void produce(MinecraftServer server, boolean allowAutoStop) {
        String masterText = System.getProperty(PREFIX + "masterSeed");
        long masterSeed = Long.parseLong(masterText);
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(requireProperty("dimension")));
        ChunkPos target = new ChunkPos(
                Integer.parseInt(requireProperty("chunkX")),
                Integer.parseInt(requireProperty("chunkZ")));
        Path output = Path.of(requireProperty("output")).toAbsolutePath().normalize();
        String evidenceRootText = System.getProperty(PREFIX + "evidenceRoot");
        Path evidenceRoot = evidenceRootText == null || evidenceRootText.isBlank()
                ? null
                : Path.of(evidenceRootText).toAbsolutePath().normalize();
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        String directLocalSeed = System.getProperty(PREFIX + "directLocalSeed");
        long localSeed = directLocalSeed == null
                ? new MosaicSeedResolver(profile).resolveLocalWorldSeed(masterSeed, dimension, target)
                : Long.parseLong(directLocalSeed);
        long physicalSeed = server.getWorldGenSettings().options().seed();
        if (physicalSeed != localSeed) {
            throw new IllegalStateException(
                    "Native FEATURES control requires WorldOptions.seed=" + localSeed + ", found " + physicalSeed);
        }
        if (MosaicWorldIdentity.isMosaicWorld(server)) {
            throw new IllegalStateException("Native FEATURES control requires an ordinary Vanilla world");
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            throw new IllegalStateException("Missing native FEATURES dimension " + dimension.identifier());
        }

        FeatureOrderingPlan plan = FeatureOrderingPlan.targetLocalZxRowMajorV1(target);
        long started = System.nanoTime();
        NativeFeatureExecutionTrace.begin(plan.writers());
        if (evidenceRoot != null) {
            FeatureFrontierEvidence.begin(
                    FeatureFrontierEvidence.Mode.NATIVE,
                    evidenceRoot,
                    dimension,
                    target,
                    plan.writers());
        }
        try {
            for (ChunkPos writer : plan.writers()) {
                ChunkAccess writerChunk = level.getChunk(writer.x(), writer.z(), ChunkStatus.FEATURES, true);
                if (writerChunk == null || writerChunk.getPersistedStatus() != ChunkStatus.FEATURES) {
                    throw new IllegalStateException(
                            "Native canonical writer did not stop exactly at FEATURES: " + writer
                                    + ", status=" + (writerChunk == null ? "null" : writerChunk.getPersistedStatus()));
                }
            }
            ChunkAccess targetChunk = level.getChunk(target.x(), target.z(), ChunkStatus.FEATURES, false);
            long generationFinished = System.nanoTime();
            FeatureStableSnapshot nativeSnapshot = FeatureStableSnapshot.capture(
                    dimension.identifier().toString(), targetChunk, level.registryAccess());
            long snapshotFinished = System.nanoTime();
            if (evidenceRoot != null) {
                FeatureFrontierEvidence.finish(FeatureFrontierEvidence.Mode.NATIVE, nativeSnapshot);
            }
            NativeFeatureExecutionTrace.Result trace = NativeFeatureExecutionTrace.finish();
            String snapshotOutput = System.getProperty(PREFIX + "snapshotOutput");
            if (snapshotOutput != null && !snapshotOutput.isBlank()) {
                nativeSnapshot.write(Path.of(snapshotOutput));
            }
            String referenceSnapshot = System.getProperty(PREFIX + "referenceSnapshot");
            if (referenceSnapshot != null && !referenceSnapshot.isBlank()) {
                FeatureStableSnapshot reference = FeatureStableSnapshot.read(Path.of(referenceSnapshot));
                FeatureStageSnapshot.Diff referenceDiff = nativeSnapshot.diff(reference);
                RandomNibble6Plus24Generator.LOGGER.info(
                        "Native FEATURES reference diff equivalent={} first={} differingBlocks={} categories={} metadata={}",
                        referenceDiff.equivalent(), referenceDiff.firstDifference(),
                        referenceDiff.differingBlocks(), referenceDiff.blockCategories(),
                        nativeSnapshot.deterministicMetadataDifference(reference));
            }

            String json = "{\"status\":\"PASS\",\"mode\":\"native-producer\",\"dimension\":\""
                    + dimension.identifier()
                    + "\",\"masterSeed\":" + masterSeed
                    + ",\"localSeed\":" + localSeed
                    + ",\"chunkX\":" + target.x()
                    + ",\"chunkZ\":" + target.z()
                    + ",\"featureHash\":\"" + nativeSnapshot.hash()
                    + "\",\"writers\":" + plan.writers().size()
                    + ",\"requestedWriters\":\"" + escape(trace.requestedWriters().toString()) + "\""
                    + ",\"completedWriters\":\"" + escape(trace.completedWriters().toString()) + "\""
                    + ",\"maxConcurrentFeatureWriters\":" + trace.maxConcurrentFeatureWriters()
                    + ",\"decorationSeedReads\":" + trace.decorationSeedReads()
                    + ",\"featureSeedInvocationCount\":" + trace.featureSeedInvocationCount()
                    + ",\"featureSeedSequenceHash\":\""
                    + Long.toUnsignedString(trace.featureSeedSequenceHash(), 16) + "\""
                    + ",\"featureVisibleBiomeSequence\":\""
                    + escape(trace.featureVisibleBiomeSequence().toString()) + "\""
                    + ",\"blockEntities\":" + nativeSnapshot.blockEntityCount()
                    + ",\"instantiatedBlockEntities\":" + nativeSnapshot.instantiatedBlockEntityCount()
                    + ",\"pendingBlockEntityNbt\":" + nativeSnapshot.pendingBlockEntityNbtCount()
                    + ",\"blockTicks\":" + nativeSnapshot.blockTickCount()
                    + ",\"fluidTicks\":" + nativeSnapshot.fluidTickCount()
                    + ",\"postProcessing\":" + nativeSnapshot.postProcessingCount()
                    + ",\"entities\":" + nativeSnapshot.entityCount()
                    + ",\"rawEntityNbt\":\"" + escape(nativeSnapshot.rawEntityNbt().toString()) + "\""
                    + ",\"canonicalEntityNbt\":\"" + escape(nativeSnapshot.canonicalEntityNbt().toString()) + "\""
                    + ",\"blockEntityNbt\":\"" + escape(nativeSnapshot.blockEntityNbt().toString()) + "\""
                    + ",\"structureStarts\":\"" + escape(nativeSnapshot.structureStartData().toString()) + "\""
                    + ",\"evidenceRoot\":\"" + (evidenceRoot == null ? "" : evidenceRoot.toString().replace('\\', '/'))
                    + "\",\"generationRuntimeMs\":" + (generationFinished - started) / 1_000_000L
                    + ",\"snapshotCostMs\":" + (snapshotFinished - generationFinished) / 1_000_000L
                    + ",\"runtimeMs\":" + (System.nanoTime() - started) / 1_000_000L + "}";
            write(output, json);
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Native FEATURES evidence producer PASS dimension={} localSeed={} target={} hash={} writers={} evidenceRoot={} runtimeMs={}",
                    dimension.identifier(), localSeed, target, nativeSnapshot.hash(),
                    plan.writers().size(), evidenceRoot, (System.nanoTime() - started) / 1_000_000L);
        } catch (RuntimeException exception) {
            FeatureFrontierEvidence.abort(FeatureFrontierEvidence.Mode.NATIVE);
            NativeFeatureExecutionTrace.abort();
            throw exception;
        }
        if (allowAutoStop && Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
            server.execute(() -> server.halt(false));
        }
    }

    private static String requireProperty(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing native FEATURES property " + PREFIX + suffix);
        }
        return value;
    }

    private static void write(Path output, String value) {
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write native FEATURES result " + output, exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }
}
