package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.ScopedStructureSources;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.fml.ModList;

/** Exact detached pre-FEATURES prerequisites; no Artifact or decoration before it is required. */
public final class PhysicalPreFeaturesPreparation {
    private static final ConcurrentHashMap<PreparationKey, CompletableFuture<ProtoChunk>>
            IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<ChunkAccess, PreparationKey> PENDING =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<ChunkStatus, LongAdder> BUILDS =
            Map.of(ChunkStatus.BIOMES, new LongAdder(), ChunkStatus.TERRAIN, new LongAdder());
    private static final Map<ChunkStatus, LongAdder> PUBLISHES =
            Map.of(ChunkStatus.BIOMES, new LongAdder(), ChunkStatus.TERRAIN, new LongAdder());
    private static final ConcurrentHashMap<PreparationKey, LongAdder> BUILDS_BY_KEY =
            new ConcurrentHashMap<>();
    // Loader's installed mod set is immutable for this JVM, not a world identity cache.
    private static final boolean VANILLA_ENVIRONMENT = vanillaEnvironment();

    private PhysicalPreFeaturesPreparation() {}

    public static boolean supported(ServerLevel level) {
        return BDevelopmentWorld.active(level) && VANILLA_ENVIRONMENT;
    }

    public static boolean terrainSupported(ServerLevel level) {
        return supported(level);
    }

    record PreparationKey(MosaicPhysicalMaterializer.GenerationKey owner, ChunkStatus status) {}

    private static boolean vanillaEnvironment() {
        var known =
                java.util.Set.of(
                        "minecraft",
                        "java",
                        "neoforge",
                        "javafml",
                        "mixinextras",
                        ModConstants.MOD_ID,
                        ModConstants.MOD_ID + "_probe");
        return ModList.get().getMods().stream().allMatch(mod -> known.contains(mod.getModId()));
    }

    static CompletableFuture<ProtoChunk> request(
            ServerLevel level, MosaicPhysicalMaterializer.GenerationKey key) {
        return request(level, key, ChunkStatus.BIOMES);
    }

    static CompletableFuture<ProtoChunk> request(
            ServerLevel level, MosaicPhysicalMaterializer.GenerationKey key, ChunkStatus status) {
        if (status != ChunkStatus.BIOMES && status != ChunkStatus.TERRAIN)
            throw new IllegalArgumentException("Unsupported partial preparation " + status);
        if (!supported(level))
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Partial preparation outside its validated B capability"));
        var preparation = new PreparationKey(key, status);
        var lifecycle =
                ((MosaicGenerationLifecycleOwner) level)
                        .randomnibble6plus24generator$generationLifecycle();
        var promise = new CompletableFuture<ProtoChunk>();
        synchronized (lifecycle) {
            if (!lifecycle.activate())
                return CompletableFuture.failedFuture(lifecycle.cancellation());
            var previous = IN_FLIGHT.putIfAbsent(preparation, promise);
            if (previous != null) return previous;
            lifecycle.workerAccepted();
        }
        promise.whenComplete(
                (chunk, failure) -> {
                    if (failure != null) IN_FLIGHT.remove(preparation, promise);
                });
        try {
            CompletableFuture.runAsync(
                    () -> {
                        try {
                            lifecycle.checkPreparing();
                            var runtime = MosaicWorldIdentity.runtimeContext(level).orElseThrow();
                            long local =
                                    runtime.resolveLocalWorldSeed(level.dimension(), key.pos());
                            ProtoChunk prepared;
                            try (var context =
                                            IsolatedGenerationContext.create(
                                                    IsolatedGenerationMode.ISOLATED_MOSAIC,
                                                    level,
                                                    local,
                                                    key.pos(),
                                                    runtime.profile());
                                    var sources = ScopedStructureSources.create(context)) {
                                var structures = CompactStructureInputs.resolve(context, sources);
                                prepared =
                                        status == ChunkStatus.BIOMES
                                                ? DetachedBiomeInputs.generate(
                                                                context,
                                                                structures,
                                                                (stage, chunk) -> {})
                                                        .target()
                                                : DetachedTerrainInputs.generate(
                                                                context,
                                                                structures,
                                                                (stage, chunk) -> {})
                                                        .target();
                                lifecycle.checkPreparing();
                            }
                            if (prepared.getPersistedStatus() != status
                                    || !prepared.getPos().equals(key.pos()))
                                throw new IllegalStateException(
                                        "Partial physical preparation escaped " + status);
                            BUILDS.get(status).increment();
                            var observed = BUILDS_BY_KEY.get(preparation);
                            if (observed != null) observed.increment();
                            promise.complete(prepared);
                        } catch (Throwable failure) {
                            if (!lifecycle.isExpected(failure))
                                io.github.recrivenvi.randomnibble6plus24generator
                                        .RandomNibble6Plus24Generator.LOGGER
                                        .error(
                                                "Mosaic physical pre-FEATURES preparation failed at {} {}",
                                                level.dimension().identifier(),
                                                key.pos(),
                                                failure);
                            if (!promise.completeExceptionally(failure)
                                    && !lifecycle.isExpected(failure))
                                net.minecraft.util.thread.BlockableEventLoop.relayDelayCrash(
                                        net.minecraft.CrashReport.forThrowable(
                                                failure,
                                                "Exception Mosaic pre-FEATURES preparation worker"));
                        } finally {
                            lifecycle.workerFinished();
                        }
                    },
                    Util.backgroundExecutor());
        } catch (RuntimeException failure) {
            promise.completeExceptionally(failure);
            lifecycle.workerFinished();
        }
        return promise;
    }

