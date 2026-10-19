package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapQueues;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.fml.ModList;

/**
 * One physical dimension owns its plans, deduplication and detached handoff; no static world cache.
 */
public final class MosaicPhysicalLevelState {
    private final ServerLevel level;
    private final MosaicGenerationLifecycle lifecycle;
    private volatile MosaicRuntimeContext authority;
    private final Object planLock = new Object();
    private final Map<ChunkPos, ChunkStatus> requested = new ConcurrentHashMap<>();
    private final Map<ChunkPos, ChunkStatus> obligations = new ConcurrentHashMap<>();
    private final Map<ChunkPos, ChunkStatus> allowances = new ConcurrentHashMap<>();
    private final Map<ChunkPos, Map<ChunkPos, ChunkStatus>> plans = new HashMap<>();
    private final Map<ChunkPos, CompletableFuture<Prepared>> inFlight = new ConcurrentHashMap<>();
    private final Map<ChunkAccess, Prepared> awaitingPublication =
            Collections.synchronizedMap(new IdentityHashMap<>());
    // The value has no Chunk/session reference. A saved pre-SPAWN chunk can recover without it.
    private final Map<ChunkAccess, SpawnBiomeSnapshot> spawnBiomes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ChunkAccess, SpawnCollisionSnapshot> spawnCollisions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<ChunkAccess> poiReady =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final LongAdder generations = new LongAdder(),
            publications = new LongAdder(),
            dedup = new LongAdder();
    private final LongAdder biomePreparations = new LongAdder(),
            biomePublications = new LongAdder();
    private final LongAdder populationCollisionReads = new LongAdder(),
            populationColdCollisionSources = new LongAdder();
    private final LongAdder poi = new LongAdder(),
            spawn = new LongAdder(),
            light = new LongAdder(),
            full = new LongAdder();

    public MosaicPhysicalLevelState(ServerLevel level, MosaicGenerationLifecycle lifecycle) {
        this.level = level;
        this.lifecycle = lifecycle;
    }

    private MosaicRuntimeContext runtime() {
        var current = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
        synchronized (lifecycle) {
            if (!lifecycle.activate()) throw lifecycle.cancellation();
            if (authority == null) authority = current;
            if (current != authority)
                throw new IllegalStateException(
                        "Physical handoff belongs to a replaced world authority");
        }
        return current;
    }

    public void plan(ChunkStatus status, ChunkPos pos) {
        if (status == ChunkStatus.EMPTY) return;
        runtime();
        synchronized (planLock) {
            var effective = requested.merge(pos, status, MosaicPhysicalLevelState::later);
            var plan = MosaicPhysicalGenerationPlan.derive(pos, effective);
            var old = plans.put(pos, plan.requirements());
            Set<ChunkPos> affected = new HashSet<>(plan.requirements().keySet());
            if (old != null) affected.addAll(old.keySet());
            plan.artifacts()
                    .forEach(
                            (target, required) ->
                                    obligations.merge(
                                            target, required, MosaicPhysicalLevelState::later));
            affected.forEach(this::refresh);
        }
    }

    public ChunkStatus allowance(ChunkPos pos) {
        return allowances.get(pos);
    }

    public boolean obligated(ChunkPos pos) {
        return obligations.containsKey(pos);
    }

    public boolean supportsPartialBiomes() {
        // Only this Target's independently proved native environment may detach BIOMES.
        // Unknown mod hooks retain the complete exact Artifact path, never an empty palette.
        return !Boolean.getBoolean(ModConstants.MOD_ID + ".physicalBiomes.exactFallback")
                && ModList.get().getMods().stream()
                        .allMatch(
                                mod ->
                                        Set.of(
                                                        "minecraft",
                                                        "neoforge",
                                                        "javafml",
                                                        "mixinextras",
                                                        ModConstants.MOD_ID,
                                                        ModConstants.MOD_ID + "_probe")
                                                .contains(mod.getModId()));
    }

    private boolean needsPreparation(ChunkPos pos, ChunkAccess chunk) {
        var required = obligations.get(pos);
        if (required == null) return false;
        var minimum =
                required.isOrBefore(ChunkStatus.BIOMES) ? ChunkStatus.BIOMES : ChunkStatus.FEATURES;
        return chunk.getPersistedStatus().isBefore(minimum);
    }

    private static ChunkStatus later(ChunkStatus left, ChunkStatus right) {
        return right.isAfter(left) ? right : left;
    }

    private void refresh(ChunkPos pos) {
        ChunkStatus maximum = null;
        for (var plan : plans.values()) {
            var status = plan.get(pos);
            if (status != null) maximum = maximum == null ? status : later(maximum, status);
        }
        if (maximum == null) {
            allowances.remove(pos);
            obligations.remove(pos);
        } else allowances.put(pos, maximum);
    }

