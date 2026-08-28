package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Verification counters and bounded-on-demand evidence for physical Mosaic derived-state work. */
public final class PhysicalMosaicTrace {

    private static final ConcurrentHashMap<String, LongAdder> GENERATOR_CALLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> FORBIDDEN_STATUS_CALLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> PHYSICAL_STATUS_CALLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> PHYSICAL_STATUS_COMPLETIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<StageKey, Long> STAGE_STARTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PlanScope, Set<ChunkPos>> PLANNED_GEOMETRY = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<PlanRecord> PLANS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<LightQuery> LIGHT_QUERIES = new ConcurrentLinkedQueue<>();
    private static final AtomicLong INITIALIZE_NANOS = new AtomicLong();
    private static final AtomicLong LIGHT_NANOS = new AtomicLong();
    private static volatile FaultPoint verificationFault = FaultPoint.NONE;
    private static volatile boolean detailedVerification;

    private PhysicalMosaicTrace() {
    }

    public static void recordGeneratorCall(String operation) {
        GENERATOR_CALLS.computeIfAbsent(operation, ignored -> new LongAdder()).increment();
    }

    public static void rejectForbiddenStatus(ChunkStatus status) {
        FORBIDDEN_STATUS_CALLS.computeIfAbsent(status.getName(), ignored -> new LongAdder()).increment();
        throw new IllegalStateException("Phase 3B forbids physical Mosaic status " + status);
    }

    public static void recordPlan(ServerLevel level, MosaicPhysicalGenerationPlan plan) {
        if (!detailedVerification) return;
        PlanScope scope = new PlanScope(level, level.dimension());
        PLANNED_GEOMETRY.computeIfAbsent(scope, ignored -> ConcurrentHashMap.newKeySet())
                .addAll(plan.materializationObligations().keySet());
        PLANS.add(new PlanRecord(
                level.dimension(),
                plan.requestedPos(),
                plan.requestedStatus(),
                plan.accumulatedRadius(),
                Map.copyOf(plan.materializationObligations())));
    }

    public static void beginPhysicalStage(
            ServerLevel level,
            ChunkStatus status,
            ChunkAccess chunk,
            boolean physicalEngine) {
        if (status != ChunkStatus.INITIALIZE_LIGHT && status != ChunkStatus.LIGHT) {
            throw new IllegalArgumentException("Not a Phase 3B physical stage: " + status);
        }
        if (!physicalEngine) {
            throw new IllegalStateException("Physical Mosaic lighting did not receive ServerChunkCache light engine");
        }
        PHYSICAL_STATUS_CALLS.computeIfAbsent(status.getName(), ignored -> new LongAdder()).increment();
        STAGE_STARTS.put(new StageKey(level, status, chunk.getPos()), System.nanoTime());
    }

