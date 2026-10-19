package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.mixin.ChunkMapQueues;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BFeaturePreparation;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.runtime.MosaicRuntimeContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnBiomeSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.SpawnCollisionSnapshot;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.StructureGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.MosaicStructureOverlayStore;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** Native Either futures and the chunk-source queue own publication; no replacement scheduler. */
public final class MosaicPhysicalLevelState {
    private final ServerLevel level;
    private final MosaicGenerationLifecycle lifecycle;
    private MosaicRuntimeContext authority;
    private final Map<ChunkPos, ChunkStatus> requested = new HashMap<>();
    private final Map<ChunkPos, Map<ChunkPos, ChunkStatus>> plans = new HashMap<>();
    private final Set<ChunkPos> obligations = ConcurrentHashMap.newKeySet();
    private final Map<
                    ChunkPos,
                    CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>
            inFlight = new ConcurrentHashMap<>();
    // Earlier native status futures may still hold the pre-materialization ProtoChunk.
    private final Map<ChunkAccess, ChunkAccess> aliases =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ChunkAccess, SpawnBiomeSnapshot> biomes =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ChunkAccess, SpawnCollisionSnapshot> collisions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<ChunkAccess, Set<ChunkStatus>> observed =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final LongAdder generations = new LongAdder(),
            publications = new LongAdder(),
            dedup = new LongAdder();
    private final LongAdder poi = new LongAdder(),
            spawn = new LongAdder(),
            light = new LongAdder(),
            full = new LongAdder();
    private final LongAdder collisionReads = new LongAdder(),
            coldCollisionSources = new LongAdder();

    public MosaicPhysicalLevelState(ServerLevel level, MosaicGenerationLifecycle lifecycle) {
        this.level = level;
        this.lifecycle = lifecycle;
    }

    private MosaicRuntimeContext runtime() {
        synchronized (lifecycle) {
            lifecycle.checkPreparing();
            var current = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
            if (!lifecycle.activate()) throw lifecycle.cancellation();
            if (authority == null) authority = current;
            if (authority != current)
                throw new IllegalStateException("Replaced physical world authority");
            return current;
        }
    }

    public synchronized void plan(ChunkStatus status, ChunkPos pos) {
        if (status == ChunkStatus.EMPTY) return;
        runtime();
        var wanted = requested.merge(pos, status, MosaicPhysicalLevelState::later);
        plans.put(pos, MosaicPhysicalGenerationPlan.derive(pos, wanted).requirements());
        refreshObligations();
        if (status.isOrAfter(ChunkStatus.STRUCTURE_STARTS)
                && ChunkStatus.FEATURES.isOrAfter(status)) obligations.add(pos);
    }

    private static ChunkStatus later(ChunkStatus a, ChunkStatus b) {
        return a.isOrAfter(b) ? a : b;
    }

    private void refreshObligations() {
        obligations.clear();
        plans.values()
                .forEach(
                        plan ->
                                plan.forEach(
                                        (pos, status) -> {
                                            if (status.isOrAfter(ChunkStatus.BIOMES))
                                                obligations.add(pos);
                                        }));
        requested.forEach(
                (pos, status) -> {
                    if (status.isOrAfter(ChunkStatus.STRUCTURE_STARTS)
                            && ChunkStatus.FEATURES.isOrAfter(status)) obligations.add(pos);
                });
    }

    private synchronized void completed(ChunkPos pos, ChunkStatus status) {
        var wanted = requested.get(pos);
        if (wanted != null && status.isOrAfter(wanted)) {
            requested.remove(pos);
            plans.remove(pos);
            refreshObligations();
        }
    }