    static ProtoChunk awaiting(
            MosaicPhysicalMaterializer.GenerationKey key, ChunkStatus status, ProtoChunk chunk) {
        PENDING.put(chunk, new PreparationKey(key, status));
        return chunk;
    }

    static PreparationKey published(ChunkAccess chunk) {
        var key = PENDING.remove(chunk);
        if (key != null) {
            if (!chunk.getPos().equals(key.owner().pos())
                    || chunk.getPersistedStatus() != key.status())
                throw new IllegalStateException(
                        "Partial physical publication changed identity/status");
            var flight = IN_FLIGHT.remove(key);
            if (flight == null || flight.join() != chunk)
                throw new IllegalStateException("Partial publication lost its preparation owner");
            PUBLISHES.get(key.status()).increment();
        }
        return key;
    }

    static void clear(Predicate<MosaicPhysicalMaterializer.GenerationKey> belongs) {
        IN_FLIGHT.keySet().removeIf(key -> belongs.test(key.owner()));
        BUILDS_BY_KEY.keySet().removeIf(key -> belongs.test(key.owner()));
        synchronized (PENDING) {
            PENDING.entrySet().removeIf(entry -> belongs.test(entry.getValue().owner()));
        }
    }

    public static Map<String, Long> metrics() {
        return metrics(ChunkStatus.BIOMES);
    }

    public static Map<String, Long> metrics(ChunkStatus status) {
        long pending;
        synchronized (PENDING) {
            pending = PENDING.values().stream().filter(key -> key.status() == status).count();
        }
        return Map.of(
                "builds",
                BUILDS.get(status).sum(),
                "publishes",
                PUBLISHES.get(status).sum(),
                "inFlight",
                IN_FLIGHT.keySet().stream().filter(key -> key.status() == status).count(),
                "pending",
                pending);
    }

    static void resetVerificationState() {
        if (!IN_FLIGHT.isEmpty() || !PENDING.isEmpty())
            throw new IllegalStateException("Cannot reset active partial BIOMES preparation");
        BUILDS_BY_KEY.clear();
        BUILDS.values().forEach(LongAdder::reset);
        PUBLISHES.values().forEach(LongAdder::reset);
    }

    /** Bounded probe observation, not used to schedule or generate anything. */
    public static void observeForVerification(
            ServerLevel level, net.minecraft.world.level.ChunkPos pos) {
        observeForVerification(level, pos, ChunkStatus.BIOMES);
    }

    public static void observeForVerification(
            ServerLevel level, net.minecraft.world.level.ChunkPos pos, ChunkStatus status) {
        BUILDS_BY_KEY.computeIfAbsent(
                new PreparationKey(MosaicPhysicalMaterializer.key(level, pos), status),
                ignored -> new LongAdder());
    }

    /**
     * Only explicitly observed targets retain counters; ordinary exploration never grows this map.
     */
    public static long builds(ServerLevel level, net.minecraft.world.level.ChunkPos pos) {
        return builds(level, pos, ChunkStatus.BIOMES);
    }

    public static long builds(
            ServerLevel level, net.minecraft.world.level.ChunkPos pos, ChunkStatus status) {
        var value =
                BUILDS_BY_KEY.get(
                        new PreparationKey(MosaicPhysicalMaterializer.key(level, pos), status));
        return value == null ? 0L : value.sum();
    }
}