    private void complete(ChunkPos pos, ChunkStatus status) {
        synchronized (planLock) {
            var wanted = requested.get(pos);
            if (wanted != null && status.isOrAfter(wanted)) {
                requested.remove(pos, wanted);
                var removed = plans.remove(pos);
                if (removed != null) removed.keySet().forEach(this::refresh);
            }
            var allowed = allowances.get(pos);
            if (allowed != null && status.isOrAfter(allowed)) allowances.remove(pos, allowed);
            var obligation = obligations.get(pos);
            if (obligation != null && status.isOrAfter(obligation))
                obligations.remove(pos, obligation);
        }
    }

    private CompletableFuture<Prepared> prepare(ChunkPos pos) {
        var runtime = runtime();
        CompletableFuture<Prepared> created = new CompletableFuture<>();
        synchronized (lifecycle) {
            lifecycle.checkPreparing();
            var existing = inFlight.putIfAbsent(pos, created);
            if (existing != null) {
                dedup.increment();
                return existing;
            }
            lifecycle.workerAccepted();
        }
        created.whenComplete(
                (value, failure) -> {
                    if (failure != null) {
                        inFlight.remove(pos, created);
                        complete(pos, ChunkStatus.FULL);
                    }
                });
        try {
            CompletableFuture.runAsync(
                    () -> {
                        try {
                            lifecycle.checkPreparing();
                            long seed = runtime.resolveLocalWorldSeed(level.dimension(), pos);
                            ProtoChunk partial = null;
                            BFeaturePreparation.Result data = null;
                            try (var context = new StructureGenerationContext(level, seed, pos)) {
                                if (supportsPartialBiomes()
                                        && obligations.get(pos) == ChunkStatus.BIOMES) {
                                    partial =
                                            DetachedBiomeInputs.generate(
                                                    context,
                                                    CompactStructureInputs.resolve(context));
                                    biomePreparations.increment();
                                } else {
                                    data = BFeaturePreparation.generate(context, runtime.profile());
                                }
                            }
                            lifecycle.checkPreparing();
                            // No live preparation owner crosses either physical handoff.
                            ProtoChunk chunk;
                            if (data == null) {
                                chunk = Objects.requireNonNull(partial);
                            } else {
                                chunk =
                                        data.artifact()
                                                .rehydrate(
                                                        level.dimension().location().toString(),
                                                        pos,
                                                        seed,
                                                        runtime.profile(),
                                                        level,
                                                        level.registryAccess(),
                                                        StructurePieceSerializationContext
                                                                .fromLevel(level));
                                generations.increment();
                            }
                            lifecycle.checkPreparing();
                            chunk.setUnsaved(true);
                            created.complete(new Prepared(chunk, data));
                        } catch (Throwable failure) {
                            if (!created.completeExceptionally(failure)
                                    && !lifecycle.isExpected(failure))
                                MinecraftServer.setFatalException(
                                        new ReportedException(
                                                CrashReport.forThrowable(
                                                        failure, "Mosaic canonical worker")));
                        } finally {
                            lifecycle.workerFinished();
                        }
                    },
                    Util.backgroundExecutor());
        } catch (RuntimeException failure) {
            created.completeExceptionally(failure);
            lifecycle.workerFinished();
        }
        return created;
    }

    public CompletableFuture<ChunkAccess> loaded(
            GenerationChunkHolder holder, CompletableFuture<ChunkAccess> loaded) {
        if (!obligated(holder.getPos())) return loaded;
        return loaded.thenCompose(
                chunk -> {
                    if (!needsPreparation(holder.getPos(), chunk)) {
                        return CompletableFuture.completedFuture(chunk);
                    }
                    return prepare(holder.getPos())
                            .thenCompose(
                                    prepared ->
                                            onMain(
                                                    () -> {
                                                        awaitingPublication.put(
                                                                prepared.chunk(), prepared);
                                                        return prepared.chunk();
                                                    }));
                });
    }

    /** Publish metadata before native holder futures can release dependent continuations. */
    public void published(GenerationChunkHolder holder, ChunkStatus status, ChunkAccess chunk) {
        var prepared = awaitingPublication.get(chunk);
        if (prepared != null) {
            if (!level.getServer().isSameThread() || !holder.getPos().equals(chunk.getPos()))
                throw new IllegalStateException(
                        "Artifact publication lost holder/thread ownership");
            runtime();
            if (!awaitingPublication.remove(chunk, prepared))
                throw new IllegalStateException("Physical publication lost its prepared owner");
            var flight = inFlight.get(chunk.getPos());
            if (flight == null || flight.getNow(null) != prepared)
                throw new IllegalStateException("Physical publication lost its in-flight owner");
            var data = prepared.data();
            if (data == null) {
                if (chunk.getPersistedStatus() != ChunkStatus.BIOMES)
                    throw new IllegalStateException("Detached partial result is not BIOMES");
                biomePublications.increment();
            } else {
                MosaicStructureOverlayStore.publish(
                        level,
                        chunk.getPos(),
                        data.artifact().localWorldSeed(),
                        data.externalStarts());
                spawnBiomes.put(chunk, data.spawnBiomes());
                spawnCollisions.put(chunk, data.spawnCollisions());
                publications.increment();
            }
            if (!inFlight.remove(chunk.getPos(), flight))
                throw new IllegalStateException("Physical publication owner was replaced");
        }
        complete(holder.getPos(), status);
    }

