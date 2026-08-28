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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.core.BlockPos;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureOrderingPlan;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationSession;

/** Separate-process isolated consumer for Phase 2C1R evidence. */
public final class Phase2C1RootCauseVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase2c1r.isolated.";

    private Phase2C1RootCauseVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        String masterText = System.getProperty(PREFIX + "masterSeed");
        if (masterText == null) return;

        long masterSeed = Long.parseLong(masterText);
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(requireProperty("dimension")));
        ChunkPos target = new ChunkPos(
                Integer.parseInt(requireProperty("chunkX")),
                Integer.parseInt(requireProperty("chunkZ")));
        Path nativeRoot = Path.of(requireProperty("nativeEvidenceRoot")).toAbsolutePath().normalize();
        Path isolatedRoot = Path.of(requireProperty("isolatedEvidenceRoot")).toAbsolutePath().normalize();
        Path summaryOutput = Path.of(requireProperty("summaryOutput")).toAbsolutePath().normalize();

        if (MosaicWorldIdentity.isMosaicWorld(server)) {
            throw new IllegalStateException("Phase 2C1R isolated consumer requires an ordinary unrelated host world");
        }
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing isolated evidence dimension " + dimension.identifier());

        MosaicWorldProfile profile = MosaicWorldProfile.current();
        String directLocalSeed = System.getProperty(PREFIX + "directLocalSeed");
        long localSeed = directLocalSeed == null
                ? new MosaicSeedResolver(profile).resolveLocalWorldSeed(masterSeed, dimension, target)
                : Long.parseLong(directLocalSeed);
        long hostSeed = server.getWorldGenSettings().options().seed();
        boolean allowSameHostSeed = Boolean.parseBoolean(System.getProperty(PREFIX + "allowSameHostSeed", "false"));
        if (!allowSameHostSeed && hostSeed == localSeed) {
            throw new IllegalStateException("Phase 2C1R unrelated host seed unexpectedly equals local seed " + localSeed);
        }

        String frontierState = System.getProperty(PREFIX + "hostFrontierState", "empty");
        preparePhysicalFrontier(level, target, frontierState);

        FeatureOrderingPlan plan = FeatureOrderingPlan.targetLocalZxRowMajorV1(target);
        long started = System.nanoTime();
        FeatureFrontierEvidence.begin(
                FeatureFrontierEvidence.Mode.ISOLATED,
                isolatedRoot,
                dimension,
                target,
                plan.writers());
        try {
            var run = directLocalSeed == null
                    ? new IsolatedGenerationSession(profile).generateFeaturesStable(level, masterSeed, target)
                    : generateDirectFeatures(level, localSeed, target);
            FeatureStableSnapshot isolatedSnapshot = FeatureStableSnapshot.capture(
                    dimension.identifier().toString(), run.targetChunk(), level.registryAccess());
            FeatureFrontierEvidence.finish(FeatureFrontierEvidence.Mode.ISOLATED, isolatedSnapshot);

            FeatureFrontierEvidence.Divergence first = FeatureFrontierEvidence.firstDivergence(
                    nativeRoot, isolatedRoot, plan.writers().size());
            FeatureFrontierEvidence.Divergence firstStage = FeatureFrontierEvidence.firstStageDivergence(
                    nativeRoot, isolatedRoot);
            java.util.List<String> checkpointDifferences = first == null
                    ? java.util.List.of()
                    : FeatureFrontierEvidence.checkpointDifferences(
                            nativeRoot, isolatedRoot, first.writerIndex(), first.phase());
            FeatureStableSnapshot nativeSnapshot = FeatureStableSnapshot.read(
                    nativeRoot.resolve("final-feature-stable.bin.gz"));
            FeatureStageSnapshot.Diff finalDiff = nativeSnapshot.diff(isolatedSnapshot);
            String status = first == null && finalDiff.equivalent() ? "MATCH" : "DIVERGED";
            String json = "{\"status\":\"" + status
                    + "\",\"dimension\":\"" + dimension.identifier()
                    + "\",\"masterSeed\":" + masterSeed
                    + ",\"localSeed\":" + localSeed
                    + ",\"hostSeed\":" + hostSeed
                    + ",\"hostFrontierState\":\"" + escape(frontierState) + "\""
                    + ",\"chunkX\":" + target.x()
                    + ",\"chunkZ\":" + target.z()
                    + ",\"nativeHash\":\"" + nativeSnapshot.hash()
                    + "\",\"isolatedHash\":\"" + isolatedSnapshot.hash()
                    + "\",\"firstCheckpointDivergence\":\"" + escape(String.valueOf(first))
                    + "\",\"firstStageDivergence\":\"" + escape(String.valueOf(firstStage))
                    + "\",\"checkpointDifferences\":\"" + escape(checkpointDifferences.toString())
                    + "\",\"physicalLevelEscapes\":\"" + escape(run.featureTrace().physicalLevelEscapeSummary().toString())
                    + "\",\"finalFirstDifference\":\"" + escape(String.valueOf(finalDiff.firstDifference()))
                    + "\",\"finalDifferingBlocks\":" + finalDiff.differingBlocks()
                    + ",\"runtimeMs\":" + (System.nanoTime() - started) / 1_000_000L + "}";
            write(summaryOutput, json);
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Phase 2C1R isolated evidence {} hostSeed={} hostFrontierState={} localSeed={} target={} nativeHash={} isolatedHash={} firstCheckpointDivergence={} firstStageDivergence={} checkpointDifferences={} physicalLevelEscapes={} localUncachedBiomeReads={} finalFirstDifference={} differingBlocks={} evidenceRoot={}",
                    status, hostSeed, frontierState, localSeed, target, nativeSnapshot.hash(), isolatedSnapshot.hash(),
                    first, firstStage, checkpointDifferences,
                    run.featureTrace().physicalLevelEscapeSummary(),
                    run.featureTrace().localUncachedBiomeReads(),
                    finalDiff.firstDifference(), finalDiff.differingBlocks(), isolatedRoot);
        } catch (RuntimeException exception) {
            FeatureFrontierEvidence.abort(FeatureFrontierEvidence.Mode.ISOLATED);
            throw exception;
        }

        if (Boolean.parseBoolean(System.getProperty(PREFIX + "autoStop", "true"))) {
            server.execute(() -> server.halt(false));
        }
    }

    private static io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun generateDirectFeatures(
            ServerLevel level,
            long localSeed,
            ChunkPos target) {
        try (var context = io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext.create(
                io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode.VANILLA_CONTROL,
                level,
                localSeed,
                target)) {
            return context.generateFeaturesStable();
        }
    }

    private static void preparePhysicalFrontier(ServerLevel level, ChunkPos target, String state) {
        if (state.equals("empty")) {
            for (int z = target.z() - 1; z <= target.z() + 1; z++) {
                for (int x = target.x() - 1; x <= target.x() + 1; x++) {
                    if (level.getChunkSource().getChunkNow(x, z) != null) {
                        throw new IllegalStateException("Expected empty physical frontier, found loaded chunk "
                                + new ChunkPos(x, z));
                    }
                }
            }
            return;
        }
        if (!state.equals("generated") && !state.equals("mutated")) {
            throw new IllegalArgumentException("Unknown host frontier state " + state);
        }
        for (int z = target.z() - 1; z <= target.z() + 1; z++) {
            for (int x = target.x() - 1; x <= target.x() + 1; x++) {
                level.getChunk(x, z, ChunkStatus.FULL, true);
                if (state.equals("mutated")) {
                    BlockPos marker = new BlockPos((x << 4) + 8, level.getSeaLevel(), (z << 4) + 8);
                    level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    private static String requireProperty(String suffix) {
        String value = System.getProperty(PREFIX + suffix);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing Phase 2C1R isolated property " + PREFIX + suffix);
        }
        return value;
    }

    private static void write(Path output, String value) {
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 2C1R summary " + output, exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
