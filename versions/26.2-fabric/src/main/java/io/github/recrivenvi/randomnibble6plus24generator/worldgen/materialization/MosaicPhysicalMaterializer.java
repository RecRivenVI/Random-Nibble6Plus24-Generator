package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMetrics;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;

/**
 * Prepares one detached canonical FEATURES result and publishes its fresh ProtoChunk only through
 * GenerationChunkHolder's normal EMPTY-result completion boundary.
 */
public final class MosaicPhysicalMaterializer {

    private static final ConcurrentHashMap<GenerationKey, ChunkStatus> REQUESTED_TARGETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GenerationKey, InFlight> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<ChunkAccess, GenerationKey> AWAITING_PUBLISH =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicLong REQUEST_COUNT = new AtomicLong();
    private static final AtomicLong DEDUP_HITS = new AtomicLong();
    private static final AtomicLong ISOLATED_GENERATIONS = new AtomicLong();
    private static final AtomicLong ARTIFACT_CAPTURES = new AtomicLong();
    private static final AtomicLong PUBLISHES = new AtomicLong();

    private static volatile Consumer<Publication> verificationObserver;
    private static volatile FaultPoint verificationFault = FaultPoint.NONE;

    private MosaicPhysicalMaterializer() {
    }

    public static boolean isPhysicalMosaic(ServerLevel level) {
        return MosaicWorldIdentity.isMosaicWorld(level) && MosaicWorldIdentity.isMosaicDimension(level);
    }

    public static void registerPhysicalRequest(ServerLevel level, ChunkStatus targetStatus, ChunkPos pos) {
        if (!isPhysicalMosaic(level) || targetStatus == ChunkStatus.EMPTY) return;
        if (targetStatus.isAfter(ChunkStatus.FEATURES)) {
            throw new IllegalStateException(
                    "Phase 3A refuses physical Mosaic status after FEATURES: " + targetStatus + " at " + pos);
        }
        GenerationKey key = key(level, pos);
        REQUESTED_TARGETS.merge(key, targetStatus, (left, right) -> right.isAfter(left) ? right : left);
    }

    public static boolean isRequestedTarget(ServerLevel level, ChunkPos pos) {
        return REQUESTED_TARGETS.containsKey(key(level, pos));
    }

    public static CompletableFuture<ChunkAccess> materializeLoadedTarget(
            ServerLevel level,
            GenerationChunkHolder holder,
            CompletableFuture<ChunkAccess> loadedFuture) {
        GenerationKey key = key(level, holder.getPos());
        if (!REQUESTED_TARGETS.containsKey(key)) return loadedFuture;
        return loadedFuture.thenCompose(loaded -> {
            if (!loaded.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
                REQUESTED_TARGETS.remove(key);
                return CompletableFuture.completedFuture(loaded);
            }
            return requestPrepared(level, holder.getPos()).thenApply(prepared -> {
                try {
                    failIf(FaultPoint.BEFORE_PHYSICAL_PUBLISH);
                    AWAITING_PUBLISH.put(prepared.chunk(), key);
                    return prepared.chunk();
                } catch (RuntimeException exception) {
                    abort(key, prepared.chunk());
                    throw exception;
                }
            });
        });
    }

    /** Returns the same in-flight future for every concurrent request of an identical generation key. */
    public static CompletableFuture<PreparedMaterialization> requestPrepared(ServerLevel level, ChunkPos pos) {
        GenerationKey key = key(level, pos);
        REQUEST_COUNT.incrementAndGet();
        InFlight existing = IN_FLIGHT.get(key);
        if (existing != null) {
            DEDUP_HITS.incrementAndGet();
            return existing.future();
        }

        CompletableFuture<PreparedMaterialization> promise = new CompletableFuture<>();
        InFlight created = new InFlight(promise);
        existing = IN_FLIGHT.putIfAbsent(key, created);
        if (existing != null) {
            DEDUP_HITS.incrementAndGet();
            return existing.future();
        }

        CompletableFuture.runAsync(() -> {
            try {
                promise.complete(prepare(level, pos, key));
            } catch (Throwable throwable) {
                promise.completeExceptionally(throwable);
            }
        }, Util.backgroundExecutor());
        promise.whenComplete((prepared, throwable) -> {
            if (throwable != null) {
                IN_FLIGHT.remove(key, created);
                REQUESTED_TARGETS.remove(key);
            }
        });
        return promise;
    }

    /** Exercises the final pre-publish gate without attaching the prepared object to a holder. */
    public static CompletableFuture<Void> exerciseBeforePublishFailureForVerification(
            ServerLevel level,
            ChunkPos pos) {
        return requestPrepared(level, pos).thenApply(prepared -> {
            try {
                failIf(FaultPoint.BEFORE_PHYSICAL_PUBLISH);
                throw new IllegalStateException("Expected an injected BEFORE_PHYSICAL_PUBLISH failure");
            } finally {
                abort(prepared.key(), prepared.chunk());
            }
        });
    }

