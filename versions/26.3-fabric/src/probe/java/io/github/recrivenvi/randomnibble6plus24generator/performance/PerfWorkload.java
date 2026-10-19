package io.github.recrivenvi.randomnibble6plus24generator.performance;

import com.mojang.authlib.GameProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.seed.MosaicSeedResolver;
import io.github.recrivenvi.randomnibble6plus24generator.worldgen.session.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.Util;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * A disconnected player object is only a source token for Vanilla's unmodified DistanceManager. It
 * is never added to the entity manager, logged in, rendered, or controlled by a client.
 */
public final class PerfWorkload {
    private static boolean started, removed, finished;
    private static long start, lastMove, completedAt;
    private static long overCpuSince;
    private static final double MAX_CPU =
            Double.parseDouble(System.getProperty("mosaic.profile.maxCpu", "1.0"));
    private static ServerPlayer ticketSource;
    private static ChunkPos center;
    private static CompletableFuture<?> future;
    private static int moves;

    public static boolean cli() {
        return !System.getProperty("mosaic.profile.workload", "").isBlank();
    }

    public static void tick(MinecraftServer server) {
        if (!cli() || finished) return;
        ServerLevel level =
                Objects.requireNonNull(
                        server.getLevel(
                                switch (System.getProperty(
                                        "mosaic.profile.dimension", "minecraft:overworld")) {
                                    case "minecraft:overworld" -> Level.OVERWORLD;
                                    case "minecraft:the_nether" -> Level.NETHER;
                                    case "minecraft:the_end" -> Level.END;
                                    default ->
                                            throw new IllegalArgumentException(
                                                    "Unsupported performance dimension");
                                }),
                        "Performance dimension is unavailable");
        String mode = System.getProperty("mosaic.profile.workload");
        if (!started) {
            started = true;
            start = lastMove = System.nanoTime();
            center =
                    new ChunkPos(
                            Integer.getInteger("mosaic.profile.x", 125),
                            Integer.getInteger("mosaic.profile.z", -37));
            PerfTrace.marker("WORKLOAD_START_" + mode);
            if (mode.equals("isolated")) {
                future =
                        CompletableFuture.runAsync(
                                () -> {
                                    long master = server.getWorldGenSettings().options().seed();
                                    long local =
                                            new MosaicSeedResolver(MosaicWorldProfile.current())
                                                    .resolveLocalWorldSeed(
                                                            master, level.dimension(), center);
                                    for (int i = 0; i < 3; i++) {
                                        long began = System.nanoTime();
                                        try (IsolatedGenerationContext context =
                                                IsolatedGenerationContext.create(
                                                        IsolatedGenerationMode.ISOLATED_MOSAIC,
                                                        level,
                                                        local,
                                                        center)) {
                                            context.generateFeaturesStable();
                                        }
                                        System.out.println(
                                                "ISOLATED BASELINE run="
                                                        + i
                                                        + " seed="
                                                        + local
                                                        + " milliseconds="
                                                        + (System.nanoTime() - began) / 1e6);
                                    }
                                },
                                Util.backgroundExecutor());
            } else if (mode.equals("single") || mode.equals("patch")) {
                int radius = mode.equals("single") ? 0 : 1;
                List<CompletableFuture<?>> all = new ArrayList<>();
                for (int z = center.z() - radius; z <= center.z() + radius; z++)
                    for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                        ChunkPos p = new ChunkPos(x, z);
                        PerfTrace.event(level, p, "WORKLOAD_REQUEST", mode);
                        all.add(
                                level.getChunkSource()
                                        .getChunkFuture(x, z, ChunkStatus.FULL, true));
                    }
                future = CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
            } else if (mode.equals("frontier")) {
                ticketSource =
                        new ServerPlayer(
                                server,
                                level,
                                new GameProfile(
                                        UUID.nameUUIDFromBytes(
                                                "MosaicTicketProfile"
                                                        .getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8)),
                                        "ProfileTicket"),
                                ClientInformation.createDefault());
                level.getChunkSource()
                        .chunkMap
                        .getDistanceManager()
                        .addPlayer(SectionPos.of(center, 0), ticketSource);
                PerfTrace.marker("TICKET_VIEWER_ENTER_" + center);
            } else throw new IllegalArgumentException("Unknown profile workload " + mode);
        }
        long now = System.nanoTime();
        if (future != null && future.isDone() && completedAt == 0) {
            future.join();
            completedAt = now;
            PerfTrace.marker("WORKLOAD_REQUESTS_COMPLETED");
        }
        if (mode.equals("frontier")) {
            int maxMoves = Integer.getInteger("mosaic.profile.moves", 2);
            long moveAfter = Long.getLong("mosaic.profile.moveSeconds", 15L) * 1_000_000_000L;
            // The structure isolation study uses a fixed wall-time input trajectory.
            // Catch up delayed input samples; do not modify Vanilla's ticket scheduler.
            int due =
                    (Boolean.getBoolean("mosaic.profile.structureExperiment")
                                    || Boolean.getBoolean("mosaic.profile.fixedTrajectory"))
                            ? Math.min(maxMoves, (int) ((now - start) / moveAfter))
                            : (moves < maxMoves && now - lastMove >= moveAfter ? moves + 1 : moves);
            while (moves < due) {
                var manager = level.getChunkSource().chunkMap.getDistanceManager();
                manager.removePlayer(SectionPos.of(center, 0), ticketSource);
                center =
                        new ChunkPos(
                                center.x() + Integer.getInteger("mosaic.profile.moveChunks", 2),
                                center.z());
                manager.addPlayer(SectionPos.of(center, 0), ticketSource);
                moves++;
                lastMove = now;
                PerfTrace.marker("TICKET_VIEWER_MOVE_" + center);
            }
        }
        long limit = Long.getLong("mosaic.profile.seconds", 90L) * 1_000_000_000L;
        if (PerfTrace.latestProcessCpuLoad() > MAX_CPU) {
            if (overCpuSince == 0) overCpuSince = now;
        } else overCpuSince = 0;
        boolean cpuBudget = overCpuSince != 0 && now - overCpuSince >= 8_000_000_000L;
        if ((completedAt != 0 && now - completedAt > 2_000_000_000L)
                || now - start >= limit
                || cpuBudget) {
            if (cpuBudget) PerfTrace.marker("CPU_BUDGET_STOP");
            if (ticketSource != null && !removed) {
                level.getChunkSource()
                        .chunkMap
                        .getDistanceManager()
                        .removePlayer(SectionPos.of(center, 0), ticketSource);
                removed = true;
            }
            finished = true;
            PerfTrace.marker("WORKLOAD_END");
            PerfTrace.dump(false);
            PerfTrace.checkpoint();
            server.halt(false);
        }
    }
}
