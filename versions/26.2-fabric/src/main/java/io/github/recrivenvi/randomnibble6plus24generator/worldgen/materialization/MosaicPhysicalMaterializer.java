package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.GeneratingChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Util;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.artifact.CanonicalChunkArtifact;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.FeatureStableGenerationRun;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMetrics;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;

/**
 * Prepares one detached canonical FEATURES result and publishes its fresh ProtoChunk only through
 * GenerationChunkHolder's normal EMPTY-result completion boundary.
 */
public final class MosaicPhysicalMaterializer {

    private static final ConcurrentHashMap<GenerationKey, ChunkStatus> REQUESTED_PHYSICAL_STATUSES =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GenerationKey, ChunkStatus> MATERIALIZATION_OBLIGATIONS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GenerationKey, ChunkStatus> PHYSICAL_STATUS_ALLOWANCES =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GenerationKey, Map<GenerationKey, ChunkStatus>> ACTIVE_PHYSICAL_PLANS =
            new ConcurrentHashMap<>();
    private static final Object PLAN_LOCK = new Object();
    private static final Map<GeneratingChunkMap, ServerLevel> PHYSICAL_CHUNK_MAPS =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final ConcurrentHashMap<GenerationKey, InFlight> IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<ChunkAccess, GenerationKey> AWAITING_PUBLISH =
            java.util.Collections.synchronizedMap(new IdentityHashMap<>());
    private static final AtomicLong REQUEST_COUNT = new AtomicLong();
    private static final AtomicLong DEDUP_HITS = new AtomicLong();
    private static final AtomicLong ISOLATED_GENERATIONS = new AtomicLong();
    private static final AtomicLong ARTIFACT_CAPTURES = new AtomicLong();
    private static final AtomicLong PUBLISHES = new AtomicLong();
    private static final AtomicLong PLAN_COUNT = new AtomicLong();
    private static final ConcurrentHashMap<GenerationKey, AtomicLong> ARTIFACT_CAPTURES_BY_KEY =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<GenerationKey, AtomicLong> PUBLISHES_BY_KEY =
            new ConcurrentHashMap<>();

    private static volatile Consumer<Publication> verificationObserver;
    private static volatile FaultPoint verificationFault = FaultPoint.NONE;

    private MosaicPhysicalMaterializer() {
    }

    public static boolean isPhysicalMosaic(ServerLevel level) {
        return MosaicWorldIdentity.isMosaicWorld(level) && MosaicWorldIdentity.isMosaicDimension(level);
    }

    public static void registerPhysicalRequest(ServerLevel level, ChunkStatus targetStatus, ChunkPos pos) {
        if (!isPhysicalMosaic(level) || targetStatus == ChunkStatus.EMPTY) return;
        var lifecycle = lifecycle(level);
        synchronized (lifecycle) {
            if (lifecycle.activate()) registerOpenPhysicalRequest(level, targetStatus, pos);
        }
    }

    private static void registerOpenPhysicalRequest(ServerLevel level, ChunkStatus targetStatus, ChunkPos pos) {
        if (targetStatus.isAfter(ChunkStatus.FULL)) {
            throw new IllegalStateException(
                    "Phase 3C1 refuses physical Mosaic status after FULL: " + targetStatus + " at " + pos);
        }
        GenerationKey key = key(level, pos);
        PHYSICAL_CHUNK_MAPS.put(level.getChunkSource().chunkMap, level);
        ChunkStatus effectiveStatus = REQUESTED_PHYSICAL_STATUSES.merge(
                key, targetStatus, (left, right) -> right.isAfter(left) ? right : left);
        MosaicPhysicalGenerationPlan plan = MosaicPhysicalGenerationPlan.derive(pos, effectiveStatus);
        PLAN_COUNT.incrementAndGet();
        plan.materializationObligations().forEach((obligationPos, requiredStatus) -> {
            GenerationKey obligationKey = key(level, obligationPos);
            GenerationChunkHolder existing = level.getChunkSource().chunkMap
                    .getUpdatingChunkIfPresent(ChunkPos.pack(obligationPos.x(), obligationPos.z()));
            ChunkStatus existingStatus = existing == null ? null : existing.getPersistedStatus();
            if (existingStatus != null && !existingStatus.isBefore(ChunkStatus.FEATURES)) {
                MATERIALIZATION_OBLIGATIONS.remove(obligationKey);
            } else {
                MATERIALIZATION_OBLIGATIONS.merge(
                        obligationKey,
                        requiredStatus,
                        (left, right) -> right.isAfter(left) ? right : left);
            }
        });
        Map<GenerationKey, ChunkStatus> keyedRequirements = new java.util.LinkedHashMap<>();
        plan.physicalStatusRequirements().forEach((requiredPos, requiredStatus) ->
                keyedRequirements.put(key(level, requiredPos), requiredStatus));
        synchronized (PLAN_LOCK) {
            ACTIVE_PHYSICAL_PLANS.put(key, Map.copyOf(keyedRequirements));
            keyedRequirements.forEach((requiredKey, requiredStatus) ->
                    PHYSICAL_STATUS_ALLOWANCES.merge(
                            requiredKey,
                            requiredStatus,
                            (left, right) -> right.isAfter(left) ? right : left));
        }
        PhysicalMosaicTrace.recordPlan(level, plan);
    }