    public static CompletableFuture<ChunkAccess> passThroughPhysicalStep(
            GenerationChunkHolder holder,
            ChunkStatus targetStatus) {
        ChunkAccess parent = holder.getChunkIfPresentUnchecked(targetStatus.getParent());
        if (parent == null) {
            throw new IllegalStateException(
                    "Mosaic physical status pass-through is missing parent "
                            + targetStatus.getParent() + " for " + holder.getPos());
        }
        return CompletableFuture.completedFuture(parent);
    }

    /** Called from GenerationChunkHolder.completeFuture, the scheduler-owned publication point. */
    public static void onHolderFutureCompleted(
            GenerationChunkHolder holder,
            ChunkStatus status,
            ChunkAccess chunk) {
        if (status != ChunkStatus.EMPTY) return;
        GenerationKey key = AWAITING_PUBLISH.remove(chunk);
        if (key == null) return;
        if (!holder.getPos().equals(key.pos())) {
            abort(key, chunk);
            throw new IllegalStateException("Prepared Mosaic Chunk published to the wrong holder");
        }
        PUBLISHES.incrementAndGet();
        InFlight flight = IN_FLIGHT.remove(key);
        REQUESTED_TARGETS.remove(key);
        if (flight == null) {
            throw new IllegalStateException("Published Mosaic Chunk had no in-flight owner: " + key);
        }
        PreparedMaterialization prepared = flight.future().join();
        Consumer<Publication> observer = verificationObserver;
        if (observer != null) observer.accept(new Publication(key, prepared, holder));
    }

    public static void validateProvenance(
            ServerLevel level,
            ChunkPos requestedPos,
            CanonicalChunkArtifact artifact) {
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Physical Mosaic materialization lacks runtime identity"));
        MosaicWorldProfile profile = runtime.profile();
        long expectedSeed = runtime.resolveLocalWorldSeed(level.dimension(), requestedPos);
        validateProvenance(level.dimension(), requestedPos, expectedSeed, profile, artifact);
    }

    static void validateProvenance(
            ResourceKey<Level> dimension,
            ChunkPos requestedPos,
            long expectedSeed,
            MosaicWorldProfile profile,
            CanonicalChunkArtifact artifact) {
        if (!artifact.dimension().equals(dimension.identifier().toString())) {
            throw new IllegalArgumentException("Artifact dimension mismatch");
        }
        if (!artifact.chunkPos().equals(requestedPos)) {
            throw new IllegalArgumentException("Artifact ChunkPos mismatch");
        }
        if (artifact.localWorldSeed() != expectedSeed) {
            throw new IllegalArgumentException("Artifact local world seed mismatch");
        }
        if (artifact.mosaicFormatVersion() != profile.formatVersion()
                || artifact.seedDerivationAlgorithmVersion() != profile.seedDerivationAlgorithmVersion()
                || artifact.featureOrderingAlgorithmVersion() != profile.featureOrderingAlgorithmVersion()) {
            throw new IllegalArgumentException("Artifact Mosaic profile version mismatch");
        }
    }

    public static void shutdown(MinecraftServer server) {
        IN_FLIGHT.forEach((key, flight) -> {
            if (key.server() == server && IN_FLIGHT.remove(key, flight)) flight.future().cancel(true);
        });
        REQUESTED_TARGETS.keySet().removeIf(key -> key.server() == server);
        synchronized (AWAITING_PUBLISH) {
            AWAITING_PUBLISH.entrySet().removeIf(entry -> entry.getValue().server() == server);
        }
    }

    public static Metrics metrics() {
        return new Metrics(
                REQUEST_COUNT.get(),
                DEDUP_HITS.get(),
                ISOLATED_GENERATIONS.get(),
                ARTIFACT_CAPTURES.get(),
                PUBLISHES.get(),
                IN_FLIGHT.size(),
                REQUESTED_TARGETS.size());
    }

    public static void resetVerificationState() {
        if (!IN_FLIGHT.isEmpty() || !AWAITING_PUBLISH.isEmpty()) {
            throw new IllegalStateException("Cannot reset Phase 3A metrics while materialization is in flight");
        }
        REQUESTED_TARGETS.clear();
        REQUEST_COUNT.set(0L);
        DEDUP_HITS.set(0L);
        ISOLATED_GENERATIONS.set(0L);
        ARTIFACT_CAPTURES.set(0L);
        PUBLISHES.set(0L);
        verificationFault = FaultPoint.NONE;
        verificationObserver = null;
    }

    public static void setVerificationObserver(Consumer<Publication> observer) {
        verificationObserver = observer;
    }

    public static void setVerificationFault(FaultPoint fault) {
        verificationFault = Objects.requireNonNull(fault, "fault");
    }