    public static void beforeLightStep(ServerLevel level, GenerationChunkHolder holder) {
        if (verificationFault == FaultPoint.AFTER_INITIALIZE_LIGHT_BEFORE_LIGHT) {
            verificationFault = FaultPoint.NONE;
            ChunkAccess initialized = holder.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT);
            if (initialized == null || initialized.getPersistedStatus() != ChunkStatus.INITIALIZE_LIGHT) {
                throw new IllegalStateException("Post-INITIALIZE_LIGHT fault fired without initialized physical Chunk");
            }
            throw new InjectedDerivedStateFailure(FaultPoint.AFTER_INITIALIZE_LIGHT_BEFORE_LIGHT);
        }
    }

    public static void onPhysicalStepCompleted(ServerLevel level, ChunkStatus status, ChunkAccess chunk) {
        if (status != ChunkStatus.INITIALIZE_LIGHT && status != ChunkStatus.LIGHT) return;
        PHYSICAL_STATUS_COMPLETIONS.computeIfAbsent(status.getName(), ignored -> new LongAdder()).increment();
        Long started = STAGE_STARTS.remove(new StageKey(level, status, chunk.getPos()));
        if (started != null) {
            long elapsed = System.nanoTime() - started;
            if (status == ChunkStatus.INITIALIZE_LIGHT) INITIALIZE_NANOS.addAndGet(elapsed);
            if (status == ChunkStatus.LIGHT) LIGHT_NANOS.addAndGet(elapsed);
        }
    }

    public static void onHolderStatusCompleted(
            GenerationChunkHolder holder, ChunkStatus status, ChunkAccess chunk) {
        // GenerationChunkHolder remains the scheduler-owned future publication observer.
    }

    public static void recordLightingQuery(
            ServerLevel level, int chunkX, int chunkZ, LightChunk returned) {
        ChunkPos requested = new ChunkPos(chunkX, chunkZ);
        ChunkAccess physical = returned instanceof ChunkAccess access ? access : null;
        boolean planned = PLANNED_GEOMETRY
                .getOrDefault(new PlanScope(level, level.dimension()), Set.of())
                .contains(requested);
        if (planned && physical == null) {
            throw new IllegalStateException("Physical light engine missed planned Mosaic geometry at " + requested);
        }
        if (physical != null && physical.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
            throw new IllegalStateException(
                    "Physical light engine observed noncanonical Chunk " + requested
                            + " status=" + physical.getPersistedStatus());
        }
        if (detailedVerification) {
            LIGHT_QUERIES.add(new LightQuery(
                    level.dimension(),
                    requested,
                    physical == null ? null : physical.getPos(),
                    physical == null ? null : physical.getPersistedStatus(),
                    physical != null,
                    planned));
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                copy(GENERATOR_CALLS),
                copy(FORBIDDEN_STATUS_CALLS),
                copy(PHYSICAL_STATUS_CALLS),
                copy(PHYSICAL_STATUS_COMPLETIONS),
                INITIALIZE_NANOS.get(),
                LIGHT_NANOS.get(),
                List.copyOf(PLANS),
                List.copyOf(LIGHT_QUERIES),
                STAGE_STARTS.size());
    }

    public static void reset() {
        GENERATOR_CALLS.clear();
        FORBIDDEN_STATUS_CALLS.clear();
        PHYSICAL_STATUS_CALLS.clear();
        PHYSICAL_STATUS_COMPLETIONS.clear();
        STAGE_STARTS.clear();
        PLANNED_GEOMETRY.clear();
        PLANS.clear();
        LIGHT_QUERIES.clear();
        INITIALIZE_NANOS.set(0L);
        LIGHT_NANOS.set(0L);
        verificationFault = FaultPoint.NONE;
        detailedVerification = false;
    }

    public static void enableDetailedVerification() {
        detailedVerification = true;
    }

    public static void setVerificationFault(FaultPoint point) {
        verificationFault = Objects.requireNonNull(point, "point");
    }

    public static void shutdown(ServerLevel level) {
        PlanScope scope = new PlanScope(level, level.dimension());
        PLANNED_GEOMETRY.remove(scope);
        STAGE_STARTS.keySet().removeIf(key -> key.level() == level);
    }

    private static Map<String, Long> copy(ConcurrentHashMap<String, LongAdder> source) {
        java.util.Map<String, Long> result = new java.util.TreeMap<>();
        source.forEach((key, value) -> result.put(key, value.sum()));
        return Map.copyOf(result);
    }

    private record PlanScope(ServerLevel level, ResourceKey<Level> dimension) {
    }

    private record StageKey(ServerLevel level, ChunkStatus status, ChunkPos pos) {
    }

    public record PlanRecord(
            ResourceKey<Level> dimension,
            ChunkPos requestedPos,
            ChunkStatus requestedStatus,
            int accumulatedRadius,
            Map<ChunkPos, ChunkStatus> obligations) {
    }

    public record LightQuery(
            ResourceKey<Level> dimension,
            ChunkPos requestedPos,
            ChunkPos returnedPos,
            ChunkStatus returnedStatus,
            boolean returnedPhysicalChunk,
            boolean plannedMaterializedGeometry) {
    }

    public record Snapshot(
            Map<String, Long> generatorCalls,
            Map<String, Long> forbiddenStatusCalls,
            Map<String, Long> physicalStatusCalls,
            Map<String, Long> physicalStatusCompletions,
            long initializeLightNanos,
            long lightNanos,
            List<PlanRecord> plans,
            List<LightQuery> lightQueries,
            int activeStageCount) {
        public long totalGeneratorCalls() {
            return generatorCalls.values().stream().mapToLong(Long::longValue).sum();
        }
    }

    public enum FaultPoint {
        NONE,
        AFTER_INITIALIZE_LIGHT_BEFORE_LIGHT
    }

    public static final class InjectedDerivedStateFailure extends RuntimeException {
        public InjectedDerivedStateFailure(FaultPoint point) {
            super("Injected Phase 3B derived-state failure at " + point);
        }
    }
}