    private synchronized boolean obligated(ChunkPos pos) {
        return obligations.contains(pos);
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> stage(
            ChunkStatus status, ChunkAccess input) {
        try {
            return openStage(status, input);
        } catch (RuntimeException | Error error) {
            if (!lifecycle.isExpected(error)) {
                retainFailure(error);
                throw error;
            }
            lifecycle.recordExpectedTermination();
            return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        }
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> openStage(
            ChunkStatus status, ChunkAccess input) {
        if (lifecycle.closing()) return ChunkHolder.UNLOADED_CHUNK_FUTURE;
        runtime();
        ChunkAccess current = aliases.getOrDefault(input, input);
        if (!current.getStatus().isOrAfter(ChunkStatus.FEATURES) && obligated(current.getPos()))
            return prepare(current)
                    .thenApply(
                            result -> {
                                result.left()
                                        .ifPresent(
                                                chunk ->
                                                        completed(
                                                                chunk.getPos(), chunk.getStatus()));
                                return result;
                            });
        if (status.isOrAfter(ChunkStatus.BIOMES)
                && !current.getStatus().isOrAfter(ChunkStatus.FEATURES))
            if (lifecycle.closing()) return ChunkHolder.UNLOADED_CHUNK_FUTURE;
            else
                throw new IllegalStateException(
                        "Missing real data obligation for native "
                                + status
                                + " at "
                                + current.getPos());
        if (current instanceof ProtoChunk proto && !current.getStatus().isOrAfter(status))
            proto.setStatus(status);
        completed(current.getPos(), status);
        return CompletableFuture.completedFuture(Either.left(current));
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> prepare(
            ChunkAccess input) {
        var runtime = runtime();
        var created = new CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>();
        synchronized (lifecycle) {
            if (lifecycle.closing()) return ChunkHolder.UNLOADED_CHUNK_FUTURE;
            var existing = inFlight.putIfAbsent(input.getPos(), created);
            if (existing != null) {
                dedup.increment();
                return existing;
            }
            lifecycle.workerAccepted();
        }
        try {
            Util.backgroundExecutor()
                    .execute(
                            () -> {
                                try {
                                    lifecycle.checkPreparing();
                                    var prepared = generate(input.getPos(), runtime);
                                    lifecycle.checkPreparing();
                                    ((ChunkMapQueues) level.getChunkSource().chunkMap)
                                            .mosaic$mainThreadExecutor()
                                            .execute(
                                                    () -> {
                                                        try {
                                                            lifecycle.checkPreparing();
                                                            if (!level.getServer().isSameThread()
                                                                    || runtime() != runtime)
                                                                throw new IllegalStateException(
                                                                        "Off-thread/replaced physical publication");
                                                            var artifact = prepared.artifact();
                                                            var chunk =
                                                                    artifact.rehydrate(
                                                                            level.dimension()
                                                                                    .location()
                                                                                    .toString(),
                                                                            input.getPos(),
                                                                            runtime
                                                                                    .resolveLocalWorldSeed(
                                                                                            level
                                                                                                    .dimension(),
                                                                                            input
                                                                                                    .getPos()),
                                                                            runtime.profile(),
                                                                            level,
                                                                            level.registryAccess(),
                                                                            StructurePieceSerializationContext
                                                                                    .fromLevel(
                                                                                            level));
                                                            var sections = chunk.getSections();
                                                            for (int i = 0;
                                                                    i < sections.length;
                                                                    i++)
                                                                level.getPoiManager()
                                                                        .checkConsistencyWithBlocks(
                                                                                SectionPos.of(
                                                                                        chunk
                                                                                                .getPos(),
                                                                                        chunk
                                                                                                .getSectionYFromSectionIndex(
                                                                                                        i)),
                                                                                sections[i]);
                                                            MosaicStructureOverlayStore.publish(
                                                                    level,
                                                                    chunk.getPos(),
                                                                    artifact.localWorldSeed(),
                                                                    prepared.externalStarts());
                                                            biomes.put(
                                                                    chunk, prepared.spawnBiomes());
                                                            collisions.put(
                                                                    chunk,
                                                                    prepared.spawnCollisions());
                                                            aliases.put(input, chunk);
                                                            chunk.setUnsaved(true);
                                                            poi.increment();
                                                            publications.increment();
                                                            created.complete(Either.left(chunk));
                                                        } catch (Throwable error) {
                                                            terminate(created, error);
                                                        } finally {
                                                            inFlight.remove(
                                                                    input.getPos(), created);
                                                        }
                                                    });
                                } catch (Throwable error) {
                                    terminate(created, error);
                                    inFlight.remove(input.getPos(), created);
                                } finally {
                                    lifecycle.workerFinished();
                                }
                            });
        } catch (Throwable error) {
            lifecycle.workerFinished();
            inFlight.remove(input.getPos(), created);
            terminate(created, error);
        }
        return created;
    }

    private BFeaturePreparation.Result generate(ChunkPos pos, MosaicRuntimeContext runtime) {
        try (var context =
                new StructureGenerationContext(
                        level, runtime.resolveLocalWorldSeed(level.dimension(), pos), pos)) {
            var prepared = BFeaturePreparation.generate(context, runtime.profile());
            generations.increment();
            return prepared;
        }
    }

    private void terminate(
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future,
            Throwable error) {
        if (lifecycle.isExpected(error)) {
            lifecycle.recordExpectedTermination();
            future.complete(ChunkHolder.UNLOADED_CHUNK);
        } else {
            failure.compareAndSet(null, error);
            future.completeExceptionally(error);
            ((ChunkMapQueues) level.getChunkSource().chunkMap)
                    .mosaic$mainThreadExecutor()
                    .execute(
                            () -> {
                                throw reported(error);
                            });
        }
    }

    private static ReportedException reported(Throwable error) {
        return new ReportedException(
                CrashReport.forThrowable(error, "Mosaic native physical generation"));
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> observe(
            ChunkStatus status,
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future) {
        return future.thenApply(
                result -> {
                    result.left()
                            .ifPresent(
                                    chunk -> {
                                        if (status == ChunkStatus.LIGHT
                                                || status == ChunkStatus.FULL) {
                                            synchronized (observed) {
                                                if (observed.computeIfAbsent(
                                                                chunk, unused -> new HashSet<>())
                                                        .add(status)) {
                                                    if (status == ChunkStatus.LIGHT)
                                                        light.increment();
                                                    else full.increment();
                                                }
                                            }
                                        }
                                        completed(chunk.getPos(), status);
                                    });
                    return result;
                });
    }

    public SpawnBiomeSnapshot takeBiomes(ChunkAccess chunk) {
        return biomes.remove(chunk);
    }

    public void retainFailure(Throwable error) {
        failure.compareAndSet(null, error);
    }

    public SpawnCollisionSnapshot takeCollisions(ChunkAccess chunk) {
        return collisions.remove(chunk);
    }

    public void spawned() {
        spawn.increment();
    }

    public void collisionRead() {
        collisionReads.increment();
    }

    public void coldCollisionSource() {
        coldCollisionSources.increment();
    }

    public void beginClosing() {
        lifecycle.beginClosing();
        inFlight.values().forEach(future -> future.complete(ChunkHolder.UNLOADED_CHUNK));
        inFlight.clear();
        aliases.clear();
        biomes.clear();
        collisions.clear();
        observed.clear();
        synchronized (this) {
            requested.clear();
            plans.clear();
            obligations.clear();
        }
    }

    public void awaitClosed() {
        lifecycle.awaitClosedWorkers();
        var error = failure.get();
        if (error != null) throw reported(error);
    }

    public Metrics metrics() {
        return new Metrics(
                generations.sum(),
                publications.sum(),
                poi.sum(),
                light.sum(),
                spawn.sum(),
                full.sum(),
                dedup.sum(),
                inFlight.size(),
                collisionReads.sum(),
                coldCollisionSources.sum());
    }

    public record Metrics(
            long generations,
            long publications,
            long poi,
            long light,
            long spawn,
            long full,
            long dedup,
            int inFlight,
            long collisionReads,
            long coldCollisionSources) {}
}