    public CompletableFuture<ChunkAccess> step(
            GenerationChunkHolder holder,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            WorldGenContext context) {
        var status = step.targetStatus();
        ChunkAccess parent = holder.getChunkIfPresentUnchecked(status.getParent());
        if (parent == null)
            throw new IllegalStateException("Missing native physical parent " + status.getParent());
        if (needsPreparation(holder.getPos(), parent)) {
            return prepare(holder.getPos())
                    .thenCompose(
                            prepared ->
                                    onMain(
                                            () -> {
                                                awaitingPublication.put(prepared.chunk(), prepared);
                                                return prepared.chunk();
                                            }))
                    .thenCompose(
                            chunk ->
                                    status.isOrBefore(ChunkStatus.FEATURES)
                                            ? CompletableFuture.completedFuture(chunk)
                                            : derived(context, step, cache, chunk));
        }
        if (status.isOrBefore(ChunkStatus.FEATURES))
            return CompletableFuture.completedFuture(parent);
        return derived(context, step, cache, parent);
    }

    private CompletableFuture<ChunkAccess> derived(
            WorldGenContext context,
            ChunkStep step,
            StaticCache2D<GenerationChunkHolder> cache,
            ChunkAccess chunk) {
        if (chunk.getPersistedStatus().isBefore(ChunkStatus.FEATURES))
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Derived step lacks a physical canonical result"));
        CompletableFuture<Void> prepared;
        if (step.targetStatus() == ChunkStatus.INITIALIZE_LIGHT
                && chunk.getPersistedStatus().isBefore(ChunkStatus.INITIALIZE_LIGHT)
                && !poiReady.contains(chunk)) {
            prepared =
                    MosaicPhysicalPoiReconciler.reconcile(level, chunk)
                            .thenRun(
                                    () -> {
                                        if (!poiReady.add(chunk))
                                            throw new IllegalStateException("POI reconciled twice");
                                        poi.increment();
                                    });
        } else prepared = CompletableFuture.completedFuture(null);
        return prepared.thenCompose(ignored -> step.apply(context, cache, chunk))
                .thenCompose(
                        result ->
                                onMain(
                                        () -> {
                                            if (step.targetStatus() == ChunkStatus.LIGHT)
                                                light.increment();
                                            if (step.targetStatus() == ChunkStatus.FULL)
                                                full.increment();
                                            return result;
                                        }));
    }

    public SpawnBiomeSnapshot takeSpawnBiomes(ChunkAccess chunk) {
        return spawnBiomes.remove(chunk);
    }

    public SpawnCollisionSnapshot takeSpawnCollisions(ChunkAccess chunk) {
        return spawnCollisions.remove(chunk);
    }

    public void spawned() {
        spawn.increment();
    }

    public void populationCollisionRead() {
        populationCollisionReads.increment();
    }

    public void populationColdCollisionSource() {
        populationColdCollisionSources.increment();
    }

    private <T> CompletableFuture<T> onMain(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        // The holder's own completion is separately dispatched onto this same native queue;
        // completing this future on main alone cannot guarantee a late handler's thread.
        ((ChunkMapQueues) level.getChunkSource().chunkMap)
                .mosaic$mainThreadExecutor()
                .tell(
                        () -> {
                            try {
                                lifecycle.checkPreparing();
                                runtime();
                                result.complete(task.get());
                            } catch (Throwable failure) {
                                result.completeExceptionally(failure);
                            }
                        });
        return result;
    }

    public void beginClosing() {
        if (!lifecycle.active()) return;
        lifecycle.beginClosing();
        inFlight.values().forEach(future -> future.completeExceptionally(lifecycle.cancellation()));
    }

    public void clearClosed() {
        if (!lifecycle.quiescent())
            throw new IllegalStateException("World still owns canonical workers");
        inFlight.clear();
        awaitingPublication.clear();
        spawnBiomes.clear();
        spawnCollisions.clear();
        poiReady.clear();
        synchronized (planLock) {
            plans.clear();
            requested.clear();
            obligations.clear();
            allowances.clear();
        }
    }

    public Metrics metrics() {
        return new Metrics(
                generations.sum(),
                publications.sum(),
                dedup.sum(),
                poi.sum(),
                light.sum(),
                spawn.sum(),
                full.sum(),
                inFlight.size(),
                biomePreparations.sum(),
                biomePublications.sum(),
                populationCollisionReads.sum(),
                populationColdCollisionSources.sum());
    }

    public record Metrics(
            long artifacts,
            long publications,
            long dedupHits,
            long poi,
            long light,
            long spawn,
            long full,
            int inFlight,
            long biomePreparations,
            long biomePublications,
            long populationCollisionReads,
            long populationColdCollisionSources) {}

    private record Prepared(ProtoChunk chunk, BFeaturePreparation.Result data) {}
}
