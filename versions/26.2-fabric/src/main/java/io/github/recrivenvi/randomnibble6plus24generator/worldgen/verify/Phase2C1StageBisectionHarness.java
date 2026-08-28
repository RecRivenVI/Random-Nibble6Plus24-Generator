package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;

/** Dedicated two-process stage bisection for one pre-FEATURES dependency chunk. */
public final class Phase2C1StageBisectionHarness {

    private static final String NATIVE_PREFIX = "randomnibble6plus24generator.phase2c1r.stage.native.";
    private static final String ISOLATED_PREFIX = "randomnibble6plus24generator.phase2c1r.stage.isolated.";
    private static final AtomicReference<Request> NATIVE = new AtomicReference<>();

    private Phase2C1StageBisectionHarness() {
    }

    public static void armNativeIfRequested(MinecraftServer server) {
        String seedText = System.getProperty(NATIVE_PREFIX + "worldSeed");
        if (seedText == null) return;
        Request request = request(NATIVE_PREFIX, Long.parseLong(seedText));
        if (server.getWorldGenSettings().options().seed() != request.worldSeed) {
            throw new IllegalStateException("Native stage world seed mismatch");
        }
        if (!NATIVE.compareAndSet(null, request)) {
            throw new IllegalStateException("Native stage bisection already armed");
        }
        FeatureFrontierEvidence.beginStageProbe(
                FeatureFrontierEvidence.Mode.NATIVE,
                request.evidenceRoot,
                request.dimension,
                request.probe);
    }

    public static void completeNativeIfRequested(MinecraftServer server) {
        Request request = NATIVE.get();
        if (request == null) return;
        try {
            ServerLevel level = requireLevel(server, request.dimension);
            var chunk = level.getChunk(request.probe.x(), request.probe.z(), ChunkStatus.CARVERS, true);
            FeatureFrontierEvidence.captureDirectStage(
                    FeatureFrontierEvidence.Mode.NATIVE,
                    level,
                    request.worldSeed,
                    ChunkStatus.CARVERS,
                    chunk);
            FeatureFrontierEvidence.finishStageProbe(FeatureFrontierEvidence.Mode.NATIVE);
            write(request.summaryOutput,
                    "{\"status\":\"PASS\",\"mode\":\"native-stage\",\"worldSeed\":"
                            + request.worldSeed + ",\"chunkX\":" + request.probe.x()
                            + ",\"chunkZ\":" + request.probe.z() + "}");
        } catch (RuntimeException exception) {
            FeatureFrontierEvidence.abort(FeatureFrontierEvidence.Mode.NATIVE);
            throw exception;
        } finally {
            NATIVE.compareAndSet(request, null);
        }
        server.execute(() -> server.halt(false));
    }

    public static void runIsolatedIfRequested(MinecraftServer server) {
        String seedText = System.getProperty(ISOLATED_PREFIX + "worldSeed");
        if (seedText == null) return;
        Request request = request(ISOLATED_PREFIX, Long.parseLong(seedText));
        ServerLevel level = requireLevel(server, request.dimension);
        FeatureFrontierEvidence.beginStageProbe(
                FeatureFrontierEvidence.Mode.ISOLATED,
                request.evidenceRoot,
                request.dimension,
                request.probe);
        try {
            var run = new VanillaCarverControl().generateCarvers(level, request.worldSeed, request.probe);
            FeatureFrontierEvidence.captureDirectStage(
                    FeatureFrontierEvidence.Mode.ISOLATED,
                    level,
                    request.worldSeed,
                    ChunkStatus.CARVERS,
                    run.targetChunk());
            FeatureFrontierEvidence.finishStageProbe(FeatureFrontierEvidence.Mode.ISOLATED);
            Path nativeEvidence = Path.of(require(ISOLATED_PREFIX, "nativeEvidenceRoot"))
                    .toAbsolutePath().normalize();
            FeatureFrontierEvidence.Divergence first = FeatureFrontierEvidence.firstStageDivergence(
                    nativeEvidence, request.evidenceRoot);
            FeatureFrontierEvidence.Divergence surfacePre = FeatureFrontierEvidence.surfacePreDivergence(
                    nativeEvidence, request.evidenceRoot);
            java.util.List<String> surfacePreDifferences = FeatureFrontierEvidence.surfacePreDifferences(
                    nativeEvidence, request.evidenceRoot);
            String detailed = first == null
                    ? "none"
                    : FeatureFrontierEvidence.stageDetailedDifference(
                            nativeEvidence,
                            request.evidenceRoot,
                            first);
            write(request.summaryOutput,
                    "{\"status\":\"" + (first == null ? "MATCH" : "DIVERGED")
                            + "\",\"mode\":\"isolated-stage\",\"worldSeed\":" + request.worldSeed
                            + ",\"hostSeed\":" + server.getWorldGenSettings().options().seed()
                            + ",\"chunkX\":" + request.probe.x() + ",\"chunkZ\":" + request.probe.z()
                            + ",\"firstStageDivergence\":\"" + escape(String.valueOf(first))
                            + "\",\"surfacePreDivergence\":\"" + escape(String.valueOf(surfacePre))
                            + "\",\"surfacePreDifferences\":\"" + escape(surfacePreDifferences.toString())
                            + "\",\"detailedDifference\":\"" + escape(detailed) + "\"}");
            RandomNibble6Plus24Generator.LOGGER.info(
                    "Phase 2C1R stage bisection worldSeed={} hostSeed={} probe={} firstDivergence={} surfacePreDivergence={} surfacePreDifferences={} detailed={}",
                    request.worldSeed, server.getWorldGenSettings().options().seed(), request.probe,
                    first, surfacePre, surfacePreDifferences, detailed);
        } catch (RuntimeException exception) {
            FeatureFrontierEvidence.abort(FeatureFrontierEvidence.Mode.ISOLATED);
            throw exception;
        }
        server.execute(() -> server.halt(false));
    }

    private static Request request(String prefix, long worldSeed) {
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, Identifier.parse(require(prefix, "dimension")));
        ChunkPos probe = new ChunkPos(
                Integer.parseInt(require(prefix, "chunkX")),
                Integer.parseInt(require(prefix, "chunkZ")));
        return new Request(
                worldSeed,
                dimension,
                probe,
                Path.of(require(prefix, "evidenceRoot")).toAbsolutePath().normalize(),
                Path.of(require(prefix, "summaryOutput")).toAbsolutePath().normalize());
    }

    private static ServerLevel requireLevel(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalStateException("Missing stage bisection dimension " + dimension.identifier());
        return level;
    }

    private static String require(String prefix, String suffix) {
        String value = System.getProperty(prefix + suffix);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + prefix + suffix);
        return value;
    }

    private static void write(Path output, String value) {
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write stage bisection result " + output, exception);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Request(
            long worldSeed,
            ResourceKey<Level> dimension,
            ChunkPos probe,
            Path evidenceRoot,
            Path summaryOutput) {
    }
}