    public static boolean hasMaterializationObligation(ServerLevel level, ChunkPos pos) {
        return MATERIALIZATION_OBLIGATIONS.containsKey(key(level, pos));
    }

    /** Bounded diagnostics for the physical handoff harness; not used by production generation. */
    public static String describePhysicalState(ServerLevel level, ChunkPos pos) {
        GenerationKey generationKey = key(level, pos);
        GenerationChunkHolder holder = level.getChunkSource().chunkMap
                .getUpdatingChunkIfPresent(ChunkPos.pack(pos.x(), pos.z()));
        return "holder=" + (holder == null ? "null" : holder.getPersistedStatus())
                + ",obligation=" + MATERIALIZATION_OBLIGATIONS.get(generationKey)
                + ",allowance=" + PHYSICAL_STATUS_ALLOWANCES.get(generationKey)
                + ",requested=" + REQUESTED_PHYSICAL_STATUSES.get(generationKey);
    }

    public static ChunkStatus physicalStepAllowance(
            GeneratingChunkMap chunkMap, ChunkPos pos) {
        ServerLevel level = PHYSICAL_CHUNK_MAPS.get(chunkMap);
        if (level == null || lifecycle(level).closing() || !isPhysicalMosaic(level)) return null;
        return PHYSICAL_STATUS_ALLOWANCES.get(key(level, pos));
    }

