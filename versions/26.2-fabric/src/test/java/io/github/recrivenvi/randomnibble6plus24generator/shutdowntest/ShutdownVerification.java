package io.github.recrivenvi.randomnibble6plus24generator.shutdowntest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.google.gson.*;
import net.minecraft.CrashReport;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.materialization.*;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;

/** Deterministic close race and unmodified Vanilla delayed-crash observation. */
public final class ShutdownVerification {
    private static final String MODE = System.getProperty("mosaic.shutdown.test.mode", "cancel");
    private static final AtomicInteger BLOCKED = new AtomicInteger();
    private static final AtomicInteger REPORTS = new AtomicInteger();
    private static final AtomicReference<ChunkPos> FIRST_BLOCKED = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicBoolean FAULT_INJECTED = new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile boolean armed;
    private static boolean started;
    private static boolean normalStopRequested;
    private static final JsonObject RESULT = new JsonObject();
    private static final java.util.List<net.minecraft.server.level.GenerationChunkHolder> WATCHED = new java.util.ArrayList<>();

    private ShutdownVerification() {}
    public static boolean isFaultMode() { return MODE.contains("error") || MODE.contains("unexpected"); }

    public static void beforePrepare(ServerLevel level, ChunkPos target) {
        if (!armed) return;
        if (MODE.equals("error") && FAULT_INJECTED.compareAndSet(false,true)) throw new IllegalStateException("EXPLICIT_GENERATION_ERROR");
        if (MODE.equals("unexpected") && FAULT_INJECTED.compareAndSet(false,true)) throw new CancellationException("UNEXPECTED_NON_LIFECYCLE_CANCELLATION");
        if (MODE.equals("cancel") || MODE.startsWith("close-")) {
            // Do not park every shared-pool worker before native parseChunk tasks can
            // create the requested frontier. Arm the close gate only once it exists.
            if (MosaicPhysicalMaterializer.metrics().inFlightCount() < 8) return;
            FIRST_BLOCKED.compareAndSet(null, target);
            BLOCKED.incrementAndGet();
            var lifecycle = ((MosaicGenerationLifecycleOwner) level).randomnibble6plus24generator$generationLifecycle();
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (!lifecycle.closing()) {
                if (System.nanoTime() > deadline) throw new AssertionError("Test never began real server shutdown");
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
            if (MODE.equals("close-error") && FAULT_INJECTED.compareAndSet(false,true)) throw new IllegalStateException("EXPLICIT_ERROR_DURING_SHUTDOWN");
            if (MODE.equals("close-unexpected") && FAULT_INJECTED.compareAndSet(false,true)) throw new CancellationException("UNEXPECTED_CANCELLATION_DURING_SHUTDOWN");
        }
    }

    public static synchronized void reported(CrashReport report) {
        REPORTS.incrementAndGet();
        RESULT.addProperty("reportedFailure", report.getException().toString());
        Throwable root = report.getException();
        while ((root instanceof java.util.concurrent.CompletionException || root instanceof java.util.concurrent.ExecutionException)
                && root.getCause() != null) root = root.getCause();
        RESULT.addProperty("reportedRoot", root.toString());
        RESULT.addProperty("crashReports", REPORTS.get());
        write("observed-crash.json", RESULT);
        // Do not cancel or replace relayDelayCrash: the real Minecraft error path must run.
    }

    public static void run(MinecraftServer server) {
        if (started) return;
        started = true;
        ServerLevel level = server.overworld();
        RESULT.addProperty("mode", MODE);
        if (MODE.equals("reload")) {
            long artifactsBefore = MosaicPhysicalMaterializer.metrics().artifactCaptureCount();
            int spawnsBefore = PhysicalMosaicTrace.snapshot().spawnSeeds().size();
            LevelChunk saved = full(server, level, new ChunkPos(125, -37));
            BlockPos marker = new BlockPos(2000, level.getMinY() + 3, -592);
            if (!saved.getBlockState(marker).is(Blocks.DIAMOND_BLOCK)) throw new AssertionError("Saved edit overwritten");
            if (MosaicPhysicalMaterializer.metrics().artifactCaptureCount() != artifactsBefore
                    || PhysicalMosaicTrace.snapshot().spawnSeeds().size() != spawnsBefore) throw new AssertionError("FULL reload regenerated");
            JsonObject previous;
            try { previous = JsonParser.parseString(Files.readString(Path.of(System.getProperty("mosaic.shutdown.test.reference")))).getAsJsonObject(); }
            catch (Exception error) { throw new IllegalStateException(error); }
            ChunkPos retry = new ChunkPos(previous.get("blockedX").getAsInt(), previous.get("blockedZ").getAsInt());
            full(server, level, retry);
            if (MosaicPhysicalMaterializer.metrics().artifactCaptureCount() <= artifactsBefore) throw new AssertionError("Cancelled chunk did not resume fresh generation");
            RESULT.addProperty("savedFullRegenerated", false);
            RESULT.addProperty("savedEditPreserved", true);
            RESULT.addProperty("cancelledTargetRegenerated", true);
            normalStopRequested = true;
            server.halt(false);
            return;
        }
        if (MODE.equals("cancel")) {
            LevelChunk saved = full(server, level, new ChunkPos(125, -37));
            saved.setBlockState(new BlockPos(2000, level.getMinY() + 3, -592), Blocks.DIAMOND_BLOCK.defaultBlockState(), 0);
            saved.markUnsaved();
        }
        armed = true;
        if (MODE.equals("vanilla")) {
            full(server, level, new ChunkPos(125, -37));
            normalStopRequested = true;
            server.halt(false);
            return;
        }
        // 26.2's public method blocks when invoked on the server thread. Submit from
        // a test requester instead; Vanilla still owns all tickets, dispatch and generation.
        Thread requester = Thread.ofPlatform().name("shutdown-test-requester").start(() -> {
            for (int z = 64; z < 69; z++) for (int x = 64; x < 69; x++)
                level.getChunkSource().getChunkFuture(x, z, ChunkStatus.FULL, true);
        });
        try { requester.join(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
        long deadline = System.nanoTime() + 25_000_000_000L;
        if (MODE.equals("error") || MODE.equals("unexpected")) {
            server.managedBlock(() -> REPORTS.get() > 0 || System.nanoTime() > deadline);
            if (REPORTS.get() == 0) throw new AssertionError("Injected real failure was swallowed");
        } else {
            server.managedBlock(() -> (BLOCKED.get() > 0 && MosaicPhysicalMaterializer.metrics().inFlightCount() >= 8)
                    || System.nanoTime() > deadline);
            if (BLOCKED.get() == 0 || MosaicPhysicalMaterializer.metrics().inFlightCount() < 8) {
                throw new AssertionError("Did not create an in-flight generation frontier");
            }
            RESULT.addProperty("inFlightAtClose", MosaicPhysicalMaterializer.metrics().inFlightCount());
            RESULT.addProperty("blockedX", FIRST_BLOCKED.get().x());
            RESULT.addProperty("blockedZ", FIRST_BLOCKED.get().z());
            for (int z=63;z<=69;z++) for(int x=63;x<=69;x++) {
                var holder=level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(ChunkPos.pack(x,z));
                if(holder!=null)WATCHED.add(holder);
            }
        }
        normalStopRequested = true;
        server.halt(false);
    }

    public static synchronized void stopped(MinecraftServer server) {
        if (!isFaultMode() && !normalStopRequested) throw new AssertionError("Shutdown test scenario did not complete");
        var metrics = MosaicPhysicalMaterializer.metrics();
        long expected = 0;
        for (ServerLevel level : server.getAllLevels()) {
            var state = ((MosaicGenerationLifecycleOwner) level).randomnibble6plus24generator$generationLifecycle().snapshot();
            if (state.workers() != 0) throw new AssertionError("Worker leak after close");
            expected += state.expectedTerminations();
        }
        if (!isFaultMode() && REPORTS.get() != 0) throw new AssertionError("Normal shutdown reached delayed-crash path");
        if (MODE.equals("cancel") && expected == 0) throw new AssertionError("Expected-cancellation branch was not exercised");
        if (isFaultMode() && REPORTS.get() == 0) throw new AssertionError("Real failure not reported");
        if (metrics.inFlightCount() != 0 || metrics.requestedTargetCount() != 0
                || metrics.materializationObligationCount() != 0 || metrics.physicalStatusAllowanceCount() != 0
                || GenerationContextRegistry.bindingCount() != 0 || MosaicSpawnContextRegistry.bindingCount() != 0) {
            throw new AssertionError("Mosaic state leaked after shutdown: " + metrics);
        }
        if(!isFaultMode()){
            try {
                var refs=net.minecraft.server.level.GenerationChunkHolder.class.getDeclaredField("generationRefCount");refs.setAccessible(true);
                var futures=net.minecraft.server.level.GenerationChunkHolder.class.getDeclaredField("futures");futures.setAccessible(true);
                for(var holder:WATCHED){
                    if(((AtomicInteger)refs.get(holder)).get()!=0)throw new AssertionError("Holder generation claim leaked");
                    var array=(java.util.concurrent.atomic.AtomicReferenceArray<?>)futures.get(holder);
                    for(int i=0;i<array.length();i++)if(array.get(i) instanceof java.util.concurrent.CompletableFuture<?> future && !future.isDone())
                        throw new AssertionError("Pending holder future survived world close");
                }
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            RESULT.addProperty("holdersChecked",WATCHED.size());RESULT.addProperty("holderClaimsAfterClose",0);
        }
        RESULT.addProperty("expectedTerminations", expected);
        RESULT.addProperty("crashReports", REPORTS.get());
        RESULT.addProperty("inFlightAfterClose", metrics.inFlightCount());
        RESULT.addProperty("bindingsAfterClose", GenerationContextRegistry.bindingCount());
        RESULT.addProperty("status", "PASS");
        write("result.json", RESULT);
        System.out.println("GENERATION SHUTDOWN TEST " + RESULT);
    }

    private static LevelChunk full(MinecraftServer server, ServerLevel level, ChunkPos pos) {
        var future = level.getChunkSource().getChunkFuture(pos.x(), pos.z(), ChunkStatus.FULL, true);
        server.managedBlock(future::isDone);
        return (LevelChunk) future.join().orElseThrow(() -> new AssertionError("FULL request failed"));
    }

    private static void write(String file, JsonObject value) {
        try {
            Path folder = Path.of(System.getProperty("mosaic.shutdown.test.output"));
            Files.createDirectories(folder); Files.writeString(folder.resolve(file), value.toString());
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