    private static PreparedMaterialization prepare(ServerLevel level, ChunkPos pos, GenerationKey key) {
        long totalStarted = System.nanoTime();
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context"));
        long localSeed = runtime.resolveLocalWorldSeed(level.dimension(), pos);
        failIf(FaultPoint.AFTER_LOCAL_SEED_RESOLVE);

        ISOLATED_GENERATIONS.incrementAndGet();
        long isolatedStarted = System.nanoTime();
        CanonicalChunkArtifact artifact;
        IsolatedGenerationMetrics isolatedMetrics;
        long artifactNanos;
        try (IsolatedGenerationContext context = IsolatedGenerationContext.create(
                IsolatedGenerationMode.ISOLATED_MOSAIC, level, localSeed, pos)) {
            FeatureStableGenerationRun run = context.generateFeaturesStable();
            isolatedMetrics = run.metrics();
            failIf(FaultPoint.AFTER_ISOLATED_STABLE_GENERATION);
            long artifactStarted = System.nanoTime();
            artifact = CanonicalChunkArtifact.capture(
                    run,
                    level.dimension().identifier().toString(),
                    localSeed,
                    runtime.profile(),
                    level.registryAccess(),
                    StructurePieceSerializationContext.fromLevel(level));
            artifactNanos = System.nanoTime() - artifactStarted;
            ARTIFACT_CAPTURES.incrementAndGet();
            failIf(FaultPoint.AFTER_ARTIFACT_CAPTURE);
        }
        long isolatedNanos = System.nanoTime() - isolatedStarted;
        failIf(FaultPoint.AFTER_SESSION_CLOSE);

        long rehydrateStarted = System.nanoTime();
        ProtoChunk fresh = artifact.rehydrate(
                level.registryAccess(), StructurePieceSerializationContext.fromLevel(level));
        long rehydrateNanos = System.nanoTime() - rehydrateStarted;
        failIf(FaultPoint.AFTER_ARTIFACT_REHYDRATE);

        validateProvenance(level, pos, artifact);
        if (!fresh.getPos().equals(pos)
                || fresh.getPersistedStatus() != ChunkStatus.FEATURES
                || fresh.isLightCorrect()) {
            throw new IllegalStateException("Rehydrated physical handoff is not a fresh pre-light FEATURES ProtoChunk");
        }
        failIf(FaultPoint.AFTER_PROVENANCE_VALIDATION);
        return new PreparedMaterialization(
                key,
                artifact,
                fresh,
                isolatedMetrics,
                isolatedNanos,
                artifactNanos,
                rehydrateNanos,
                System.nanoTime() - totalStarted);
    }

    private static GenerationKey key(ServerLevel level, ChunkPos pos) {
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context"));
        MosaicWorldProfile profile = runtime.profile();
        return new GenerationKey(
                level.getServer(),
                level.dimension(),
                pos,
                profile.formatVersion(),
                profile.seedDerivationAlgorithmVersion(),
                profile.featureOrderingAlgorithmVersion(),
                profile.presentationAlgorithmVersion());
    }

    private static void abort(GenerationKey key, ChunkAccess chunk) {
        AWAITING_PUBLISH.remove(chunk);
        IN_FLIGHT.remove(key);
        REQUESTED_TARGETS.remove(key);
    }

    private static void failIf(FaultPoint point) {
        if (verificationFault == point) {
            verificationFault = FaultPoint.NONE;
            throw new InjectedMaterializationFailure(point);
        }
    }

    private record InFlight(CompletableFuture<PreparedMaterialization> future) {
    }

    public record GenerationKey(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            ChunkPos pos,
            int formatVersion,
            int seedDerivationAlgorithmVersion,
            int featureOrderingAlgorithmVersion,
            int presentationAlgorithmVersion) {
    }

    public record PreparedMaterialization(
            GenerationKey key,
            CanonicalChunkArtifact artifact,
            ProtoChunk chunk,
            IsolatedGenerationMetrics isolatedMetrics,
            long isolatedNanos,
            long artifactNanos,
            long rehydrateNanos,
            long totalNanos) {
    }

    public record Publication(
            GenerationKey key,
            PreparedMaterialization prepared,
            GenerationChunkHolder holder) {
    }

    public record Metrics(
            long requestCount,
            long dedupHits,
            long isolatedGenerationCount,
            long artifactCaptureCount,
            long publishCount,
            int inFlightCount,
            int requestedTargetCount) {
    }

    public enum FaultPoint {
        NONE,
        AFTER_LOCAL_SEED_RESOLVE,
        AFTER_ISOLATED_STABLE_GENERATION,
        AFTER_ARTIFACT_CAPTURE,
        AFTER_SESSION_CLOSE,
        AFTER_ARTIFACT_REHYDRATE,
        AFTER_PROVENANCE_VALIDATION,
        BEFORE_PHYSICAL_PUBLISH
    }

    public static final class InjectedMaterializationFailure extends RuntimeException {
        public InjectedMaterializationFailure(FaultPoint point) {
            super("Injected Phase 3A materialization failure at " + point);
        }
    }
}
