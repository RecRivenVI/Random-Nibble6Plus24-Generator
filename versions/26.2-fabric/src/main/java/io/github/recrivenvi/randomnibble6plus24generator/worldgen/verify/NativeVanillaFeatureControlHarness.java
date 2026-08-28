package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/** Fresh-world, ordinary physical ChunkMap control for frozen FEATURES ordering V1. */
public final class NativeVanillaFeatureControlHarness {

    private static final String PREFIX = "randomnibble6plus24generator.phase2c1.native.";

    private NativeVanillaFeatureControlHarness() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String masterText = System.getProperty(PREFIX + "masterSeed");
        if (masterText == null) {
            return;
        }
        long masterSeed = Long.parseLong(masterText);
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(requireProperty("dimension")));
        ChunkPos target = new ChunkPos(
                Integer.parseInt(requireProperty("chunkX")),
                Integer.parseInt(requireProperty("chunkZ")));
        Path output = Path.of(requireProperty("output")).toAbsolutePath().normalize();
        MosaicWorldProfile profile = MosaicWorldProfile.current();
        long localSeed = new MosaicSeedResolver(profile).resolveLocalWorldSeed(masterSeed, dimension, target);
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
        for (ChunkPos writer : plan.writers()) {
            ChunkAccess writerChunk = level.getChunk(writer.x(), writer.z(), ChunkStatus.FEATURES, true);
            if (writerChunk == null || writerChunk.getPersistedStatus() != ChunkStatus.FEATURES) {
                throw new IllegalStateException(
                        "Native canonical writer did not stop exactly at FEATURES: " + writer
                                + ", status=" + (writerChunk == null ? "null" : writerChunk.getPersistedStatus()));
            }
        }
        ChunkAccess targetChunk = level.getChunk(target.x(), target.z(), ChunkStatus.FEATURES, false);
        FeatureStableSnapshot nativeSnapshot = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), targetChunk, level.registryAccess());
        String snapshotOutput = System.getProperty(PREFIX + "snapshotOutput");
        if (snapshotOutput != null && !snapshotOutput.isBlank()) {
            nativeSnapshot.write(Path.of(snapshotOutput));
        }
        String referenceSnapshot = System.getProperty(PREFIX + "referenceSnapshot");
        if (referenceSnapshot != null && !referenceSnapshot.isBlank()) {
            FeatureStageSnapshot.Diff referenceDiff = nativeSnapshot.diff(
                    FeatureStableSnapshot.read(Path.of(referenceSnapshot)));
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Native FEATURES reference diff equivalent={} first={} differingBlocks={} categories={} metadata={}",
                    referenceDiff.equivalent(), referenceDiff.firstDifference(),
                    referenceDiff.differingBlocks(), referenceDiff.blockCategories(),
                    nativeSnapshot.deterministicMetadataDifference(
                            FeatureStableSnapshot.read(Path.of(referenceSnapshot))));
        }

        var isolatedRun = new IsolatedGenerationSession(profile)
                .generateFeaturesStable(level, masterSeed, target);
        FeatureStableSnapshot isolatedSnapshot = FeatureStableSnapshot.capture(
                dimension.identifier().toString(), isolatedRun.targetChunk(), level.registryAccess());
        FeatureStageSnapshot.Diff diff = isolatedSnapshot.diff(nativeSnapshot);
        if (!diff.equivalent()) {
            throw new IllegalStateException(
                    "Isolated/native canonical FEATURES mismatch dimension=" + dimension.identifier()
                            + ", masterSeed=" + masterSeed + ", localSeed=" + localSeed
                            + ", target=" + target + ", first=" + diff.firstDifference()
                            + ", differingBlocks=" + diff.differingBlocks()
                            + ", categories=" + diff.blockCategories());
        }

        String json = "{\"status\":\"PASS\",\"dimension\":\"" + dimension.identifier()
                + "\",\"masterSeed\":" + masterSeed
                + ",\"localSeed\":" + localSeed
                + ",\"chunkX\":" + target.x()
                + ",\"chunkZ\":" + target.z()
                + ",\"featureHash\":\"" + nativeSnapshot.hash()
                + "\",\"writers\":" + plan.writers().size()
                + ",\"virtualChunks\":" + isolatedRun.metrics().virtualChunkCount()
                + ",\"blockEntities\":" + nativeSnapshot.blockEntityCount()
                + ",\"blockTicks\":" + nativeSnapshot.blockTickCount()
                + ",\"fluidTicks\":" + nativeSnapshot.fluidTickCount()
                + ",\"postProcessing\":" + nativeSnapshot.postProcessingCount()
                + ",\"entities\":" + nativeSnapshot.entityCount()
                + ",\"rawEntityNbtDiffers\":"
                + !nativeSnapshot.rawEntityNbt().equals(isolatedSnapshot.rawEntityNbt())
                + ",\"runtimeMs\":" + (System.nanoTime() - started) / 1_000_000L + "}";
        write(output, json);
        RandomNibble6Plus24Generator.LOGGER.info(
                "Native canonical FEATURES parity PASS dimension={} masterSeed={} localSeed={} target={} hash={} "
                        + "writers={} runtimeMs={} entities={} rawEntityNbtDiffers={} featureSeedInvocations={} featureSeedSequenceHash={}",
                dimension.identifier(), masterSeed, localSeed, target, nativeSnapshot.hash(),
                plan.writers().size(), (System.nanoTime() - started) / 1_000_000L,
                nativeSnapshot.entityCount(),
                !nativeSnapshot.rawEntityNbt().equals(isolatedSnapshot.rawEntityNbt()),
                isolatedRun.featureTrace().featureSeedInvocationCount(),
                Long.toUnsignedString(isolatedRun.featureTrace().featureSeedSequenceHash(), 16));
        RandomNibble6Plus24Generator.LOGGER.info(
                "Native canonical isolated feature writes {}",
                isolatedRun.featureTrace().featureWriteSummary());
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
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
}
