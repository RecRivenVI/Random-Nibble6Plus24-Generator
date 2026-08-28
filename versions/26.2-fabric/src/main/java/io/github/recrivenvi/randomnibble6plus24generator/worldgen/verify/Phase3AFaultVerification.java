package io.github.recrivenvi.randomnibble6plus24generator.worldgen.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import io.github.recrivenvi.randomnibble6plus24generator.RandomNibble6Plus24Generator;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.MosaicPhysicalMaterializer;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.GenerationContextRegistry;

/** Verifies every pre-publish fault leaves no physical or in-flight state, followed by a deterministic retry. */
public final class Phase3AFaultVerification {

    private static final String PREFIX = "randomnibble6plus24generator.phase3a.fault.";

    private Phase3AFaultVerification() {
    }

    public static void runIfRequested(MinecraftServer server) {
        if (System.getProperty(PREFIX + "verify", "").isBlank()) return;
        ServerLevel level = server.overworld();
        ChunkPos target = new ChunkPos(
                Integer.parseInt(System.getProperty(PREFIX + "chunkX", "125")),
                Integer.parseInt(System.getProperty(PREFIX + "chunkZ", "-37")));
        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        JsonArray faults = new JsonArray();
        for (MosaicPhysicalMaterializer.FaultPoint point : MosaicPhysicalMaterializer.FaultPoint.values()) {
            if (point == MosaicPhysicalMaterializer.FaultPoint.NONE) continue;
            MosaicPhysicalMaterializer.resetVerificationState();
            MosaicPhysicalMaterializer.setVerificationFault(point);
            CompletableFuture<?> future = point == MosaicPhysicalMaterializer.FaultPoint.BEFORE_PHYSICAL_PUBLISH
                    ? MosaicPhysicalMaterializer.exerciseBeforePublishFailureForVerification(level, target)
                    : MosaicPhysicalMaterializer.requestPrepared(level, target);
            server.managedBlock(future::isDone);
            try {
                future.join();
                throw new IllegalStateException("Fault point did not fail: " + point);
            } catch (CompletionException exception) {
                Throwable cause = rootCause(exception);
                if (!(cause instanceof MosaicPhysicalMaterializer.InjectedMaterializationFailure)) {
                    throw new IllegalStateException("Unexpected Phase 3A fault result at " + point, exception);
                }
            }
            MosaicPhysicalMaterializer.Metrics metrics = MosaicPhysicalMaterializer.metrics();
            if (metrics.inFlightCount() != 0
                    || metrics.requestedTargetCount() != 0
                    || metrics.publishCount() != 0
                    || GenerationContextRegistry.bindingCount() != 0
                    || level.getChunkSource().getLoadedChunksCount() != loadedBefore
                    || level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(
                            ChunkPos.pack(target.x(), target.z())) != null) {
                throw new IllegalStateException("Fault leaked physical/in-flight state at " + point + ": " + metrics);
            }
            faults.add(point.name());
        }

        MosaicPhysicalMaterializer.resetVerificationState();
        AtomicReference<MosaicPhysicalMaterializer.Publication> publication = new AtomicReference<>();
        MosaicPhysicalMaterializer.setVerificationObserver(value -> publication.compareAndSet(null, value));
        CompletableFuture<ChunkResult<ChunkAccess>> retry = level.getChunkSource().getChunkFuture(
                target.x(), target.z(), ChunkStatus.FEATURES, true);
        server.managedBlock(retry::isDone);
        ChunkAccess physical = retry.join().orElse(null);
        if (physical == null || publication.get() == null
                || physical.getPersistedStatus() != ChunkStatus.FEATURES
                || physical.isLightCorrect()) {
            throw new IllegalStateException("Phase 3A retry did not publish a complete FEATURES ProtoChunk");
        }
        FeatureStableSnapshot snapshot = FeatureStableSnapshot.capture(
                Level.OVERWORLD.identifier().toString(), physical, level.registryAccess());
        String expected = System.getProperty(PREFIX + "expectedHash", "");
        if (!expected.isBlank() && !expected.equals(snapshot.hash())) {
            throw new IllegalStateException("Fault retry canonical hash mismatch");
        }
        MosaicPhysicalMaterializer.Metrics retryMetrics = MosaicPhysicalMaterializer.metrics();
        if (retryMetrics.isolatedGenerationCount() != 1
                || retryMetrics.artifactCaptureCount() != 1
                || retryMetrics.publishCount() != 1
                || retryMetrics.inFlightCount() != 0
                || GenerationContextRegistry.bindingCount() != 0) {
            throw new IllegalStateException("Fault retry metrics invalid " + retryMetrics);
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "PASS");
        result.add("faultPoints", faults);
        result.addProperty("faults", faults.size());
        result.addProperty("physicalLoadedChunksBeforeFaults", loadedBefore);
        result.addProperty("retryHash", snapshot.hash());
        result.addProperty("retryPublishes", retryMetrics.publishCount());
        write(result);
        Phase3APhysicalMaterializationVerification.markCompleted();
        RandomNibble6Plus24Generator.LOGGER.info(
                "Phase 3A fault/retry PASS faults={} retryHash={}", faults.size(), snapshot.hash());
        server.halt(false);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static void write(JsonObject result) {
        Path output = Path.of(System.getProperty(PREFIX + "output"));
        try {
            Files.createDirectories(output.toAbsolutePath().normalize().getParent());
            Files.writeString(output, result.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write Phase 3A fault result", exception);
        }
    }
}
