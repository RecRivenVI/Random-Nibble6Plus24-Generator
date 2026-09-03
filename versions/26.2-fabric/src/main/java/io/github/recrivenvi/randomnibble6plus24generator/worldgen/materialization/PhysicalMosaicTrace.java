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

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.PhysicalWorldAccessException;

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
    private static final ConcurrentLinkedQueue<SpawnSeed> SPAWN_SEEDS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<SpawnRead> SPAWN_READS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<StructureReloadSeed> STRUCTURE_RELOAD_SEEDS = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<String, LongAdder> LIFECYCLE_CALLS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> LIFECYCLE_DUPLICATES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> RUNTIME_TICKS = new ConcurrentHashMap<>();
    private static final AtomicLong INITIALIZE_NANOS = new AtomicLong();
    private static final AtomicLong LIGHT_NANOS = new AtomicLong();
    private static final AtomicLong SPAWN_NANOS = new AtomicLong();
    private static final AtomicLong FULL_NANOS = new AtomicLong();
    private static volatile FaultPoint verificationFault = FaultPoint.NONE;
    private static volatile boolean detailedVerification;
    private static volatile boolean strictLightingVerification;

    private PhysicalMosaicTrace() {
    }

    public static void recordGeneratorCall(String operation) {
        GENERATOR_CALLS.computeIfAbsent(operation, ignored -> new LongAdder()).increment();
    }

    public static void rejectForbiddenStatus(ChunkStatus status) {
        FORBIDDEN_STATUS_CALLS.computeIfAbsent(status.getName(), ignored -> new LongAdder()).increment();
        throw new IllegalStateException("Phase 3C1 forbids physical Mosaic status " + status);
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
        if (status != ChunkStatus.INITIALIZE_LIGHT
                && status != ChunkStatus.LIGHT
                && status != ChunkStatus.SPAWN
                && status != ChunkStatus.FULL) {
            throw new IllegalArgumentException("Not a Phase 3C1 physical stage: " + status);
        }
        if ((status == ChunkStatus.INITIALIZE_LIGHT || status == ChunkStatus.LIGHT) && !physicalEngine) {
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
        if (status != ChunkStatus.INITIALIZE_LIGHT
                && status != ChunkStatus.LIGHT
                && status != ChunkStatus.SPAWN
                && status != ChunkStatus.FULL) return;
        PHYSICAL_STATUS_COMPLETIONS.computeIfAbsent(status.getName(), ignored -> new LongAdder()).increment();
        Long started = STAGE_STARTS.remove(new StageKey(level, status, chunk.getPos()));
        if (started != null) {
            long elapsed = System.nanoTime() - started;
            if (status == ChunkStatus.INITIALIZE_LIGHT) INITIALIZE_NANOS.addAndGet(elapsed);
            if (status == ChunkStatus.LIGHT) LIGHT_NANOS.addAndGet(elapsed);
            if (status == ChunkStatus.SPAWN) SPAWN_NANOS.addAndGet(elapsed);
            if (status == ChunkStatus.FULL) FULL_NANOS.addAndGet(elapsed);
        }
    }

    public static void recordSpawnSeed(
            ServerLevel level, net.minecraft.world.level.ChunkPos target, long observed, long expected) {
        if (observed != expected) {
            throw new PhysicalWorldAccessException(
                    "Physical Mosaic SPAWN seed mismatch at " + target
                            + ": observed=" + observed + ", expected=" + expected);
        }
        SPAWN_SEEDS.add(new SpawnSeed(level.dimension(), target, observed, expected));
    }

    public static void recordSpawnChunkReads(
            ServerLevel level,
            net.minecraft.world.level.ChunkPos target,
            Map<String, Long> reads) {
        reads.forEach((key, count) -> SPAWN_READS.add(
                new SpawnRead(level.dimension(), target, key, count)));
    }

    public static void recordStructureReloadSeed(
            ServerLevel level,
            net.minecraft.world.level.ChunkPos chunkPos,
            long routedSeed,
            long physicalSeed) {
        STRUCTURE_RELOAD_SEEDS.add(new StructureReloadSeed(
                level.dimension(), chunkPos, routedSeed, physicalSeed));
    }

    public static void recordLifecycleCall(
            ServerLevel level, String lifecycle, net.minecraft.world.level.ChunkPos pos) {
        if (!detailedVerification || !MosaicPhysicalMaterializer.isPhysicalMosaic(level)) return;
        String key = lifecycle + "@" + level.dimension().identifier() + ":" + pos;
        long count = LIFECYCLE_CALLS.computeIfAbsent(key, ignored -> new LongAdder()).sum();
        // updateBlockEntityTicker is invoked once per ticker-bearing block
        // entity, so its per-chunk count is not a duplicate lifecycle event.
        if (count > 0 && !"updateBlockEntityTicker".equals(lifecycle)) {
            LIFECYCLE_DUPLICATES.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        }
        LIFECYCLE_CALLS.get(key).increment();
    }

    public static void recordRuntimeTick(ServerLevel level, String kind) {
        if (!detailedVerification || !MosaicPhysicalMaterializer.isPhysicalMosaic(level)) return;
        RUNTIME_TICKS.computeIfAbsent(kind, ignored -> new LongAdder()).increment();
    }

    public static void recordRuntimeTick(String kind) {
        if (!detailedVerification) return;
        RUNTIME_TICKS.computeIfAbsent(kind, ignored -> new LongAdder()).increment();
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
        if (strictLightingVerification && planned && physical == null) {
            throw new IllegalStateException("Physical light engine missed planned Mosaic geometry at " + requested);
        }
        // The normal production scheduler may ask the light engine about
        // unplanned outer prerequisites while it is advancing a target.  Those
        // holders are allowed to remain below FEATURES; only a holder that was
        // explicitly planned for Mosaic materialization must already contain
        // canonical geometry.  The old unconditional check treated legitimate
        // Vanilla light-frontier queries as a corruption signal and prevented
        // ordinary production requests from reaching FULL.
        if (strictLightingVerification && planned && physical != null
                && physical.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
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
                SPAWN_NANOS.get(),
                FULL_NANOS.get(),
                List.copyOf(PLANS),
                List.copyOf(LIGHT_QUERIES),
                List.copyOf(SPAWN_SEEDS),
                List.copyOf(SPAWN_READS),
                List.copyOf(STRUCTURE_RELOAD_SEEDS),
                copy(LIFECYCLE_CALLS),
                copy(LIFECYCLE_DUPLICATES),
                copy(RUNTIME_TICKS),
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
        SPAWN_SEEDS.clear();
        SPAWN_READS.clear();
        STRUCTURE_RELOAD_SEEDS.clear();
        LIFECYCLE_CALLS.clear();
        LIFECYCLE_DUPLICATES.clear();
        RUNTIME_TICKS.clear();
        INITIALIZE_NANOS.set(0L);
        LIGHT_NANOS.set(0L);
        SPAWN_NANOS.set(0L);
        FULL_NANOS.set(0L);
        verificationFault = FaultPoint.NONE;
        detailedVerification = false;
        strictLightingVerification = false;
    }

    public static void enableDetailedVerification() {
        detailedVerification = true;
        strictLightingVerification = true;
    }

    /** Enables counters/evidence without applying verifier-only frontier assertions. */
    public static void enableDetailedObservation() {
        detailedVerification = true;
        strictLightingVerification = false;
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

    public record SpawnSeed(
            ResourceKey<Level> dimension,
            net.minecraft.world.level.ChunkPos target,
            long observed,
            long expected) {
    }

    public record SpawnRead(
            ResourceKey<Level> dimension,
            net.minecraft.world.level.ChunkPos target,
            String requested,
            long count) {
    }

    public record StructureReloadSeed(
            ResourceKey<Level> dimension,
            net.minecraft.world.level.ChunkPos chunkPos,
            long routedSeed,
            long physicalSeed) {
    }

    public record Snapshot(
            Map<String, Long> generatorCalls,
            Map<String, Long> forbiddenStatusCalls,
            Map<String, Long> physicalStatusCalls,
            Map<String, Long> physicalStatusCompletions,
            long initializeLightNanos,
            long lightNanos,
            long spawnNanos,
            long fullNanos,
            List<PlanRecord> plans,
            List<LightQuery> lightQueries,
            List<SpawnSeed> spawnSeeds,
            List<SpawnRead> spawnReads,
            List<StructureReloadSeed> structureReloadSeeds,
            Map<String, Long> lifecycleCalls,
            Map<String, Long> lifecycleDuplicates,
            Map<String, Long> runtimeTicks,
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