    public static CompletableFuture<ChunkAccess> materializeLoadedTarget(
            ServerLevel level,
            GenerationChunkHolder holder,
            CompletableFuture<ChunkAccess> loadedFuture) {
        GenerationKey key = key(level, holder.getPos());
        if (!MATERIALIZATION_OBLIGATIONS.containsKey(key)) return loadedFuture;
        return loadedFuture.thenCompose(loaded -> {
            if (!loaded.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
                MATERIALIZATION_OBLIGATIONS.remove(key);
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
        MosaicGenerationLifecycle lifecycle = lifecycle(level);
        if (lifecycle.active() && lifecycle.closing()) {
            return CompletableFuture.failedFuture(lifecycle.cancellation());
        }
        GenerationKey key = key(level, pos);
        REQUEST_COUNT.incrementAndGet();
        CompletableFuture<PreparedMaterialization> promise = new CompletableFuture<>();
        InFlight created = new InFlight(promise);
        synchronized (lifecycle) {
            if (!lifecycle.activate()) return CompletableFuture.failedFuture(lifecycle.cancellation());
            InFlight existing = IN_FLIGHT.putIfAbsent(key, created);
            if (existing != null) {
                DEDUP_HITS.incrementAndGet();
                return existing.future();
            }
            lifecycle.workerAccepted();
        }

        promise.whenComplete((prepared, throwable) -> {
            if (throwable != null) {
                IN_FLIGHT.remove(key, created);
                MATERIALIZATION_OBLIGATIONS.remove(key);
                clearPlansContaining(key);
            }
        });
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    promise.complete(prepare(level, pos, key));
                } catch (Throwable throwable) {
                    if (!promise.completeExceptionally(throwable) && !lifecycle.isExpected(throwable)) {
                        // An unrelated caller may have cancelled the result first. A real worker
                        // failure must not disappear just because its result future was already done.
                        net.minecraft.util.thread.BlockableEventLoop.relayDelayCrash(
                                net.minecraft.CrashReport.forThrowable(throwable, "Exception Mosaic generation worker"));
                    }
                } finally {
                    lifecycle.workerFinished();
                }
            }, Util.backgroundExecutor());
        } catch (RuntimeException failure) {
            promise.completeExceptionally(failure);
            lifecycle.workerFinished();
        }
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
            ServerLevel level,
            GenerationChunkHolder holder,
            ChunkStatus targetStatus) {
        ChunkAccess parent = holder.getChunkIfPresentUnchecked(targetStatus.getParent());
        if (parent == null) {
            throw new IllegalStateException(
                    "Mosaic physical status pass-through is missing parent "
                            + targetStatus.getParent() + " for " + holder.getPos());
        }
        return onPhysicalStepFuture(
                level, holder, targetStatus, CompletableFuture.completedFuture(parent));
    }

    /**
     * Repairs the race where the ordinary scheduler has already completed a
     * holder's EMPTY/pre-light future before its Mosaic physical plan is
     * registered.  The canonical Artifact is then supplied directly as the
     * input to the requested Vanilla derived-state step, so the holder still
     * advances through the normal ChunkStatus future chain without ever
     * running seed-dependent worldgen on the physical master seed.
     */
    public static CompletableFuture<ChunkAccess> materializePhysicalStep(
            ServerLevel level,
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            WorldGenContext worldGenContext) {
        ChunkStatus targetStatus = step.targetStatus();
        GenerationKey key = key(level, holder.getPos());
        CompletableFuture<PreparedMaterialization> preparedFuture = requestPrepared(level, holder.getPos());
        CompletableFuture<ChunkAccess> stage = preparedFuture.thenCompose(prepared -> {
            MATERIALIZATION_OBLIGATIONS.remove(key);
            return step.apply(worldGenContext, cache, prepared.chunk()).thenApply(result -> {
                MosaicStructureOverlayStore.publishCanonical(
                        level, key.pos(), prepared.artifact().localWorldSeed(),
                        prepared.chunk(), prepared.externalStructureStarts());
                return result;
            });
        });
        return stage.thenCompose(result -> onPhysicalStepFuture(
                level, holder, targetStatus, CompletableFuture.completedFuture(result)))
                .whenComplete((result, throwable) -> {
                    InFlight flight = IN_FLIGHT.get(key);
                    if (flight != null && flight.future() == preparedFuture) {
                        IN_FLIGHT.remove(key, flight);
                    }
                });
    }

    public static CompletableFuture<ChunkAccess> onPhysicalStepFuture(
            ServerLevel level,
            GenerationChunkHolder holder,
            ChunkStatus targetStatus,
            CompletableFuture<ChunkAccess> future) {
        GenerationKey key = key(level, holder.getPos());
        return future.thenApply(chunk -> {
            ChunkStatus requested = REQUESTED_PHYSICAL_STATUSES.get(key);
            if (requested != null && targetStatus.isOrAfter(requested)) {
                completePhysicalRequest(key);
            }
            ChunkStatus allowance = PHYSICAL_STATUS_ALLOWANCES.get(key);
            if (allowance != null && targetStatus.isOrAfter(allowance)) {
                PHYSICAL_STATUS_ALLOWANCES.remove(key, allowance);
            }
            PhysicalMosaicTrace.onPhysicalStepCompleted(level, targetStatus, chunk);
            return chunk;
        }).whenComplete((chunk, throwable) -> {
            if (throwable != null) clearPlansContaining(key);
        });
    }

    public static void clearPhysicalRequest(ServerLevel level, ChunkPos pos) {
        completePhysicalRequest(key(level, pos));
    }

    private static void completePhysicalRequest(GenerationKey key) {
        REQUESTED_PHYSICAL_STATUSES.remove(key);
        synchronized (PLAN_LOCK) {
            Map<GenerationKey, ChunkStatus> removed = ACTIVE_PHYSICAL_PLANS.remove(key);
            if (removed == null) {
                removeObligationIfUnused(key);
                PHYSICAL_STATUS_ALLOWANCES.remove(key);
                return;
            }
            for (GenerationKey affected : removed.keySet()) {
                removeObligationIfUnused(affected);
                ChunkStatus remaining = null;
                for (Map<GenerationKey, ChunkStatus> active : ACTIVE_PHYSICAL_PLANS.values()) {
                    ChunkStatus candidate = active.get(affected);
                    if (candidate != null && (remaining == null || candidate.isAfter(remaining))) {
                        remaining = candidate;
                    }
                }
                if (remaining == null) PHYSICAL_STATUS_ALLOWANCES.remove(affected);
                else PHYSICAL_STATUS_ALLOWANCES.put(affected, remaining);
            }
        }
    }

    private static void removeObligationIfUnused(GenerationKey obligation) {
        boolean stillRequired = ACTIVE_PHYSICAL_PLANS.values().stream()
                .anyMatch(plan -> plan.containsKey(obligation));
        if (!stillRequired) MATERIALIZATION_OBLIGATIONS.remove(obligation);
    }

    private static void clearPlansContaining(ServerLevel level, ChunkPos failedPos) {
        clearPlansContaining(key(level, failedPos));
    }

    private static void clearPlansContaining(GenerationKey failedKey) {
        java.util.List<GenerationKey> owners;
        synchronized (PLAN_LOCK) {
            owners = ACTIVE_PHYSICAL_PLANS.entrySet().stream()
                    .filter(entry -> entry.getValue().containsKey(failedKey))
                    .map(Map.Entry::getKey)
                    .toList();
        }
        if (owners.isEmpty()) {
            MATERIALIZATION_OBLIGATIONS.remove(failedKey);
            PHYSICAL_STATUS_ALLOWANCES.remove(failedKey);
        } else {
            owners.forEach(MosaicPhysicalMaterializer::completePhysicalRequest);
        }
    }

    /** Called from GenerationChunkHolder.completeFuture, the scheduler-owned publication point. */
    public static void onHolderFutureCompleted(
            GenerationChunkHolder holder,
            ChunkStatus status,
            ChunkAccess chunk) {
        if (status == ChunkStatus.EMPTY) {
            GenerationKey key = AWAITING_PUBLISH.remove(chunk);
            if (key != null) {
                if (!holder.getPos().equals(key.pos())) {
                    abort(key, chunk);
                    throw new IllegalStateException("Prepared Mosaic Chunk published to the wrong holder");
                }
                PUBLISHES.incrementAndGet();
                PUBLISHES_BY_KEY.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
                InFlight flight = IN_FLIGHT.remove(key);
                MATERIALIZATION_OBLIGATIONS.remove(key);
                if (flight == null) {
                    throw new IllegalStateException("Published Mosaic Chunk had no in-flight owner: " + key);
                }
                PreparedMaterialization prepared = flight.future().join();
                ServerLevel level = levelFor(key);
                MosaicStructureOverlayStore.publishCanonical(
                        level, key.pos(), prepared.artifact().localWorldSeed(),
                        prepared.chunk(), prepared.externalStructureStarts());
                Consumer<Publication> observer = verificationObserver;
                if (observer != null) observer.accept(new Publication(key, prepared, holder));
            }
        }
        PhysicalMosaicTrace.onHolderStatusCompleted(holder, status, chunk);
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
        // Cooperative close: queued/preparing workers produce an owner-tagged termination,
        // while transactions already past canonical preparation finish their atomic handoff.
        // Vanilla's existing hasWork/deactivateTicketsOnClosing loop drains holder tasks.
        for (ServerLevel level : server.getAllLevels()) lifecycle(level).beginClosing();
    }

    /** Invoked after Vanilla's generation drain, before save/closing registries or storage. */
    public static void finishShutdown(MinecraftServer server) {
        server.managedBlock(() -> {
            for (ServerLevel level : server.getAllLevels()) if (!lifecycle(level).quiescent()) return false;
            return true;
        });
        clearClosedServer(server);
    }

    /** Preserve the original fatal error, but never leave detached workers running against a retired identity. */
    public static void finishFailedShutdown(MinecraftServer server) {
        shutdown(server);
        for (ServerLevel level : server.getAllLevels()) lifecycle(level).awaitClosedWorkers();
        clearClosedServer(server);
        MosaicStructureOverlayStore.clear(server);
    }

    private static void clearClosedServer(MinecraftServer server) {
        IN_FLIGHT.keySet().removeIf(key -> key.server() == server);
        REQUESTED_PHYSICAL_STATUSES.keySet().removeIf(key -> key.server() == server);
        MATERIALIZATION_OBLIGATIONS.keySet().removeIf(key -> key.server() == server);
        PHYSICAL_STATUS_ALLOWANCES.keySet().removeIf(key -> key.server() == server);
        ACTIVE_PHYSICAL_PLANS.keySet().removeIf(key -> key.server() == server);
        ARTIFACT_CAPTURES_BY_KEY.keySet().removeIf(key -> key.server() == server);
        PUBLISHES_BY_KEY.keySet().removeIf(key -> key.server() == server);
        synchronized (PHYSICAL_CHUNK_MAPS) {
            PHYSICAL_CHUNK_MAPS.entrySet().removeIf(entry -> entry.getValue().getServer() == server);
        }
        synchronized (AWAITING_PUBLISH) {
            AWAITING_PUBLISH.entrySet().removeIf(entry -> entry.getValue().server() == server);
        }
    }

    /** Also covers an explicit ServerLevel close, not just the integrated-server stop path. */
    public static void closeLevel(ServerLevel level) {
        var lifecycle = lifecycle(level);
        lifecycle.beginClosing();
        if (!lifecycle.active()) return;
        level.getServer().managedBlock(lifecycle::quiescent);
        java.util.function.Predicate<GenerationKey> belongs = key -> key.server() == level.getServer()
                && key.dimension().equals(level.dimension());
        IN_FLIGHT.keySet().removeIf(belongs);
        REQUESTED_PHYSICAL_STATUSES.keySet().removeIf(belongs);
        MATERIALIZATION_OBLIGATIONS.keySet().removeIf(belongs);
        PHYSICAL_STATUS_ALLOWANCES.keySet().removeIf(belongs);
        ACTIVE_PHYSICAL_PLANS.keySet().removeIf(belongs);
        ARTIFACT_CAPTURES_BY_KEY.keySet().removeIf(belongs);
        PUBLISHES_BY_KEY.keySet().removeIf(belongs);
        synchronized (PHYSICAL_CHUNK_MAPS) {
            PHYSICAL_CHUNK_MAPS.entrySet().removeIf(entry -> entry.getValue() == level);
        }
        synchronized (AWAITING_PUBLISH) {
            AWAITING_PUBLISH.entrySet().removeIf(entry -> belongs.test(entry.getValue()));
        }
    }

    private static MosaicGenerationLifecycle lifecycle(ServerLevel level) {
        return ((MosaicGenerationLifecycleOwner) level).randomnibble6plus24generator$generationLifecycle();
    }

    public static Metrics metrics() {
        return new Metrics(
                REQUEST_COUNT.get(),
                DEDUP_HITS.get(),
                ISOLATED_GENERATIONS.get(),
                ARTIFACT_CAPTURES.get(),
                PUBLISHES.get(),
                PLAN_COUNT.get(),
                IN_FLIGHT.size(),
                REQUESTED_PHYSICAL_STATUSES.size(),
                MATERIALIZATION_OBLIGATIONS.size(),
                PHYSICAL_STATUS_ALLOWANCES.size());
    }

    /** Returns whether one ordinary physical target has no pending Mosaic handoff work. */
    public static boolean isIdle(ServerLevel level, ChunkPos pos) {
        GenerationKey key = key(level, pos);
        return !IN_FLIGHT.containsKey(key)
                && !REQUESTED_PHYSICAL_STATUSES.containsKey(key)
                && !MATERIALIZATION_OBLIGATIONS.containsKey(key)
                && !PHYSICAL_STATUS_ALLOWANCES.containsKey(key);
    }

    public static long artifactCaptures(ServerLevel level, ChunkPos pos) {
        AtomicLong count = ARTIFACT_CAPTURES_BY_KEY.get(key(level, pos));
        return count == null ? 0L : count.get();
    }

    public static long publishes(ServerLevel level, ChunkPos pos) {
        AtomicLong count = PUBLISHES_BY_KEY.get(key(level, pos));
        return count == null ? 0L : count.get();
    }

    public static void resetVerificationState() {
        if (!IN_FLIGHT.isEmpty() || !AWAITING_PUBLISH.isEmpty()) {
            throw new IllegalStateException("Cannot reset Phase 3A metrics while materialization is in flight");
        }
        REQUESTED_PHYSICAL_STATUSES.clear();
        MATERIALIZATION_OBLIGATIONS.clear();
        PHYSICAL_STATUS_ALLOWANCES.clear();
        ACTIVE_PHYSICAL_PLANS.clear();
        PHYSICAL_CHUNK_MAPS.clear();
        REQUEST_COUNT.set(0L);
        DEDUP_HITS.set(0L);
        ISOLATED_GENERATIONS.set(0L);
        ARTIFACT_CAPTURES.set(0L);
        PUBLISHES.set(0L);
        PLAN_COUNT.set(0L);
        ARTIFACT_CAPTURES_BY_KEY.clear();
        PUBLISHES_BY_KEY.clear();
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
        MosaicGenerationLifecycle lifecycle = lifecycle(level);
        lifecycle.checkPreparing();
        long totalStarted = System.nanoTime();
        MosaicRuntimeContext runtime = MosaicWorldIdentity.runtimeContext(level)
                .orElseThrow(() -> new IllegalStateException("Missing Mosaic runtime context"));
        long localSeed = runtime.resolveLocalWorldSeed(level.dimension(), pos);
        failIf(FaultPoint.AFTER_LOCAL_SEED_RESOLVE);

        ISOLATED_GENERATIONS.incrementAndGet();
        long isolatedStarted = System.nanoTime();
        CanonicalChunkArtifact artifact;
        SpawnBiomeSnapshot spawnBiomes;
        java.util.List<CompoundTag> externalStructureStarts;
        IsolatedGenerationMetrics isolatedMetrics;
        long artifactNanos;
        try (IsolatedGenerationContext context = IsolatedGenerationContext.create(
                IsolatedGenerationMode.ISOLATED_MOSAIC, level, localSeed, pos)) {
            FeatureStableGenerationRun run = context.generateFeaturesStable();
            // Do not retain the isolated session or start Artifact/materialization for a closing world.
            // Already-running Vanilla work finishes in its existing executor and closes via try-with-resources.
            lifecycle.checkPreparing();
            spawnBiomes = context.captureSpawnBiomes(runtime.profile());
            isolatedMetrics = run.metrics();
            failIf(FaultPoint.AFTER_ISOLATED_STABLE_GENERATION);
            externalStructureStarts = context.captureExternalStructureStarts(
                    StructurePieceSerializationContext.fromLevel(level));
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
            ARTIFACT_CAPTURES_BY_KEY.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
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
        ((MosaicSpawnBiomeCarrier) fresh).randomnibble6plus24generator$spawnBiomes(spawnBiomes);
        return new PreparedMaterialization(
                key,
                artifact,
                fresh,
                externalStructureStarts,
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

    private static ServerLevel levelFor(GenerationKey key) {
        synchronized (PHYSICAL_CHUNK_MAPS) {
            for (ServerLevel level : PHYSICAL_CHUNK_MAPS.values()) {
                if (level.getServer() == key.server() && level.dimension().equals(key.dimension())) {
                    return level;
                }
            }
        }
        throw new IllegalStateException("No physical Mosaic level for published generation " + key);
    }

    private static void abort(GenerationKey key, ChunkAccess chunk) {
        AWAITING_PUBLISH.remove(chunk);
        IN_FLIGHT.remove(key);
        MATERIALIZATION_OBLIGATIONS.remove(key);
        PHYSICAL_STATUS_ALLOWANCES.remove(key);
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
            java.util.List<CompoundTag> externalStructureStarts,
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
            long planCount,
            int inFlightCount,
            int requestedTargetCount,
            int materializationObligationCount,
            int physicalStatusAllowanceCount) {
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
