package io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization;

import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.BDevelopmentWorld;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationContext;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.IsolatedGenerationMode;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.structure.CompactStructureInputs;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Detached native BIOMES prerequisites; never an Artifact or a substitute for physical terrain. */
public final class PhysicalBiomePreparation {
    private static final ConcurrentHashMap<
                    MosaicPhysicalMaterializer.GenerationKey, CompletableFuture<ProtoChunk>>
            IN_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<ChunkAccess, MosaicPhysicalMaterializer.GenerationKey> PENDING =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private static final LongAdder BUILDS = new LongAdder(), PUBLISHES = new LongAdder();
    private static final ConcurrentHashMap<MosaicPhysicalMaterializer.GenerationKey, LongAdder>
            BUILDS_BY_KEY = new ConcurrentHashMap<>();
    // Loader's installed mod set is immutable for this JVM, not a world identity cache.
    private static final boolean VANILLA_ENVIRONMENT = vanillaEnvironment();

    private PhysicalBiomePreparation() {}

    public static boolean supported(ServerLevel level) {
        return BDevelopmentWorld.active(level) && VANILLA_ENVIRONMENT;
    }

    private static boolean vanillaEnvironment() {
        // This candidate has only a Vanilla Oracle. Unknown worldgen hooks retain the complete
        // Artifact path until their native BIOMES lifetime/serialization contract is verified.
        return FabricLoader.getInstance().getAllMods().stream()
                .allMatch(
                        mod -> {
                            String id = mod.getMetadata().getId();
                            return id.equals("minecraft")
                                    || id.equals("java")
                                    || id.equals("fabricloader")
                                    || id.equals("mixinextras")
                                    || id.equals(ModConstants.MOD_ID)
                                    || id.equals(ModConstants.MOD_ID + "_probe")
                                    || id.equals("fabric-api-base")
                                    || id.equals("fabric-networking-api-v1")
                                    || id.equals("fabric-registry-sync-v0");
                        });
    }

    static CompletableFuture<ProtoChunk> request(
            ServerLevel level, MosaicPhysicalMaterializer.GenerationKey key) {
        if (!supported(level))
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Partial BIOMES outside its validated B capability"));
        var lifecycle =
                ((MosaicGenerationLifecycleOwner) level)
                        .randomnibble6plus24generator$generationLifecycle();
        var promise = new CompletableFuture<ProtoChunk>();
        synchronized (lifecycle) {
            if (!lifecycle.activate())
                return CompletableFuture.failedFuture(lifecycle.cancellation());
            var previous = IN_FLIGHT.putIfAbsent(key, promise);
            if (previous != null) return previous;
            lifecycle.workerAccepted();
        }
        promise.whenComplete(
                (chunk, failure) -> {
                    if (failure != null) IN_FLIGHT.remove(key, promise);
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
                                            runtime.profile())) {
                                prepared =
                                        DetachedBiomeInputs.generate(
                                                        context,
                                                        CompactStructureInputs.resolve(context),
                                                        (stage, chunk) -> {})
                                                .target();
                                lifecycle.checkPreparing();
                            }
                            if (prepared.getPersistedStatus() != ChunkStatus.BIOMES
                                    || !prepared.getPos().equals(key.pos()))
                                throw new IllegalStateException(
                                        "Partial physical preparation is not BIOMES");
                            BUILDS.increment();
                            var observed = BUILDS_BY_KEY.get(key);
                            if (observed != null) observed.increment();
                            promise.complete(prepared);
                        } catch (Throwable failure) {
                            if (!lifecycle.isExpected(failure))
                                io.github.recrivenvi.randomnibble6plus24generator
                                        .RandomNibble6Plus24Generator.LOGGER
                                        .error(
                                                "Mosaic physical BIOMES preparation failed at {} {}",
                                                level.dimension().identifier(),
                                                key.pos(),
                                                failure);
                            if (!promise.completeExceptionally(failure)
                                    && !lifecycle.isExpected(failure))
                                net.minecraft.util.thread.BlockableEventLoop.relayDelayCrash(
                                        net.minecraft.CrashReport.forThrowable(
                                                failure,
                                                "Exception Mosaic BIOMES preparation worker"));
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

    static ProtoChunk awaiting(MosaicPhysicalMaterializer.GenerationKey key, ProtoChunk chunk) {
        PENDING.put(chunk, key);
        return chunk;
    }

    static MosaicPhysicalMaterializer.GenerationKey published(ChunkAccess chunk) {
        var key = PENDING.remove(chunk);
        if (key != null) {
            if (!chunk.getPos().equals(key.pos())
                    || chunk.getPersistedStatus() != ChunkStatus.BIOMES)
                throw new IllegalStateException(
                        "Partial physical publication changed identity/status");
            var flight = IN_FLIGHT.remove(key);
            if (flight == null || flight.join() != chunk)
                throw new IllegalStateException("Partial publication lost its preparation owner");
            PUBLISHES.increment();
        }
        return key;
    }

    static void clear(Predicate<MosaicPhysicalMaterializer.GenerationKey> belongs) {
        IN_FLIGHT.keySet().removeIf(belongs);
        BUILDS_BY_KEY.keySet().removeIf(belongs);
        synchronized (PENDING) {
            PENDING.entrySet().removeIf(entry -> belongs.test(entry.getValue()));
        }
    }

    public static Map<String, Long> metrics() {
        return Map.of(
                "builds",
                BUILDS.sum(),
                "publishes",
                PUBLISHES.sum(),
                "inFlight",
                (long) IN_FLIGHT.size(),
                "pending",
                (long) PENDING.size());
    }

    static void resetVerificationState() {
        if (!IN_FLIGHT.isEmpty() || !PENDING.isEmpty())
            throw new IllegalStateException("Cannot reset active partial BIOMES preparation");
        BUILDS_BY_KEY.clear();
        BUILDS.reset();
        PUBLISHES.reset();
    }

    /** Bounded probe observation, not used to schedule or generate anything. */
    public static void observeForVerification(
            ServerLevel level, net.minecraft.world.level.ChunkPos pos) {
        BUILDS_BY_KEY.computeIfAbsent(
                MosaicPhysicalMaterializer.key(level, pos), ignored -> new LongAdder());
    }

    /**
     * Only explicitly observed targets retain counters; ordinary exploration never grows this map.
     */
    public static long builds(ServerLevel level, net.minecraft.world.level.ChunkPos pos) {
        var value = BUILDS_BY_KEY.get(MosaicPhysicalMaterializer.key(level, pos));
        return value == null ? 0L : value.sum();
    }
}
