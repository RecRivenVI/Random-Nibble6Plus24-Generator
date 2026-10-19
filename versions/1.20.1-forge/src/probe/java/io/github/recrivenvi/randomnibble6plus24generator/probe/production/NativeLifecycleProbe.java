package io.github.recrivenvi.randomnibble6plus24generator.probe.production;

import com.google.gson.*;
import com.mojang.datafixers.util.Either;
import io.github.recrivenvi.randomnibble6plus24generator.ModConstants;
import io.github.recrivenvi.randomnibble6plus24generator.probe.production.mixin.PhysicalStateAccess;
import io.github.recrivenvi.randomnibble6plus24generator.probe.structure.NativeStructureVerifier;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.features.FeatureExecutionScope;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity.MosaicWorldIdentity;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.*;

/** Native stop while real preparation is in flight; this never counts as FULL acceptance. */
public final class NativeLifecycleProbe {
    public static final String PREFIX = ModConstants.MOD_ID + ".nativeLifecycle.";
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("mosaic_lifecycle_probe", Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<StructureGenerationContext> CONTEXTS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger opened = new AtomicInteger(), closed = new AtomicInteger();
    private static final AtomicInteger preparations = new AtomicInteger();
    private static final AtomicReference<CompletableFuture<?>> failedStage =
            new AtomicReference<>();
    private static final JsonObject proof = new JsonObject();
    private static ServerLevel level;
    private static ChunkPos position;
    private static CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> request;
    private static Path folder;
    private static String kind, pipeline;
    private static long started, stopRequested;
    private static boolean stopping;

    private NativeLifecycleProbe() {}

    public static boolean enabled() {
        return "integration-legacy_physical".equals(System.getProperty("validation.name"))
                && System.getProperty(PREFIX + "kind") != null;
    }

    public static void opened(StructureGenerationContext context) {
        if (enabled() && CONTEXTS.add(context)) opened.incrementAndGet();
    }

    public static void closed(StructureGenerationContext context) {
        if (enabled() && CONTEXTS.remove(context)) closed.incrementAndGet();
    }

    public static void preparation() {
        if (enabled()) preparations.incrementAndGet();
    }

    public static void failureStage(CompletableFuture<?> future, Throwable failure) {
        if (enabled()
                && failure.getMessage() != null
                && failure.getMessage().startsWith("Injected "))
            failedStage.compareAndSet(null, future);
    }

    public static void started(MinecraftServer server) {
        kind = Objects.requireNonNull(System.getProperty(PREFIX + "kind"));
        pipeline = Objects.requireNonNull(System.getProperty(PREFIX + "pipeline"));
        if (!Set.of("shutdown", "genuine", "cancellation").contains(kind)
                || !Set.of("b", "vanilla").contains(pipeline))
            throw new IllegalArgumentException("Unknown lifecycle probe condition");
        String dimension = System.getProperty(PREFIX + "dimension", "minecraft:overworld");
        level =
                Objects.requireNonNull(
                        server.getLevel(
                                switch (dimension) {
                                    case "minecraft:overworld" -> Level.OVERWORLD;
                                    case "minecraft:the_nether" -> Level.NETHER;
                                    case "minecraft:the_end" -> Level.END;
                                    default -> throw new IllegalArgumentException(dimension);
                                }));
        position = new ChunkPos(125, -37);
        folder =
                Path.of(System.getProperty("validation.result"))
                        .resolve("lifecycle_" + kind)
                        .resolve(System.getProperty("validation.target"))
                        .resolve(pipeline)
                        .resolve(dimension.replace(':', '-'));
        proof.addProperty("schema", 1);
        proof.addProperty("status", "REQUESTED");
        proof.addProperty("scope", "NATIVE_CLOSE_AND_FAILURE_NOT_FULL");
        proof.addProperty("kind", kind);
        proof.addProperty("pipeline", pipeline);
        proof.addProperty("dimension", dimension);
        proof.addProperty("target", System.getProperty("validation.target"));
        proof.addProperty("minecraft", net.minecraft.SharedConstants.getCurrentVersion().getName());
        proof.addProperty(
                "inputHash", Objects.requireNonNull(System.getProperty(PREFIX + "inputHash")));
        proof.addProperty("pid", ProcessHandle.current().pid());
        proof.addProperty("processors", Runtime.getRuntime().availableProcessors());
        proof.addProperty("coldLoadedChunks", level.getChunkSource().getLoadedChunksCount());
        proof.addProperty(
                "save",
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize()
                        .toString());
        require(
                level.getChunkSource().getLoadedChunksCount() == 0,
                "Lifecycle fixture was not cold");
        require(
                MosaicWorldIdentity.runtimeContext(level).isPresent() == pipeline.equals("b"),
                "Wrong native world identity");
        started = System.nanoTime();
        proof.addProperty("startedEpochMillis", System.currentTimeMillis());
        level.getChunkSource().addRegionTicket(TICKET, position, 0, position);
        request =
                CompletableFuture.supplyAsync(
                                () ->
                                        level.getChunkSource()
                                                .getChunkFuture(
                                                        position.x,
                                                        position.z,
                                                        ChunkStatus.FULL,
                                                        true))
                        .thenCompose(java.util.function.Function.identity());
        write();
    }

    public static void tick(MinecraftServer server) {
        if (request == null || stopping) return;
        long elapsed = System.nanoTime() - started;
        if (kind.equals("shutdown")) {
            var owner = MosaicPhysicalLevelOwner.forLevel(level);
            var counts = owner.mosaic$physicalState().metrics();
            if (pipeline.equals("b")
                    && (preparations.get() == 0
                            || counts.inFlight() == 0
                            || !owner.randomnibble6plus24generator$generationLifecycle()
                                    .active())) {
                if (elapsed > 20_000_000_000L)
                    fail(server, "No actual in-flight preparation before stop");
                return;
            }
            if (pipeline.equals("b") && elapsed < 1_000_000_000L) return;
            require(!request.isDone(), "Stop did not interrupt an unfinished native FULL request");
            proof.add("beforeStop", new Gson().toJsonTree(counts));
            proof.addProperty("preparationsBeforeStop", preparations.get());
            proof.addProperty("requestIncompleteAtStop", true);
            proof.addProperty("status", "EXPECTED_STOP_REQUESTED");
            stopping = true;
            stopRequested = System.nanoTime();
            proof.addProperty("stopRequestedEpochMillis", System.currentTimeMillis());
            write();
            server.halt(false);
        } else if (failedStage.get() != null && failedStage.get().isCompletedExceptionally()) {
            proof.addProperty("status", "OBSERVED_NATIVE_FAILURE");
            proof.addProperty("nativeFutureFailureObserved", true);
            proof.addProperty("failureObservation", "NATIVE_GENERATION_STAGE_FUTURE");
            proof.addProperty("fullFutureDoneAtFailure", request.isDone());
            write();
            // Native Util.sequence waits for all dependencies. Consume the actual failing stage,
            // not a still-pending FULL aggregate; do not synthesize or suppress an exception.
            failedStage.get().join();
            throw new IllegalStateException("Exceptional native request unexpectedly returned");
        } else if (elapsed > 20_000_000_000L) {
            fail(server, "Injected real failure was not reported within its observation budget");
        }
    }

    public static void stopped(MinecraftServer server) {
        if (!enabled() || folder == null) return;
        try {
            var state = MosaicPhysicalLevelOwner.forLevel(level).mosaic$physicalState();
            var lifecycle =
                    MosaicPhysicalLevelOwner.forLevel(level)
                            .randomnibble6plus24generator$generationLifecycle();
            var failure = ((PhysicalStateAccess) (Object) state).mosaic$retainedFailure().get();
            proof.add("afterStop", new Gson().toJsonTree(state.metrics()));
            proof.add("lifecycle", new Gson().toJsonTree(lifecycle.snapshot()));
            proof.addProperty("contextsOpened", opened.get());
            proof.addProperty("contextsClosed", closed.get());
            proof.addProperty("contextsRemaining", CONTEXTS.size());
            proof.addProperty("bindings", FeatureExecutionScope.bindingCount());
            proof.addProperty("retainedFailure", failure == null ? "" : failure.toString());
            proof.addProperty(
                    "retainedFailureIsExpected", failure != null && lifecycle.isExpected(failure));
            require(
                    state.metrics().inFlight() == 0 && lifecycle.quiescent(),
                    "Native work leaked after stop");
            require(
                    CONTEXTS.isEmpty() && opened.get() == closed.get(),
                    "Native context leaked after stop");
            require(
                    FeatureExecutionScope.bindingCount() == 0
                            && !SeedLocalRegionScope.isBound()
                            && SpawnConstructorScope.current() == null,
                    "Generation scope leaked");
            if (kind.equals("shutdown")) {
                require(
                        proof.get("status").getAsString().equals("EXPECTED_STOP_REQUESTED"),
                        "No expected native stop");
                require(failure == null, "Expected shutdown became a generation failure");
                double seconds = (System.nanoTime() - stopRequested) / 1e9;
                proof.addProperty("stopSeconds", seconds);
                require(seconds < 20, "Native shutdown exceeded 20 seconds");
                if (pipeline.equals("b")) {
                    require(
                            lifecycle.closing() && lifecycle.snapshot().expectedTerminations() > 0,
                            "No actual owned cancellation observed");
                    require(opened.get() > 0, "No actual native preparation context");
                    require(
                            request.isDone()
                                    && !request.isCompletedExceptionally()
                                    && request.join().right().isPresent(),
                            "Native FULL did not settle as unloaded");
                } else
                    require(
                            preparations.get() == 0 && state.metrics().generations() == 0,
                            "Vanilla entered Mosaic production");
                proof.addProperty("status", "PASS");
            } else {
                require(
                        proof.get("status").getAsString().equals("OBSERVED_NATIVE_FAILURE")
                                && !proof.has("failure"),
                        "Native failure was not consumed before stop");
                require(pipeline.equals("b") && failure != null, "Genuine failure was swallowed");
                require(
                        opened.get() > 0,
                        "Failure did not unwind an actual allocated native context");
                Class<?> expected =
                        kind.equals("genuine")
                                ? IllegalStateException.class
                                : CancellationException.class;
                require(
                        failure.getClass() == expected
                                && failure.getMessage().startsWith("Injected "),
                        "Wrong generation failure retained: " + failure);
                proof.addProperty("status", "EXPECTED_FAILURE_OBSERVED");
            }
        } catch (Throwable error) {
            proof.addProperty("status", "FAIL");
            proof.addProperty("failure", error.toString());
        } finally {
            write();
            CONTEXTS.clear();
            failedStage.set(null);
            level = null;
            request = null;
        }
    }

    private static void fail(MinecraftServer server, String message) {
        proof.addProperty("status", "FAIL");
        proof.addProperty("failure", message);
        stopping = true;
        write();
        server.halt(false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void write() {
        NativeStructureVerifier.write(folder.resolve("summary.json"), proof);
    }
}
